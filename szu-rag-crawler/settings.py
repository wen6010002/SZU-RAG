"""
settings.py
Scrapy项目配置 — 升级版

升级内容：
  - Pipeline链新增日期筛选(50)
  - 启用Cookie中间件（公文通登录态）
  - 新增GWT Spider相关配置
"""

BOT_NAME = "szu_rag_crawler"
SPIDER_MODULES = ["spiders"]
NEWSPIDER_MODULE = "spiders"

# ------------------------------------------------------------------ #
#  Pipeline 链路
# ------------------------------------------------------------------ #
# 顺序：日期筛选(50) → 去重(100) → 附件下载(200) → 推送(300)
#
#  50   DateFilterPipeline   Pipeline层日期兜底过滤（优先执行，避免过期item入库指纹）
#  100  DedupPipeline        URL指纹+内容MD5去重，标记 new/duplicate/updated
#  200  AttachmentPipeline   httpx下载附件到 data/attachments/
#  300  RagentPushPipeline   推送到Java后端 /api/v1/knowledge/documents/upload

ITEM_PIPELINES = {
    "pipelines.date_filter_pipeline.DateFilterPipeline": 50,
    "pipelines.dedup_pipeline.DedupPipeline": 100,
    "pipelines.attachment_pipeline.AttachmentPipeline": 200,
    "pipelines.ragent_push_pipeline.RagentPushPipeline": 300,
}

# ------------------------------------------------------------------ #
#  请求控制
# ------------------------------------------------------------------ #

# 全局下载延迟，各站点可在 sites.yaml 中覆盖
DOWNLOAD_DELAY = 1.0

# 并发请求数，各站点可覆盖
CONCURRENT_REQUESTS = 2

# Cookie中间件（公文通CAS登录态需要）
COOKIES_ENABLED = True
COOKIES_DEBUG = False

# 重试配置
RETRY_ENABLED = True
RETRY_TIMES = 3
RETRY_HTTP_CODES = [500, 502, 503, 504, 408, 429]

# 超时
DOWNLOAD_TIMEOUT = 30

# ------------------------------------------------------------------ #
#  User-Agent
# ------------------------------------------------------------------ #

USER_AGENT = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/120.0.0.0 Safari/537.36"
)

# ------------------------------------------------------------------ #
#  日志
# ------------------------------------------------------------------ #

LOG_LEVEL = "INFO"
LOG_FORMAT = "%(asctime)s [%(name)s] %(levelname)s: %(message)s"

# ------------------------------------------------------------------ #
#  Robot协议（校内站点，已获授权）
# ------------------------------------------------------------------ #

ROBOTSTXT_OBEY = False

# ------------------------------------------------------------------ #
#  自动限速
# ------------------------------------------------------------------ #

AUTOTHROTTLE_ENABLED = True
AUTOTHROTTLE_START_DELAY = 1.0
AUTOTHROTTLE_MAX_DELAY = 5.0
AUTOTHROTTLE_TARGET_CONCURRENCY = 1.0
