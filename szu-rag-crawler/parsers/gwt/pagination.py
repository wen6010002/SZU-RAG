"""
parsers/gwt/pagination.py
公文通分页URL生成器

职责：根据当前页面和配置生成下一页URL。
支持两种分页模式：
  1. 链接模式：从页面提取"下一页"链接
  2. 参数模式：通过URL查询参数构造分页
"""

from urllib.parse import urlparse, parse_qs, urlencode, urlunparse, urljoin
from parsel import Selector


# 默认分页配置（占位）
DEFAULT_PAGINATION_SELECTORS = {
    "next_page": "a.next",      # "下一页"链接选择器
    "page_param": "page",       # URL分页参数名（空字符串=禁用参数模式）
}


def get_next_page_url(
    html: str,
    current_url: str,
    current_page: int,
    base_url: str,
    selectors: dict | None = None,
) -> str | None:
    """
    获取下一页URL。

    优先尝试链接模式（从页面提取下一页链接），
    回退到参数模式（构造分页参数URL）。

    Args:
        html:          当前页HTML
        current_url:   当前页完整URL
        current_page:  当前页码（从1开始）
        base_url:      站点基础URL
        selectors:     分页选择器配置

    Returns:
        下一页URL，无更多页面时返回 None
    """
    sel_cfg = {**DEFAULT_PAGINATION_SELECTORS, **(selectors or {})}
    doc = Selector(html)

    # 模式1：提取"下一页"链接
    next_css = sel_cfg.get("next_page", "")
    if next_css:
        next_href = doc.css(f"{next_css}::attr(href)").get("")
        if next_href:
            if not next_href.startswith("http"):
                next_href = urljoin(base_url, next_href)
            return next_href

    # 模式2：参数分页
    page_param = sel_cfg.get("page_param", "")
    if not page_param:
        return None

    next_page = current_page + 1
    parsed = urlparse(current_url)
    params = parse_qs(parsed.query)

    # 更新/添加分页参数
    params[page_param] = [str(next_page)]

    # 重新构造URL
    new_query = urlencode({k: v[0] for k, v in params.items()})
    return urlunparse(parsed._replace(query=new_query))
