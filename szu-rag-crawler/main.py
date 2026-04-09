"""
main.py
FastAPI管理接口 — 升级版

升级内容：
  - 新增 GWT Spider 触发接口
  - VSB / GWT 独立定时任务
  - 站点列表API返回 spider 类型（vsb/gwt）
  - 指纹统计新增日期维度

修复项：
  - R1: BackgroundScheduler调用async函数 → 用同步wrapper
  - R9: shell=True命令注入 → 改为shell=False + 列表参数
  - O12: subprocess完成回调 → 更新任务状态
"""

import subprocess
import threading
import uuid
from datetime import datetime
from typing import Optional

from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.cron import CronTrigger
from fastapi import FastAPI, HTTPException, Query
from pydantic import BaseModel

from config.settings import get_config, load_sites_config
from pipelines.dedup_pipeline import DedupPipeline

app = FastAPI(title="SZU RAG Crawler 管理", version="2.0")
scheduler = BackgroundScheduler()

# 运行中的任务
running_tasks: dict[str, dict] = {}


# ------------------------------------------------------------------ #
#  爬取触发
# ------------------------------------------------------------------ #

class CrawlRequest(BaseModel):
    """爬取请求参数。"""
    spider: str = "vsb"              # vsb / gwt / all
    site_name: str = ""               # 指定站点，空=全部
    mode: str = "incremental"         # full / incremental


def _trigger_crawl_sync(req: CrawlRequest):
    """
    R1修复：同步版本的爬取触发，供BackgroundScheduler调用。
    BackgroundScheduler在后台线程执行，不能直接调用async def。
    """
    task_id = str(uuid.uuid4())[:8]

    config = get_config()

    if req.spider == "gwt" and not config.GWT_SPIDER_ENABLED:
        return

    commands = _build_scrapy_commands(req)
    for cmd_info in commands:
        task_key = f"{task_id}_{cmd_info['spider']}"
        running_tasks[task_key] = {
            "task_id": task_id,
            "spider": cmd_info["spider"],
            "site_name": req.site_name,
            "mode": req.mode,
            "status": "running",
            "started_at": datetime.now().isoformat(),
            "pid": None,
        }
        _run_scrapy(task_key, cmd_info["command_args"])


@app.post("/api/v1/crawl/trigger")
async def trigger_crawl(req: CrawlRequest):
    """
    触发爬取任务。

    - spider="vsb": 运行 VsbSpider
    - spider="gwt": 运行 GwtSpider
    - spider="all": 依次运行两个Spider
    """
    task_id = str(uuid.uuid4())[:8]

    config = get_config()

    if req.spider == "gwt" and not config.GWT_SPIDER_ENABLED:
        raise HTTPException(status_code=400, detail="公文通Spider未启用")

    commands = _build_scrapy_commands(req)

    for cmd_info in commands:
        task_key = f"{task_id}_{cmd_info['spider']}"
        running_tasks[task_key] = {
            "task_id": task_id,
            "spider": cmd_info["spider"],
            "site_name": req.site_name,
            "mode": req.mode,
            "status": "running",
            "started_at": datetime.now().isoformat(),
            "pid": None,
        }
        _run_scrapy(task_key, cmd_info["command_args"])

    return {
        "task_id": task_id,
        "spiders": [c["spider"] for c in commands],
        "mode": req.mode,
        "status": "started",
    }


def _build_scrapy_commands(req: CrawlRequest) -> list[dict]:
    """
    根据请求构造Scrapy命令列表。

    R9修复：使用列表参数而非字符串拼接，避免命令注入。
    """
    commands = []

    if req.spider in ("vsb", "all"):
        cmd_args = ["scrapy", "crawl", "vsb", "-a", f"mode={req.mode}"]
        if req.site_name:
            cmd_args.extend(["-a", f"site_name={req.site_name}"])
        commands.append({
            "spider": "vsb",
            "command_args": cmd_args,
        })

    if req.spider in ("gwt", "all"):
        cmd_args = ["scrapy", "crawl", "gwt", "-a", f"mode={req.mode}"]
        commands.append({
            "spider": "gwt",
            "command_args": cmd_args,
        })

    return commands


def _run_scrapy(task_key: str, command_args: list[str]):
    """
    在子进程中运行Scrapy爬取。

    R9修复：使用shell=False + 列表参数，避免命令注入。
    O12修复：启动后台线程等待子进程完成，更新任务状态。
    """
    config = get_config()
    proc = subprocess.Popen(
        command_args,
        shell=False,  # R9修复：禁用shell
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        cwd=str(config.PROJECT_ROOT),
    )
    running_tasks[task_key]["pid"] = proc.pid

    # O12修复：后台线程等待完成，更新状态
    def _wait_for_completion():
        try:
            stdout, stderr = proc.communicate(timeout=3600)
            if proc.returncode == 0:
                running_tasks[task_key]["status"] = "completed"
            else:
                running_tasks[task_key]["status"] = "failed"
                running_tasks[task_key]["log"] = (stderr or stdout).decode("utf-8", errors="replace")[-500:]
        except subprocess.TimeoutExpired:
            proc.kill()
            running_tasks[task_key]["status"] = "timeout"
        except Exception as e:
            running_tasks[task_key]["status"] = "failed"
            running_tasks[task_key]["log"] = str(e)
        finally:
            running_tasks[task_key]["finished_at"] = datetime.now().isoformat()

    thread = threading.Thread(target=_wait_for_completion, daemon=True)
    thread.start()


