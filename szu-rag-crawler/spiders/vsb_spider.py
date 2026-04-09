"""
spiders/vsb_spider.py
VSB通用Spider — 升级版（加入日期筛选）

变更：
  - 引入 DateFilterConfig，列表页/详情页两级日期过滤
  - 增量模式下遇到过期日期停止翻页（VSB倒序排列，后续更旧）
  - date_filter_after 从 sites.yaml 按站点读取
  - 新增 content_md 字段（HTML转Markdown）
"""

import re
import hashlib
from datetime import datetime, timedelta
from urllib.parse import urljoin

import scrapy
from parsel import Selector
from markdownify import markdownify as md

from config.settings import load_sites_config
from middlewares.date_filter import (
    create_date_filter,
    parse_date,
    should_crawl,
    DateFilterConfig,
)


class VsbSpider(scrapy.Spider):
    """VSB通用Spider，由 sites.yaml 配置驱动，一个Spider覆盖所有VSB站点。"""

    name = "vsb"

    def __init__(
        self,
        site_name: str = "",
        mode: str = "incremental",
        *args,
        **kwargs,
    ):
        """
        Args:
            site_name: 指定爬取的站点键名，空字符串=爬取所有VSB站点
            mode:      爬取模式，"full" 或 "incremental"
        """
        super().__init__(*args, **kwargs)
        self.mode = mode

        # 加载站点配置
        self.sites_config = load_sites_config()

        # 筛选VSB站点（排除gwt等非VSB站点）
        self.target_sites = []
        for key, cfg in self.sites_config.items():
            spider_type = cfg.get("spider", "vsb")
            if spider_type != "vsb":
                continue
            if site_name and key != site_name:
                continue
            self.target_sites.append((key, cfg))

        if not self.target_sites:
            self.logger.warning(f"未找到匹配的VSB站点: site_name={site_name}")

        # 每个站点独立的日期过滤配置
        self.date_filters: dict[str, DateFilterConfig] = {}
        for key, cfg in self.target_sites:
            self.date_filters[key] = create_date_filter(cfg)
            df = self.date_filters[key]
            if df.enabled:
                self.logger.info(
                    f"站点 [{key}] 日期过滤已启用: "
                    f"max_age={df.max_age_days}天, "
                    f"date_after={df.date_after.date() if df.date_after else 'N/A'}"
                )

        # 增量模式页数限制（从配置读取，默认3页）
        self.incremental_pages = 3
        if self.target_sites:
            first_cfg = self.target_sites[0][1]
            self.incremental_pages = first_cfg.get("crawl", {}).get("incremental_pages", 3)

        # Spider层去重集合：{site_name: set(fingerprints)}
        self.seen_per_site: dict[str, set] = {
            key: set() for key, _ in self.target_sites
        }

    # ------------------------------------------------------------------ #
    #  入口
    # ------------------------------------------------------------------ #

    def start_requests(self):
        """遍历目标站点，从每个站点的栏目入口URL开始爬取。"""
        for site_key, site_cfg in self.target_sites:
            base_url = site_cfg["base_url"]
            columns = site_cfg.get("columns", [])

            for column in columns:
                if not column.get("enabled", True):
                    continue
                url = column["url_pattern"].format(
                    base_url=base_url,
                    column_id=column.get("column_id", ""),
                )
                yield scrapy.Request(
                    url=url,
                    callback=self.parse_list,
                    meta={
                        "site_key": site_key,
                        "site_config": site_cfg,
                        "column": column,
                        "page": 1,
                    },
                )

    # ------------------------------------------------------------------ #
    #  列表页解析（含日期过滤）
    # ------------------------------------------------------------------ #

    def parse_list(self, response):
        """
        解析VSB列表页，提取文章条目。

        VSB列表页特征：
          - 倒序排列（最新在前）
          - 结构：ul.news_list > li > a[href*='/info/']
          - 日期通常在 li 内的 span 或文本中
        """
        site_key = response.meta["site_key"]
        site_cfg = response.meta["site_config"]
        column = response.meta["column"]
        page = response.meta["page"]
        date_filter = self.date_filters.get(site_key)

        sel = Selector(response)
        base_url = site_cfg["base_url"]

        # VSB列表选择器
        selectors = site_cfg.get("selectors", {}).get("list", {})
        item_css = selectors.get("item", "ul.news_list li")
        link_css = selectors.get("link", "a[href*='/info/']")
        date_css = selectors.get("date", "span")

        items = sel.css(item_css)
        if not items:
            self.logger.debug(
                f"[{site_key}] 栏目 [{column.get('name', '')}] 第{page}页无条目"
            )
            return

        article_count = 0
        skipped_date = 0
        stop_paging = False

        for item_sel in items:
            # 提取详情链接
            href = item_sel.css(f"{link_css}::attr(href)").get("")
            if not href:
                continue
            detail_url = urljoin(base_url, href)

            # Spider层去重
            fp = self._url_fingerprint(detail_url)
            if fp in self.seen_per_site.get(site_key, set()):
                continue
            self.seen_per_site[site_key].add(fp)

            # 提取标题
            title = item_sel.css(f"{link_css}::text").get("").strip()

            # 提取列表页日期并过滤
            date_text = item_sel.css(f"{date_css}::text").get("").strip()
            article_date = parse_date(date_text)

            ok, reason = should_crawl(article_date, date_filter)
            if not ok:
                skipped_date += 1
                self.logger.debug(f"[{site_key}] 跳过: {reason} - {title}")
                # 增量模式：VSB倒序，遇到过期说明后续更旧
                if self.mode == "incremental":
                    stop_paging = True
                continue

            # 放行 → 请求详情页
            article_count += 1
            yield scrapy.Request(
                url=detail_url,
                callback=self.parse_detail,
                meta={
                    "site_key": site_key,
                    "site_config": site_cfg,
                    "column": column,
                    "list_title": title,
                    "list_date": article_date,
                },
            )

        self.logger.info(
            f"[{site_key}] 栏目 [{column.get('name', '')}] 第{page}页: "
            f"共{len(items)}条, 放行{article_count}, 日期跳过{skipped_date}"
        )

        # 翻页控制
        if stop_paging:
            self.logger.info(
                f"[{site_key}] 增量模式遇到过期文章，停止翻页"
            )
            return

        if self.mode == "incremental" and page >= self.incremental_pages:
            self.logger.info(
                f"[{site_key}] 增量页数上限({self.incremental_pages})，停止翻页"
            )
            return

        # VSB分页：/info/i_list.jsp?...&curpage=N
        next_page = self._vsb_next_page(response, page)
        if next_page:
            yield scrapy.Request(
                url=next_page,
                callback=self.parse_list,
                meta={
                    "site_key": site_key,
                    "site_config": site_cfg,
                    "column": column,
                    "page": page + 1,
                },
            )

    # ------------------------------------------------------------------ #
    #  详情页解析（含日期二次校验）
    # ------------------------------------------------------------------ #

    def parse_detail(self, response):
        """
        解析VSB详情页，提取文章完整内容。

        VSB详情页特征：
          - 标题：div.art-tit h3
          - 正文：div#vsb_content
          - 日期：span.art-date 或 其他位置
        """
        site_key = response.meta["site_key"]
        site_cfg = response.meta["site_config"]
        sel = Selector(response)

        selectors = site_cfg.get("selectors", {}).get("detail", {})

        # 标题
        title_css = selectors.get("title", "div.art-tit h3")
        title = sel.css(f"{title_css}::text").get("").strip()
        if not title:
            title = response.meta.get("list_title", "")

        # 发布日期（详情页优先，回退列表页）
        date_css = selectors.get("date", "span.art-date")
        date_text = sel.css(f"{date_css}::text").get("").strip()
        publish_date = parse_date(date_text) or response.meta.get("list_date")

        # 日期二次校验（兜底过滤）
        date_filter = self.date_filters.get(site_key)
        ok, reason = should_crawl(publish_date, date_filter)
        if not ok:
            self.logger.debug(f"[{site_key}] 详情页过滤: {reason} - {title}")
            return  # 直接丢弃，不yield

        # 正文内容
        content_css = selectors.get("content", "div#vsb_content")
        content_html = sel.css(content_css).get("")

        # HTML转Markdown（R3修复：补充content_md字段，供下游Pipeline使用）
        content_md = md(content_html) if content_html else ""

        # 附件链接
        attach_css = selectors.get("attachments", "a[href*='download']")
        attachments = []
        for a in sel.css(attach_css):
            href = a.css("::attr(href)").get("")
            if href:
                filename = a.css("::text").get("").strip() or href.split("/")[-1]
                attachments.append({
                    "url": urljoin(site_cfg["base_url"], href),
                    "name": filename,  # 统一使用 "name" 字段（R4修复）
                })

        yield {
            "title": title,
            "publish_date": publish_date.strftime("%Y-%m-%d") if publish_date else "",
            "content": content_html,
            "content_md": content_md,
            "attachments": attachments,
            "url": response.url,
            "site_name": site_key,
            "base_url": site_cfg["base_url"],
            "column": response.meta["column"].get("name", ""),
        }

    # ------------------------------------------------------------------ #
    #  VSB分页
    # ------------------------------------------------------------------ #

    @staticmethod
    def _vsb_next_page(response, current_page: int) -> str | None:
        """
        VSB系统分页URL生成。

        VSB分页模式：URL中包含 curpage 参数，递增即可。
        例：/info/i_list.jsp?cata_id=xxx&curpage=2
        """
        url = response.url
        if "curpage=" in url:
            # 替换 curpage 参数
            return re.sub(
                r"curpage=\d+",
                f"curpage={current_page + 1}",
                url,
            )
        else:
            # 添加 curpage 参数
            sep = "&" if "?" in url else "?"
            return f"{url}{sep}curpage={current_page + 1}"

    # ------------------------------------------------------------------ #
    #  工具方法
    # ------------------------------------------------------------------ #

    @staticmethod
    def _url_fingerprint(url: str) -> str:
        """URL指纹：归一化后SHA256取前16位。"""
        # 归一化：去除尾部斜杠、统一小写、去除片段
        normalized = url.rstrip("/").lower().split("#")[0]
        return hashlib.sha256(normalized.encode()).hexdigest()[:16]
