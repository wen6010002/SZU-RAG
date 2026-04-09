import os
import hashlib
from pathlib import Path
from urllib.parse import urljoin
import httpx
from config.settings import get_config


class AttachmentPipeline:
    """附件下载Pipeline：下载文章中的附件文件，返回本地路径列表"""

    def __init__(self):
        self.client = None
        self.download_dir = None

    def open_spider(self, spider):
        config = get_config()
        self.download_dir = Path(config.ATTACHMENT_DIR)
        self.download_dir.mkdir(parents=True, exist_ok=True)
        self.client = httpx.Client(
            timeout=30,
            follow_redirects=True,
            headers={"User-Agent": (
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                "AppleWebKit/537.36 (KHTML, like Gecko) "
                "Chrome/120.0.0.0 Safari/537.36"
            )},
        )

    def close_spider(self, spider):
        if self.client:
            self.client.close()

    def process_item(self, item, spider):
        attachments = item.get("attachments", [])
        base_url = item.get("base_url", "")
        if not attachments:
            item["downloaded_attachments"] = []
            return item

        downloaded = []
        for att in attachments:
            att_url = att.get("url", "")
            att_name = att.get("name", "unnamed")
            if not att_url:
                continue

            # 补全URL
            if not att_url.startswith("http"):
                att_url = urljoin(base_url + "/", att_url)

            local_path = self._download(att_url, att_name, spider)
            if local_path:
                downloaded.append(
                    {"name": att_name, "path": str(local_path), "url": att_url}
                )

        item["downloaded_attachments"] = downloaded
        return item

    def _download(self, url: str, name: str, spider) -> Path | None:
        config = get_config()
        try:
            resp = self.client.get(url)
            resp.raise_for_status()

            content = resp.content
            max_size = config.ATTACHMENT_MAX_SIZE_MB * 1024 * 1024
            if len(content) > max_size:
                spider.logger.warning(f"Attachment too large ({len(content)} bytes): {url}")
                return None

            # 用URL哈希作为文件名避免冲突
            url_hash = hashlib.md5(url.encode("utf-8")).hexdigest()[:12]
            ext = Path(name).suffix or Path(url).suffix or ".bin"
            safe_name = "".join(
                c for c in Path(name).stem[:30]
                if c.isalnum() or c in " _-" or "\u4e00" <= c <= "\u9fff"
            )
            filename = f"{safe_name}_{url_hash}{ext}"
            local_path = self.download_dir / filename

            local_path.write_bytes(content)
            spider.logger.info(f"Downloaded attachment: {filename}")
            return local_path

        except httpx.HTTPError as e:
            spider.logger.warning(f"Failed to download {url}: {e}")
            return None
