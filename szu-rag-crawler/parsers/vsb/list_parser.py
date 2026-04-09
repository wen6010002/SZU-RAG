import re
from urllib.parse import urljoin
from bs4 import BeautifulSoup

class VsbListParser:
    def extract_total_count(self, html: str) -> int | None:
        match = re.search(r"共\s*(\d+)\s*条", html)
        return int(match.group(1)) if match else None

    def extract_articles(self, html: str, base_url: str = "") -> list[dict]:
        soup = BeautifulSoup(html, "lxml")
        articles = []
        for link in soup.select("li a[href*='/info/']"):
            title = link.get("title") or link.get_text(strip=True)
            href = link.get("href", "")
            if base_url:
                full_url = urljoin(base_url + "/", href)
            else:
                full_url = href
            parent = link.find_parent("li")
            date_text = ""
            if parent:
                date_match = re.search(r"\d{4}-\d{2}-\d{2}", parent.get_text())
                date_text = date_match.group() if date_match else ""
            articles.append({"url": full_url, "title": title, "date": date_text})
        return articles
