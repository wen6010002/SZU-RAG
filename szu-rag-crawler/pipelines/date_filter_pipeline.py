"""
pipelines/date_filter_pipeline.py
Pipeline层日期兜底过滤

职责：作为Spider层日期过滤的保险，过滤漏网之鱼。
     仅在 Spider 未能正确过滤时生效。
"""

from scrapy.exceptions import DropItem
from config.settings import load_sites_config
from middlewares.date_filter import create_date_filter, parse_date, should_crawl


class DateFilterPipeline:
    """Pipeline层日期过滤（兜底）。"""

    def __init__(self):
        self.date_filters = {}
        sites_config = load_sites_config()
        for key, cfg in sites_config.items():
            self.date_filters[key] = create_date_filter(cfg)

    def process_item(self, item, spider):
        site_name = item.get("site_name", "")
        date_filter = self.date_filters.get(site_name)

        if not date_filter or not date_filter.enabled:
            return item

        publish_date_str = item.get("publish_date", "")
        if not publish_date_str:
            # 日期为空，保守放行
            return item

        article_date = parse_date(publish_date_str)
        ok, reason = should_crawl(article_date, date_filter)

        if not ok:
            raise DropItem(f"日期过期(Pipeline层): {reason}")

        return item
