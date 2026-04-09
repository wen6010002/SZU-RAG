class VsbPagination:
    @staticmethod
    def generate_page_urls(first_page_url: str, total_pages: int) -> list[str]:
        if total_pages <= 1:
            return []
        base = first_page_url.rsplit(".", 1)[0]
        urls = []
        for page_num in range(2, total_pages + 1):
            page_index = total_pages - page_num + 1
            urls.append(f"{base}/{page_index}.htm")
        return urls
