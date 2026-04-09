"""
spiders/gwt_spider.py
公文通Spider — 深大www1公文系统专用爬虫

流程：
  start_requests → auth_service获取Cookie
  → 遍历columns生成列表页请求
  → parse_list：调用 parsers/gwt/list_parser 解析列表、日期过滤、翻页
  → parse_detail：调用 parsers/gwt/detail_parser 解析详情页
"""

import re
from datetime import datetime, timedelta
from urllib.parse import urljoin, urlencode

import scrapy
from parsel import Selector
from markdownify import markdownify as md

from services.auth_service import CASAuthService
from config.settings import load_sites_config
from middlewares.date_filter import parse_date, should_crawl, create_date_filter, DateFilterConfig
from parsers.gwt.list_parser import parse_list_page
from parsers.gwt.detail_parser import parse_detail_page
from parsers.gwt.pagination import get_next_page_url


class GwtSpider(scrapy.Spider):
    """公文通Spider，name='gwt'，由 sites.yaml 中 spider='gwt' 触发。"""

    name = "gwt"

    def __init__(
        self,
        site_name: str = "gwt",
        mode: str = "incremental",
        *args,
        **kwargs,
    ):
        """
        Args:
            site_name: sites.yaml 中的站点键名，默认 "gwt"
            mode:      爬取模式，"full" 或 "incremental"
        """
        super().__init__(*args, **kwargs)
        self.mode = mode
        self.site_name = site_name

        # 加载站点配置
        sites_config = load_sites_config()
        if site_name not in sites_config:
            raise ValueError(f"站点 {site_name} 不在 sites.yaml 中")
        self.config = sites_config[site_name]

        self.base_url = self.config["base_url"]
        self.columns = [
            c for c in self.config.get("columns", []) if c.get("enabled", True)
        ]
        self.selectors = self.config.get("selectors", {})
        self.crawl_cfg = self.config.get("crawl", {})
        self.date_filter_cfg = self.config.get("date_filter", {})

        # 认证服务
        self.auth_service = CASAuthService(self.config)

        # 日期过滤配置（使用统一的 date_filter 中间件）
        self.date_filter = create_date_filter(self.config)
        if self.date_filter.enabled:
            self.logger.info(
                f"日期过滤已启用，仅爬取 "
                f"{self.date_filter.date_after.date() if self.date_filter.date_after else 'N/A'} 之后的文章"
            )

        # 增量模式下限制页数
        if mode == "incremental":
            self.max_pages = self.crawl_cfg.get("incremental_pages", 5)
        else:
            self.max_pages = 200  # 全量模式安全上限，防止无限翻页

        # 已见URL集合（Spider层去重，避免重复请求详情页）
        self.seen_urls = set()

    # ------------------------------------------------------------------ #
    #  入口：认证 + 生成列表页请求
    # ------------------------------------------------------------------ #

    def start_requests(self):
        """
        Spider入口：
          1. 调用 auth_service 获取已认证Cookie
          2. 遍历启用的栏目，生成列表页第1页请求
        """
        try:
            cookies = self.auth_service.get_cookies()
        except Exception as e:
            self.logger.error(f"认证失败: {e}")
            return

        self.logger.info(
            f"认证成功，开始爬取 {len(self.columns)} 个栏目，模式={self.mode}"
        )

        for column in self.columns:
            url = column["url_pattern"].format(
                base_url=self.base_url,
                column_id=column.get("column_id", ""),
            )
            self.logger.info(f"栏目 [{column['name']}] → {url}")

            yield scrapy.Request(
                url=url,
                callback=self.parse_list,
                cookies=cookies,
                meta={
                    "column": column,
                    "page": 1,
                    "cookies": cookies,  # 传递给后续请求
                },
                dont_filter=True,
                errback=self._handle_auth_error,
            )

    # ------------------------------------------------------------------ #
    #  列表页解析
    # ------------------------------------------------------------------ #

    def parse_list(self, response):
        """
        解析列表页：
          1. 调用 parsers/gwt/list_parser 提取文章条目
          2. 日期过滤：使用 middlewares.date_filter 的 should_crawl()
          3. Spider层去重
          4. 翻页处理：调用 parsers/gwt/pagination
        """
        column = response.meta["column"]
        page = response.meta["page"]
        cookies = response.meta["cookies"]

        list_sel = self.selectors.get("list", {})

        # 调用独立的列表页解析器
        result = parse_list_page(
            html=response.text,
            base_url=self.base_url,
            selectors=list_sel,
        )

        if not result.items:
            self.logger.warning(
                f"栏目 [{column['name']}] 第{page}页未找到条目，"
                f"选择器可能需要调整。解析统计: 总匹配{result.total_parsed}, "
                f"无标题跳过{result.skipped_no_title}, 无链接跳过{result.skipped_no_link}"
            )
            return

        article_count = 0
        skipped_date = 0
        skipped_seen = 0
        stop_paging = False

        for item in result.items:
            # Spider层去重
            if item.url in self.seen_urls:
                skipped_seen += 1
                continue
            self.seen_urls.add(item.url)

            # 日期过滤（使用统一的 date_filter 中间件）
            article_date = parse_date(item.date_text)
            ok, reason = should_crawl(article_date, self.date_filter)
            if not ok:
                skipped_date += 1
                self.logger.debug(f"跳过: {reason} - {item.title}")
                # 增量模式下，遇到过期日期可提前停止翻页
                if self.mode == "incremental":
                    stop_paging = True
                continue

            # 请求详情页
            article_count += 1
            yield scrapy.Request(
                url=item.url,
                callback=self.parse_detail,
                cookies=cookies,
                meta={
                    "column": column,
                    "cookies": cookies,
                    "article_date": article_date,
                    "list_title": item.title,
                },
                errback=self._handle_auth_error,
            )

        self.logger.info(
            f"栏目 [{column['name']}] 第{page}页: "
            f"解析{result.total_parsed}条, 请求{article_count}条详情, "
            f"日期跳过{skipped_date}, 已见跳过{skipped_seen}"
        )

        # 翻页
        if stop_paging:
            self.logger.info(
                f"栏目 [{column['name']}] 增量模式遇到过期文章，停止翻页"
            )
            return

        if self.max_pages and page >= self.max_pages:
            self.logger.info(
                f"栏目 [{column['name']}] 已达页数上限({self.max_pages})，停止翻页"
            )
            return

        next_page_url = get_next_page_url(
            html=response.text,
            current_url=response.url,
            current_page=page,
            base_url=self.base_url,
            selectors=self.selectors.get("pagination", {}),
        )
        if next_page_url:
            yield scrapy.Request(
                url=next_page_url,
                callback=self.parse_list,
                cookies=cookies,
                meta={
                    "column": column,
                    "page": page + 1,
                    "cookies": cookies,
                },
                dont_filter=True,
                errback=self._handle_auth_error,
            )

    # ------------------------------------------------------------------ #
    #  详情页解析
    # ------------------------------------------------------------------ #

    def parse_detail(self, response):
        """
        解析详情页：调用 parsers/gwt/detail_parser 提取完整内容。

        提取字段：
          - title:      文章标题
          - publish_date: 发布日期
          - content:    正文HTML
          - content_md: 正文Markdown（新增，供下游Pipeline使用）
          - attachments: 附件链接列表
          - url:        原文URL
          - site_name:  站点标识
          - column:     所属栏目
        """
        detail_sel = self.selectors.get("detail", {})

        # 调用独立的详情页解析器
        result = parse_detail_page(
            html=response.text,
            base_url=self.base_url,
            selectors=detail_sel,
            fallback_title=response.meta.get("list_title", ""),
        )

        # 发布日期（详情页优先，回退列表页）
        publish_date = (
            parse_date(result.publish_date)
            or response.meta.get("article_date")
        )

        # 日期二次校验（兜底过滤）
        ok, reason = should_crawl(publish_date, self.date_filter)
        if not ok:
            self.logger.debug(f"详情页过滤: {reason} - {result.title}")
            return  # 直接丢弃，不yield

        # HTML转Markdown（R3修复：补充content_md字段）
        content_md = md(result.content_html) if result.content_html else ""

        # 记录解析告警
        for warning in result.parse_warnings:
            self.logger.warning(f"详情页解析: {warning} - {response.url}")

        # 附件字段统一用 "name" 键（R4修复）
        attachments = [
            {"url": att.url, "name": att.name}
            for att in result.attachments
        ]

        yield {
            "title": result.title,
            "publish_date": publish_date.strftime("%Y-%m-%d") if publish_date else "",
            "content": result.content_html,
            "content_md": content_md,
            "attachments": attachments,
            "url": response.url,
            "site_name": self.site_name,
            "base_url": self.base_url,
            "column": response.meta["column"].get("name", ""),
        }

    # ------------------------------------------------------------------ #
    #  工具方法
    # ------------------------------------------------------------------ #

    def _handle_auth_error(self, failure):
        """
        请求错误回调：检测Cookie过期并尝试重新登录（R8修复）。
        """
        response = getattr(failure, "value", None)
        if response and hasattr(response, "status"):
            if response.status in (403, 401):
                self.logger.warning(
                    f"请求返回 {response.status}，Cookie可能已过期: {response.url}"
                )
                # 尝试重新登录获取新Cookie
                try:
                    new_cookies = self.auth_service.get_cookies()
                    self.logger.info("重新登录成功，用新Cookie重试请求")
                    return failure.request.replace(
                        cookies=new_cookies,
                        dont_filter=True,
                    )
                except Exception as e:
                    self.logger.error(f"重新登录失败: {e}")
            else:
                self.logger.error(f"请求失败 [{response.status}]: {response.url}")
        else:
            self.logger.error(f"请求异常: {failure}")

    def closed(self, reason):
        """Spider关闭时清理认证服务资源。"""
        self.auth_service.close()
        self.logger.info(f"GwtSpider 已关闭: {reason}")
