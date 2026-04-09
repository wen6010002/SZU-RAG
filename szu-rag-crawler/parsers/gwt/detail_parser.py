"""
parsers/gwt/detail_parser.py
公文通详情页解析器

职责：从详情页HTML中提取文章完整内容（标题、日期、正文、附件）。
选择器全部从站点配置读取，支持运行时覆盖。
"""

from dataclasses import dataclass, field
from typing import Optional
from urllib.parse import urljoin

from parsel import Selector


@dataclass
class Attachment:
    """附件信息。"""
    url: str
    name: str  # 统一使用 "name" 字段，与 AttachmentPipeline 一致


@dataclass
class DetailResult:
    """详情页解析结果。"""
    title: str = ""
    publish_date: str = ""       # 原始日期文本
    content_html: str = ""       # 正文HTML
    attachments: list[Attachment] = field(default_factory=list)
    parse_warnings: list[str] = field(default_factory=list)


# 默认选择器（占位，公文通页面结构未知）
DEFAULT_DETAIL_SELECTORS = {
    "title": "div.art-tit h3",               # 文章标题
    "date": "span.art-date",                   # 发布日期
    "content": "div#vsb_content",              # 正文内容
    "attachments": "a[href*='download']",       # 附件链接
}


def parse_detail_page(
    html: str,
    base_url: str,
    selectors: dict | None = None,
    fallback_title: str = "",
) -> DetailResult:
    """
    解析公文通详情页。

    Args:
        html:           详情页HTML
        base_url:       站点基础URL，用于拼接附件相对链接
        selectors:      选择器配置，覆盖默认值。格式：
                        { "title": "...", "date": "...", "content": "...", "attachments": "..." }
        fallback_title: 列表页传来的标题，详情页提取失败时使用

    Returns:
        DetailResult 包含文章完整内容
    """
    sel_cfg = {**DEFAULT_DETAIL_SELECTORS, **(selectors or {})}
    doc = Selector(html)

    result = DetailResult()

    # 标题
    title_css = sel_cfg["title"]
    result.title = doc.css(f"{title_css}::text").get("").strip()
    if not result.title:
        result.title = fallback_title
        result.parse_warnings.append("详情页标题提取为空，使用列表页标题")

    # 发布日期
    date_css = sel_cfg["date"]
    result.publish_date = doc.css(f"{date_css}::text").get("").strip()

    # 正文内容
    content_css = sel_cfg["content"]
    content_node = doc.css(content_css)
    result.content_html = content_node.get("")
    if not result.content_html:
        result.parse_warnings.append(f"正文提取为空，选择器: {content_css}")

    # 附件链接
    attach_css = sel_cfg["attachments"]
    for a_tag in doc.css(attach_css):
        href = a_tag.css("::attr(href)").get("")
        if not href:
            continue
        # 文件名：优先取链接文本，否则取URL最后一段
        text = a_tag.css("::text").get("").strip()
        filename = text or href.split("/")[-1].split("?")[0]
        result.attachments.append(Attachment(
            url=urljoin(base_url, href),
            name=filename,  # 统一用 name 字段
        ))

    return result
