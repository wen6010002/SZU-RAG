from dataclasses import dataclass, field
from typing import Optional
from urllib.parse import urljoin

from parsel import Selector


@dataclass
class ListItem:
    """列表页单条记录。"""
    title: str
    url: str           # 详情页完整URL
    date_text: str     # 原始日期文本
    raw_html: str = ""  # 保留原始HTML片段，供调试


@dataclass
class ListParseResult:
    """列表页解析结果。"""
    items: list[ListItem] = field(default_factory=list)
    total_parsed: int = 0       # 原始匹配到的行数
    skipped_no_title: int = 0
    skipped_no_link: int = 0


# 默认选择器（公文通实际页面结构）
DEFAULT_LIST_SELECTORS = {
    "item": "table tr",                          # 列表行
    "title": "td:nth-child(4) a.fontcolor3",     # 标题链接（第4列）
    "link": "td:nth-child(4) a[href^='view.asp']", # 详情链接
    "date": "td:nth-child(6)",                   # 日期列（最后一列）
}


def parse_list_page(
    html: str,
    base_url: str,
    selectors: dict | None = None,
) -> ListParseResult:
    """
    解析公文通列表页。

    Args:
        html:       列表页HTML
        base_url:   站点基础URL，用于拼接相对链接
        selectors:  选择器配置，覆盖默认值。格式：
                    { "item": "...", "title": "...", "link": "...", "date": "..." }

    Returns:
        ListParseResult 包含提取的文章条目和统计信息
    """
    # 合并选择器：用户配置覆盖默认值
    sel_cfg = {**DEFAULT_LIST_SELECTORS, **(selectors or {})}
    doc = Selector(html)

    item_css = sel_cfg["item"]
    title_css = sel_cfg["title"]
    link_css = sel_cfg["link"]
    date_css = sel_cfg["date"]

    result = ListParseResult()

    rows = doc.css(item_css)
    result.total_parsed = len(rows)

    for row in rows:
        # 标题
        title = row.css(f"{title_css}::text").get("").strip()

        # 链接
        href = row.css(f"{link_css}::attr(href)").get("")
        if not href:
            # 尝试从title元素本身取href
            href = row.css(f"{title_css}::attr(href)").get("")

        # 跳过无效条目
        if not title:
            result.skipped_no_title += 1
            continue
        if not href:
            result.skipped_no_link += 1
            continue

        # 日期文本
        date_text = row.css(f"{date_css}::text").get("").strip()

        result.items.append(ListItem(
            title=title,
            url=urljoin(base_url, href),
            date_text=date_text,
            raw_html=row.get(),
        ))

    return result
