import httpx
from pathlib import Path
from config.settings import get_config


class RagentPushPipeline:
    """推送Pipeline：将Markdown内容和附件推送到Java后端"""

    def __init__(self):
        self.client = None
        self.base_url = None
        self.api_key = None

    def open_spider(self, spider):
        config = get_config()
        self.base_url = config.RAGENT_API_BASE
        self.api_key = config.RAGENT_API_KEY
        self.client = httpx.Client(timeout=60)

    def close_spider(self, spider):
        if self.client:
            self.client.close()

    def process_item(self, item, spider):
        title = item.get("title", "")
        content_md = item.get("content_md", "")
        url = item.get("url", "")

        # C6修复：检查_dedup_status，跳过重复文章
        status = item.get("_dedup_status", "new")
        if status == "duplicate":
            spider.logger.debug(f"跳过推送(重复): {title[:50]}")
            return item

        if not content_md or not title:
            return item

        downloaded = item.get("downloaded_attachments", [])

        try:
            if downloaded:
                self._push_with_attachments(title, content_md, url, downloaded, spider)
            else:
                self._push_markdown(title, content_md, url)
            spider.logger.info(f"Pushed: {title[:50]}")

            # O6修复：推送成功后标记pushed=1（需DedupPipeline提供方法）
            fingerprint = item.get("_fingerprint", "")
            if fingerprint:
                try:
                    from pipelines.dedup_pipeline import DedupPipeline
                    DedupPipeline._mark_pushed_static(fingerprint)
                except Exception:
                    pass  # 标记失败不影响主流程

        except Exception as e:
            spider.logger.error(f"Push failed for {url}: {e}")
            # O5修复：推送失败时不标记指纹，下次可重试

        return item

    def _push_markdown(self, title, content_md, source_url):
        tmp_dir = Path("./data/push_queue")
        tmp_dir.mkdir(parents=True, exist_ok=True)

        file_content = (
            f'---\ntitle: "{title}"\nsource_url: "{source_url}"\n---\n\n'
            f"{content_md}\n"
        )
        safe_title = "".join(
            c
            for c in title[:50]
            if c.isalnum() or c in " _-" or "\u4e00" <= c <= "\u9fff"
        )
        file_path = tmp_dir / f"{safe_title}.md"
        file_path.write_text(file_content, encoding="utf-8")

        headers = {}
        if self.api_key:
            headers["Authorization"] = f"Bearer {self.api_key}"

        with open(file_path, "rb") as f:
            resp = self.client.post(
                f"{self.base_url}/api/v1/knowledge/documents/upload",
                files={"file": (file_path.name, f, "text/markdown")},
                data={"sourceUrl": source_url},
                headers=headers,
            )
            resp.raise_for_status()
        file_path.unlink(missing_ok=True)

    def _push_with_attachments(
        self, title, content_md, source_url, attachments, spider
    ):
        tmp_dir = Path("./data/push_queue")
        tmp_dir.mkdir(parents=True, exist_ok=True)

        # 在Markdown末尾追加附件列表
        att_section = "\n\n## 附件\n"
        for att in attachments:
            att_section += f"- [{att['name']}]({att['url']})\n"
        full_md = content_md + att_section

        safe_title = "".join(
            c
            for c in title[:50]
            if c.isalnum() or c in " _-" or "\u4e00" <= c <= "\u9fff"
        )
        md_path = tmp_dir / f"{safe_title}.md"
        md_path.write_text(
            f'---\ntitle: "{title}"\nsource_url: "{source_url}"\n---\n\n{full_md}\n',
            encoding="utf-8",
        )

        # 构建multipart上传：主文件 + 附件
        files = [
            ("files", (md_path.name, open(md_path, "rb"), "text/markdown"))
        ]
        for att in attachments:
            att_path = Path(att["path"])
            if att_path.exists():
                ext = att_path.suffix.lower()
                mime = {
                    ".pdf": "application/pdf",
                    ".doc": "application/msword",
                    ".docx": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    ".xls": "application/vnd.ms-excel",
                    ".xlsx": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                }.get(ext, "application/octet-stream")
                files.append(
                    ("files", (att_path.name, open(att_path, "rb"), mime))
                )

        headers = {}
        if self.api_key:
            headers["Authorization"] = f"Bearer {self.api_key}"

        try:
            resp = self.client.post(
                f"{self.base_url}/api/v1/knowledge/documents/upload",
                files=files,
                data={"sourceUrl": source_url},
                headers=headers,
            )
            resp.raise_for_status()
        finally:
            # 关闭所有文件句柄
            for _, (_, fobj, _) in files:
                fobj.close()
            md_path.unlink(missing_ok=True)
