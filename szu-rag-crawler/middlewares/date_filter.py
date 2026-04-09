"""
middlewares/date_filter.py
统一日期过滤中间件

被 GwtSpider 和 VsbSpider 共用，保持两套Spider的日期过滤逻辑一致。
"""

import re
from datetime import datetime, timedelta
from dataclasses import dataclass


@dataclass
class DateFilterConfig:
    """日期过滤配置。"""
    enabled: bool = False
    max_age_days: int = 180
    date_after: datetime | None = None

    def __post_init__(self):
        if self.enabled and self.date_after is None:
            self.date_after = datetime.now() - timedelta(days=self.max_age_days)


# ------------------------------------------------------------------ #
#  日期解析
# ------------------------------------------------------------------ #

# 日期正则 → 构造函数（按常见程度排序）
DATE_PATTERNS: list[tuple[str, str]] = [
    # 格式名          正则表达式
    ("ISO",           r"(\d{4})-(\d{1,2})-(\d{1,2})"),
    ("中文斜杠",       r"(\d{4})/(\d{1,2})/(\d{1,2})"),
    ("中文点号",       r"(\d{4})\.(\d{1,2})\.(\d{1,2})"),
    ("中文年月日",     r"(\d{4})年(\d{1,2})月(\d{1,2})日"),
]


def parse_date(text: str) -> datetime | None:
    """
    从文本中解析日期。

    遍历 DATE_PATTERNS 尝试匹配，返回第一个成功解析的日期。

    支持格式：
      - 2024-03-15 / 2024-3-5
      - 2024/03/15 / 2024/3/5
      - 2024.03.15
      - 2024年3月15日

    Args:
        text: 可能包含日期的文本

    Returns:
        datetime 对象，解析失败返回 None
    """
    if not text:
        return None

    for _name, pattern in DATE_PATTERNS:
        match = re.search(pattern, text)
        if match:
            try:
                return datetime(
                    int(match.group(1)),
                    int(match.group(2)),
                    int(match.group(3)),
                )
            except ValueError:
                continue

    return None


# ------------------------------------------------------------------ #
#  日期过滤判断
# ------------------------------------------------------------------ #

def should_crawl(
    article_date: datetime | None,
    config: DateFilterConfig,
) -> tuple[bool, str]:
    """
    判断文章是否应该被爬取。

    Args:
        article_date: 文章日期（可能为 None）
        config:       日期过滤配置

    Returns:
        (should_crawl, reason)
        - should_crawl: True=应该爬取，False=应该跳过
        - reason:       判断原因，用于日志
    """
    # 过滤未启用 → 全部放行
    if not config.enabled:
        return True, "日期过滤未启用"

    # 无法解析日期 → 放行（宁可多爬不可漏爬）
    if article_date is None:
        return True, "日期为空，默认放行"

    # 日期有效 → 判断是否在范围内
    if article_date >= config.date_after:
        return True, f"日期有效 ({article_date.date()})"

    # 日期过期 → 跳过
    return False, (
        f"日期过期 ({article_date.date()} < {config.date_after.date()})"
    )


# ------------------------------------------------------------------ #
#  从配置创建过滤器
# ------------------------------------------------------------------ #

def create_date_filter(site_config: dict) -> DateFilterConfig:
    """
    从 sites.yaml 的 date_filter 配置块创建 DateFilterConfig。

    Args:
        site_config: sites.yaml 中的完整站点配置

    Returns:
        DateFilterConfig 实例

    配置格式：
        date_filter:
          enabled: true
          max_age_days: 180
    """
    df_cfg = site_config.get("date_filter", {})
    return DateFilterConfig(
        enabled=df_cfg.get("enabled", False),
        max_age_days=df_cfg.get("max_age_days", 180),
    )
