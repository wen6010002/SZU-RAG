"""
pipelines/dedup_pipeline.py
去重Pipeline — 升级版

升级内容：
  - URL指纹 + 日期范围组合过滤
  - 内容MD5变更检测：相同URL但内容变化时重新推送
  - 日期维度记录：在指纹表中存储 publish_date
  - 全量模式后清理过期指纹（超过 full_crawl_day_interval 天）

Pipeline优先级：100
"""

import hashlib
import sqlite3
from datetime import datetime, timedelta
from pathlib import Path

import scrapy


class DedupPipeline:
    """
    去重Pipeline。

    处理逻辑：
      1. 计算URL指纹（归一化URL → SHA256前16位）
      2. 计算内容MD5
      3. 查询SQLite：
         - 指纹不存在 → 新文章，放行
         - 指纹存在 + MD5相同 → 重复文章，跳过
         - 指纹存在 + MD5不同 → 内容变更，放行（标记为更新）
      4. 记录 publish_date 到指纹表
      5. 所有Item都写入/更新记录（包括被跳过的），保持 crawled_at 最新
    """

    def __init__(self, db_path: str = "data/dedup.db"):
        self.db_path = Path(db_path)
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self.conn: sqlite3.Connection | None = None
        self._pending_writes = 0  # O7: 批量提交计数器
        self._batch_size = 50     # 每50次操作批量commit一次

    def open_spider(self, spider):
        """Spider启动时初始化数据库连接和表结构。"""
        self.conn = sqlite3.connect(str(self.db_path))
        self.conn.execute("PRAGMA journal_mode=WAL")
        self._ensure_table()
        if spider:
            spider.logger.info(f"DedupPipeline 已连接: {self.db_path}")

    def close_spider(self, spider):
        """Spider关闭时提交剩余操作并清理。"""
        if self.conn:
            self.conn.commit()  # 提交剩余的批量操作
            self.conn.close()
            if spider:
                spider.logger.info("DedupPipeline 已关闭")

    def process_item(self, item, spider):
        """
        处理Item：去重判断。

        所有Item都会放行（DropItem不会触发后续Pipeline），
        但通过 item["_dedup_status"] 标记状态：
          - "new":       新文章
          - "duplicate": 重复（MD5相同）
          - "updated":   内容变更（MD5不同）
        """
        url = item.get("url", "")
        # O11修复：优先使用content_md计算哈希，回退到content
        content = item.get("content_md", "") or item.get("content", "")
        site_name = item.get("site_name", "")
        publish_date = item.get("publish_date", "")

        # 计算指纹和哈希
        fingerprint = self._url_fingerprint(url)
        content_hash = self._content_hash(content)

        # 查询已有记录
        row = self._query_fingerprint(fingerprint)

        if row is None:
            # 新文章
            self._insert_fingerprint(fingerprint, url, content_hash, site_name, publish_date)
            item["_dedup_status"] = "new"
            item["_fingerprint"] = fingerprint
            spider.logger.debug(f"[去重] 新文章: {item.get('title', '')[:30]}")
            return item

        existing_hash = row["content_hash"]

        # 更新 crawled_at
        self._update_crawled_at(fingerprint, publish_date)

        if existing_hash == content_hash:
            # 内容未变 → 重复
            item["_dedup_status"] = "duplicate"
            item["_fingerprint"] = fingerprint
            spider.logger.debug(f"[去重] 重复跳过: {item.get('title', '')[:30]}")
            # 注意：仍然return item，由后续Pipeline决定是否跳过推送
            return item
        else:
            # 内容变更 → 更新
            self._update_content_hash(fingerprint, content_hash)
            item["_dedup_status"] = "updated"
            item["_fingerprint"] = fingerprint
            spider.logger.info(f"[去重] 内容变更: {item.get('title', '')[:30]}")
            return item

    # ------------------------------------------------------------------ #
    #  数据库操作
    # ------------------------------------------------------------------ #

    def _batch_commit(self):
        """O7: 批量提交，减少频繁IO。"""
        self._pending_writes += 1
        if self._pending_writes >= self._batch_size:
            self.conn.commit()
            self._pending_writes = 0

    def _ensure_table(self):
        """创建/升级 url_fingerprints 表。"""
        self.conn.execute("""
            CREATE TABLE IF NOT EXISTS url_fingerprints (
                fingerprint   TEXT PRIMARY KEY,
                url           TEXT NOT NULL,
                content_hash  TEXT NOT NULL,
                site_name     TEXT NOT NULL DEFAULT '',
                publish_date  TEXT NOT NULL DEFAULT '',
                crawled_at    TEXT NOT NULL,
                pushed        INTEGER NOT NULL DEFAULT 0
            )
        """)

        # 升级：添加 publish_date 列（兼容旧表）
        columns = [row[1] for row in self.conn.execute("PRAGMA table_info(url_fingerprints)").fetchall()]
        if "publish_date" not in columns:
            self.conn.execute(
                "ALTER TABLE url_fingerprints ADD COLUMN publish_date TEXT NOT NULL DEFAULT ''"
            )

        self.conn.commit()

    def _query_fingerprint(self, fingerprint: str) -> dict | None:
        """查询指纹记录。"""
        cursor = self.conn.execute(
            "SELECT content_hash, publish_date FROM url_fingerprints WHERE fingerprint = ?",
            (fingerprint,),
        )
        row = cursor.fetchone()
        if row is None:
            return None
        return {"content_hash": row[0], "publish_date": row[1]}

    def _insert_fingerprint(
        self,
        fingerprint: str,
        url: str,
        content_hash: str,
        site_name: str,
        publish_date: str,
    ):
        """插入新指纹记录。"""
        now = datetime.now().isoformat()
        self.conn.execute(
            """INSERT INTO url_fingerprints
               (fingerprint, url, content_hash, site_name, publish_date, crawled_at, pushed)
               VALUES (?, ?, ?, ?, ?, ?, 0)""",
            (fingerprint, url, content_hash, site_name, publish_date, now),
        )
        self._batch_commit()

    def _update_crawled_at(self, fingerprint: str, publish_date: str):
        """更新爬取时间和发布日期。"""
        now = datetime.now().isoformat()
        self.conn.execute(
            """UPDATE url_fingerprints
               SET crawled_at = ?, publish_date = ?
               WHERE fingerprint = ?""",
            (now, publish_date, fingerprint),
        )
        self._batch_commit()

    def _update_content_hash(self, fingerprint: str, content_hash: str):
        """更新内容哈希（内容变更时）。"""
        self.conn.execute(
            """UPDATE url_fingerprints
               SET content_hash = ?, pushed = 0
               WHERE fingerprint = ?""",
            (content_hash, fingerprint),
        )
        self._batch_commit()

    # ------------------------------------------------------------------ #
    #  指纹与哈希计算
    # ------------------------------------------------------------------ #

    @staticmethod
    def _url_fingerprint(url: str) -> str:
        """URL指纹：归一化后SHA256取前16位。"""
        normalized = url.rstrip("/").lower().split("#")[0]
        return hashlib.sha256(normalized.encode()).hexdigest()[:16]

    @staticmethod
    def _content_hash(content: str) -> str:
        """内容MD5：对正文（优先Markdown）取MD5。"""
        return hashlib.md5(content.encode()).hexdigest()

    # ------------------------------------------------------------------ #
    #  过期指纹清理
    # ------------------------------------------------------------------ #

    def cleanup_old_fingerprints(self, site_name: str, max_age_days: int):
        """
        清理指定站点的过期指纹。

        由FastAPI管理接口在全量爬取后调用。
        删除 crawled_at 超过 max_age_days 天的记录。

        Args:
            site_name:     站点标识
            max_age_days:  保留天数
        """
        cutoff = (datetime.now() - timedelta(days=max_age_days)).isoformat()
        cursor = self.conn.execute(
            """DELETE FROM url_fingerprints
               WHERE site_name = ? AND crawled_at < ?""",
            (site_name, cutoff),
        )
        self.conn.commit()
        return cursor.rowcount

    def get_stats(self, site_name: str = "") -> dict:
        """
        获取去重统计信息。

        Returns:
            { "total": N, "by_site": { "jwb": N, ... }, "date_range": { "earliest": "...", "latest": "..." } }
        """
        if site_name:
            cursor = self.conn.execute(
                "SELECT COUNT(*) FROM url_fingerprints WHERE site_name = ?",
                (site_name,),
            )
            total = cursor.fetchone()[0]
            return {"site": site_name, "total": total}

        cursor = self.conn.execute("SELECT COUNT(*) FROM url_fingerprints")
        total = cursor.fetchone()[0]

        by_site = {}
        for row in self.conn.execute(
            "SELECT site_name, COUNT(*) FROM url_fingerprints GROUP BY site_name"
        ):
            by_site[row[0]] = row[1]

        date_range = {}
        row = self.conn.execute(
            """SELECT MIN(publish_date), MAX(publish_date)
               FROM url_fingerprints WHERE publish_date != ''"""
        ).fetchone()
        if row and row[0]:
            date_range = {"earliest": row[0], "latest": row[1]}

        return {"total": total, "by_site": by_site, "date_range": date_range}

    # ------------------------------------------------------------------ #
    #  静态方法（供 RagentPushPipeline 标记 pushed）
    # ------------------------------------------------------------------ #

    @staticmethod
    def _mark_pushed_static(fingerprint: str):
        """
        静态方法：推送成功后标记 pushed=1。

        供 RagentPushPipeline 在推送成功后调用。
        注意：此方法会创建临时数据库连接，仅在推送Pipeline中使用。
        """
        db_path = Path("data/dedup.db")
        if not db_path.exists():
            return
        conn = sqlite3.connect(str(db_path))
        try:
            conn.execute(
                "UPDATE url_fingerprints SET pushed = 1 WHERE fingerprint = ?",
                (fingerprint,),
            )
            conn.commit()
        finally:
            conn.close()
