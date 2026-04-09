import sqlite3
import hashlib
from pathlib import Path
from datetime import datetime, timedelta
from typing import Optional


class UrlStore:
    """URL指纹存储，基于SQLite，用于去重和增量爬取"""

    def __init__(self, db_path: str = "./data/url_fingerprints.db"):
        self.db_path = db_path
        Path(db_path).parent.mkdir(parents=True, exist_ok=True)
        self.conn = sqlite3.connect(db_path)
        self.conn.row_factory = sqlite3.Row
        self._init_db()

    def _init_db(self):
        self.conn.executescript("""
            CREATE TABLE IF NOT EXISTS url_fingerprints (
                fingerprint TEXT PRIMARY KEY,
                url TEXT NOT NULL,
                content_hash TEXT NOT NULL DEFAULT '',
                site_name TEXT NOT NULL,
                crawled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                pushed INTEGER DEFAULT 0
            );
            CREATE INDEX IF NOT EXISTS idx_url_fp_site ON url_fingerprints(site_name);
            CREATE INDEX IF NOT EXISTS idx_url_fp_crawled ON url_fingerprints(crawled_at);
        """)

    @staticmethod
    def fingerprint(url: str) -> str:
        normalized = url.strip().rstrip("/").lower()
        return hashlib.sha256(normalized.encode("utf-8")).hexdigest()[:16]

    @staticmethod
    def content_hash(text: str) -> str:
        return hashlib.md5(text.encode("utf-8")).hexdigest()

    def is_seen(self, url: str) -> bool:
        fp = self.fingerprint(url)
        row = self.conn.execute(
            "SELECT 1 FROM url_fingerprints WHERE fingerprint = ?", (fp,)
        ).fetchone()
        return row is not None

    def get_content_hash(self, url: str) -> Optional[str]:
        fp = self.fingerprint(url)
        row = self.conn.execute(
            "SELECT content_hash FROM url_fingerprints WHERE fingerprint = ?", (fp,)
        ).fetchone()
        return row["content_hash"] if row else None

    def mark_seen(self, url: str, site_name: str, content_hash: str = "", pushed: bool = False):
        fp = self.fingerprint(url)
        self.conn.execute(
            """INSERT INTO url_fingerprints (fingerprint, url, content_hash, site_name, crawled_at, pushed)
               VALUES (?, ?, ?, ?, ?, ?)
               ON CONFLICT(fingerprint) DO UPDATE SET
                   content_hash = excluded.content_hash,
                   crawled_at = excluded.crawled_at,
                   pushed = excluded.pushed""",
            (fp, url, content_hash, site_name, datetime.utcnow().isoformat(), 1 if pushed else 0),
        )
        self.conn.commit()

    def cleanup_old(self, days: int = 7):
        cutoff = (datetime.utcnow() - timedelta(days=days)).isoformat()
        self.conn.execute(
            "DELETE FROM url_fingerprints WHERE crawled_at < ?", (cutoff,)
        )
        self.conn.commit()

    def close(self):
        self.conn.close()