# ------------------------------------------------------------------ #
#  任务查询
# ------------------------------------------------------------------ #

@app.get("/api/v1/crawl/tasks")
async def list_tasks():
    """查询所有爬取任务。"""
    return {"tasks": list(running_tasks.values())}


@app.get("/api/v1/crawl/tasks/{task_id}")
async def get_task(task_id: str):
    """查询单个任务状态。"""
    matching = {k: v for k, v in running_tasks.items() if v["task_id"] == task_id}
    if not matching:
        raise HTTPException(status_code=404, detail="任务不存在")
    return {"tasks": list(matching.values())}


# ------------------------------------------------------------------ #
#  站点列表
# ------------------------------------------------------------------ #

@app.get("/api/v1/sites")
async def list_sites():
    """列出所有站点及其配置。"""
    sites_config = load_sites_config()
    result = []
    for key, cfg in sites_config.items():
        spider_type = cfg.get("spider", "vsb")
        date_filter = cfg.get("date_filter", {})
        result.append({
            "key": key,
            "name": cfg.get("name", key),
            "spider": spider_type,
            "base_url": cfg.get("base_url", ""),
            "columns_count": len([c for c in cfg.get("columns", []) if c.get("enabled", True)]),
            "date_filter_enabled": date_filter.get("enabled", False),
            "date_filter_max_age": date_filter.get("max_age_days", 0),
            "crawl_mode": cfg.get("crawl", {}).get("mode", "incremental"),
        })
    return {"sites": result}


# ------------------------------------------------------------------ #
#  指纹统计
# ------------------------------------------------------------------ #

@app.get("/api/v1/dedup/stats")
async def dedup_stats(site_name: str = ""):
    """去重指纹统计。"""
    config = get_config()
    pipeline = DedupPipeline(db_path=config.DEDUP_DB_PATH)
    pipeline.open_spider(None)  # type: ignore
    try:
        return pipeline.get_stats(site_name)
    finally:
        pipeline.close_spider(None)  # type: ignore


# ------------------------------------------------------------------ #
#  配置热更新
# ------------------------------------------------------------------ #

@app.post("/api/v1/config/reload")
async def reload_config():
    """重新加载 sites.yaml 配置。"""
    from config.settings import reload_sites_config
    sites = reload_sites_config()
    return {"status": "ok", "sites_loaded": list(sites.keys())}


# ------------------------------------------------------------------ #
#  定时任务
# ------------------------------------------------------------------ #

def _setup_scheduled_jobs():
    """配置定时爬取任务。"""
    config = get_config()

    # VSB 全量（凌晨2点）
    scheduler.add_job(
        _trigger_crawl_sync,  # R1修复：用同步wrapper
        CronTrigger.from_crontab(config.SCHEDULER_VSB_FULL_CRON),
        kwargs={"req": CrawlRequest(spider="vsb", mode="full")},
        id="vsb_full",
        name="VSB全量爬取",
        replace_existing=True,
    )

    # VSB 增量（6/12/18点）
    scheduler.add_job(
        _trigger_crawl_sync,
        CronTrigger.from_crontab(config.SCHEDULER_VSB_INCR_CRON),
        kwargs={"req": CrawlRequest(spider="vsb", mode="incremental")},
        id="vsb_incremental",
        name="VSB增量爬取",
        replace_existing=True,
    )

    # GWT 全量（凌晨2:30，错开VSB）
    if config.GWT_SPIDER_ENABLED:
        scheduler.add_job(
            _trigger_crawl_sync,
            CronTrigger.from_crontab(config.SCHEDULER_GWT_FULL_CRON),
            kwargs={"req": CrawlRequest(spider="gwt", mode="full")},
            id="gwt_full",
            name="公文通全量爬取",
            replace_existing=True,
        )

        # GWT 增量（6:30/12:30/18:30，错开VSB）
        scheduler.add_job(
            _trigger_crawl_sync,
            CronTrigger.from_crontab(config.SCHEDULER_GWT_INCR_CRON),
            kwargs={"req": CrawlRequest(spider="gwt", mode="incremental")},
            id="gwt_incremental",
            name="公文通增量爬取",
            replace_existing=True,
        )


@app.on_event("startup")
async def startup():
    """FastAPI启动时初始化定时任务。"""
    _setup_scheduled_jobs()
    scheduler.start()


@app.on_event("shutdown")
async def shutdown():
    """FastAPI关闭时清理。"""
    scheduler.shutdown(wait=False)


@app.get("/health")
async def health():
    return {"status": "ok", "version": "2.0", "service": "szu-rag-crawler"}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8090)
