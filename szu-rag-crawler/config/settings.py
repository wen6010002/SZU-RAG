"""
config/settings.py
Pydantic配置模型 — 升级版

升级内容：
  - 新增 SZU_USERNAME / SZU_PASSWORD（CAS凭据）
  - 新增 DEFAULT_DATE_FILTER_AFTER（全局默认日期过滤天数）
  - 新增 COOKIE_DIR（Cookie持久化目录）
  - 新增 GWT_SPIDER_ENABLED（公文通Spider开关）
  - 入口处 load_dotenv() 确保 os.getenv() 可用
"""

import os
from pathlib import Path
from typing import Optional

# R2修复：在Pydantic读取.env之前，先用load_dotenv写入os.environ
# 这样 auth_service 等模块的 os.getenv() 也能获取到值
from dotenv import load_dotenv
load_dotenv()

from pydantic_settings import BaseSettings
from pydantic import Field
import yaml


# ------------------------------------------------------------------ #
#  环境变量配置
# ------------------------------------------------------------------ #

class AppConfig(BaseSettings):
    """应用全局配置，从 .env 文件读取。"""

    # --- 基础 ---
    PROJECT_ROOT: Path = Field(default=Path(__file__).parent.parent)
    DATA_DIR: Path = Field(default=Path("data"))
    LOG_LEVEL: str = Field(default="INFO")

    # --- 后端推送 ---
    RAGENT_API_BASE: str = Field(default="http://localhost:8080")
    RAGENT_UPLOAD_ENDPOINT: str = Field(default="/api/v1/knowledge/documents/upload")
    RAGENT_API_KEY: str = Field(default="")

    # --- 附件 ---
    ATTACHMENT_DIR: Path = Field(default=Path("data/attachments"))
    ATTACHMENT_MAX_SIZE_MB: int = Field(default=50)

    # --- 去重 ---
    DEDUP_DB_PATH: str = Field(default="data/dedup.db")

    # --- CAS认证（公文通） ---
    SZU_USERNAME: str = Field(default="")
    SZU_PASSWORD: str = Field(default="")

    # --- Cookie持久化 ---
    COOKIE_DIR: str = Field(default="data/cookies")

    # --- 日期过滤 ---
    DEFAULT_DATE_FILTER_AFTER: int = Field(
        default=180,
        description="全局默认日期过滤天数，sites.yaml中可按站点覆盖",
    )

    # --- Spider开关 ---
    GWT_SPIDER_ENABLED: bool = Field(
        default=True,
        description="是否启用公文通Spider（关闭后不爬取gwt站点）",
    )

    # --- 定时任务 ---
    SCHEDULER_VSB_FULL_CRON: str = Field(default="0 2 * * *")      # 每天凌晨2点
    SCHEDULER_VSB_INCR_CRON: str = Field(default="0 6,12,18 * * *") # 每天6/12/18点
    SCHEDULER_GWT_FULL_CRON: str = Field(default="30 2 * * *")      # 每天凌晨2:30
    SCHEDULER_GWT_INCR_CRON: str = Field(default="30 6,12,18 * * *")# 每天6:30/12:30/18:30

    # --- Scrapy ---
    SCRAPY_SETTINGS_MODULE: str = Field(default="settings")

    model_config = {
        "env_file": ".env",
        "env_file_encoding": "utf-8",
        "extra": "ignore",
    }


# 全局单例
_config: Optional[AppConfig] = None


def get_config() -> AppConfig:
    """获取全局配置单例。"""
    global _config
    if _config is None:
        _config = AppConfig()
    return _config


# ------------------------------------------------------------------ #
#  sites.yaml 加载
# ------------------------------------------------------------------ #

_sites_cache: Optional[dict] = None


def load_sites_config() -> dict:
    """
    加载 sites.yaml 站点配置。

    Returns:
        { "jwb": {...}, "www": {...}, "gwt": {...}, ... }
    """
    global _sites_cache
    if _sites_cache is not None:
        return _sites_cache

    config = get_config()
    yaml_path = config.PROJECT_ROOT / "config" / "sites.yaml"

    if not yaml_path.exists():
        raise FileNotFoundError(f"sites.yaml 不存在: {yaml_path}")

    with open(yaml_path, "r", encoding="utf-8") as f:
        _sites_cache = yaml.safe_load(f) or {}

    return _sites_cache


def reload_sites_config() -> dict:
    """强制重新加载 sites.yaml（热更新用）。"""
    global _sites_cache
    _sites_cache = None
    return load_sites_config()
