import re
from bs4 import BeautifulSoup
from markdownify import markdownify as md

class VsbDetailParser:
    def parse(self, html: str, url: str = "") -> dict:
        soup = BeautifulSoup(html, "lxml")

        title_el = soup.select_one("div.art-tit h3") or soup.select_one("h1")
        title = title_el.get_text(strip=True) if title_el else ""

        source, publish_date = "", ""
        meta_area = soup.select_one("div.art-tit p")
        if meta_area:
            for span in meta_area.find_all("span"):
                text = span.get_text()
                if "信息来源" in text:
                    source = text.replace("信息来源：", "").strip()
                elif "发布日期" in text:
                    date_match = re.search(r"\d{4}-\d{2}-\d{2}", text)
                    publish_date = date_match.group() if date_match else ""

        content_el = soup.select_one("div#vsb_content") or soup.select_one("div.v_news_content")
        content_html = str(content_el) if content_el else ""
        content_text = content_el.get_text("\n", strip=True) if content_el else ""
        content_md = md(content_html, heading_style="ATX", strip=["img"]) if content_html else ""

        attachments = []
        for a in soup.select("a[href*='download.jsp']"):
            attachments.append({"url": a.get("href", ""), "name": a.get_text(strip=True)})
        for a in soup.select("a[href*='.doc'], a[href*='.docx'], a[href*='.pdf'], a[href*='.xls']"):
            href = a.get("href", "")
            name = a.get_text(strip=True)
            if name and href not in [att["url"] for att in attachments]:
                attachments.append({"url": href, "name": name})

        return {
            "url": url, "title": title, "source": source,
            "publish_date": publish_date,
            "content_html": content_html, "content_text": content_text,
            "content_md": content_md, "attachments": attachments,
        }
