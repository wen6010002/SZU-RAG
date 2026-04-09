# SZU-RAG MVP 项目启动文档

## 项目简介

**深大智答** (SZU-RAG) — 深圳大学校园智能问答助手，基于 RAG（检索增强生成）架构。

## 技术栈

| 模块 | 技术栈 |
|------|--------|
| 后端 | Java 17 + Spring Boot 3.5.7 + MyBatis Plus 3.5.9 |
| 向量数据库 | Milvus v2.4.17 |
| 关系数据库 | MySQL 8.0 |
| 缓存 | Redis 7 |
| LLM | DeepSeek V3 (OpenAI 兼容 API) |
| Embedding | 阿里百炼 DashScope (text-embedding-v3, 1024维) |
| 前端 | React 18 + Vite 6 + TypeScript + TailwindCSS + Zustand |
| 爬虫 | Python 3.11 + Scrapy + FastAPI + APScheduler |
| 部署 | Docker Compose |

## 项目结构

```
Desktop/
├── docker-compose.yml          # 全栈编排
├── .env.example                # 环境变量模板
├── szu-rag-backend/            # Java 后端
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/resources/
│       ├── application.yml         # 开发配置
│       ├── application-prod.yml    # 生产配置
│       └── db/schema.sql           # 数据库建表脚本
├── szu-rag-crawler/            # Python 爬虫
│   ├── Dockerfile
│   ├── main.py                     # FastAPI + APScheduler
│   ├── config/sites.yaml           # 站点配置
│   └── spiders/vsb_spider.py       # VSB 站点爬虫
└── szu-rag-frontend/           # React 前端
    ├── Dockerfile
    ├── nginx.conf                   # Nginx + SSE 代理
    └── src/
        ├── api/chat.ts
        ├── store/chatStore.ts
        └── components/
```

---

## 快速启动

### 前置条件

- Docker Desktop 已安装并运行
- Java 17+ (仅本地开发需要)
- Node.js 20.19+ (仅本地开发需要)
- Python 3.11+ (仅本地开发需要)

### 方式一：Docker Compose 全栈部署（推荐）

```bash
# 1. 进入项目根目录
cd ~/Desktop

# 2. 配置环境变量
cp .env.example .env
# 编辑 .env，填入真实的 API Key:
#   DEEPSEEK_API_KEY=sk-xxx
#   BAILIAN_API_KEY=sk-xxx

# 3. 构建 Java 后端 JAR（Docker 构建前需要）
cd szu-rag-backend
mvn clean package -DskipTests
cd ..

# 4. 启动所有服务
docker compose up -d

# 5. 查看服务状态
docker compose ps

# 6. 等待所有服务健康后访问
# 前端: http://localhost
# 后端 API: http://localhost:8088
# 爬虫管理: http://localhost:8090
```

### 方式二：本地开发模式

#### 1. 启动基础设施

```bash
cd ~/Desktop/szu-rag-backend
docker compose -f docker-compose.dev.yml up -d
```

等待 MySQL、Redis、Milvus 全部 healthy（约 30-60 秒）。

#### 2. 启动后端

```bash
cd ~/Desktop/szu-rag-backend

# 设置环境变量
export DEEPSEEK_API_KEY=sk-your-key
export BAILIAN_API_KEY=sk-your-key

# 构建并运行
mvn clean package -DskipTests
java -jar target/szu-rag-backend-1.0.0-SNAPSHOT.jar
```

后端启动在 `http://localhost:8088`

#### 3. 启动前端

```bash
cd ~/Desktop/szu-rag-frontend
npm install
npm run dev
```

前端启动在 `http://localhost:3000`，自动代理 API 到 8088。

#### 4. 启动爬虫

```bash
cd ~/Desktop/szu-rag-crawler
pip install -r requirements.txt
python main.py
```

爬虫管理 API 在 `http://localhost:8090`

---

## API 端点一览

### 后端 (8088)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /health | 健康检查 |
| POST | /api/v1/knowledge/bases | 创建知识库 |
| GET | /api/v1/knowledge/bases | 列出知识库 |
| POST | /api/v1/knowledge/documents/upload | 上传文档 |
| GET | /api/v1/knowledge/bases/{id}/documents | 查看文档列表 |
| POST | /api/v1/chat/conversations | 创建对话 |
| GET | /api/v1/chat/conversations | 对话列表 |
| POST | /api/v1/chat/conversations/{id}/messages | SSE 流式问答 |
| GET | /api/v1/chat/conversations/{id}/messages | 历史消息 |
| DELETE | /api/v1/chat/conversations/{id} | 删除对话 |

### 爬虫 (8090)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /health | 健康检查 |
| POST | /api/v1/crawler/crawl?site_name=xxx | 触发爬取 |
| GET | /api/v1/crawler/task/{taskId} | 查询任务状态 |
| GET | /api/v1/crawler/sites | 查看站点配置 |

---

## 首次使用流程

```bash
# 1. 确认所有服务正常
curl http://localhost:8088/health
curl http://localhost:8090/health

# 2. 创建知识库
curl -s -X POST http://localhost:8088/api/v1/knowledge/bases \
  -H "Content-Type: application/json" \
  -d '{"name":"深大教务处","description":"教务处公开通知和信息"}' | python3 -m json.tool
# 记录返回的 id (如 1)

# 3. 手动触发爬取教务处通知
curl -X POST "http://localhost:8090/api/v1/crawler/crawl?site_name=教务处-教务通知"
# 记录 taskId，等待完成

# 4. 创建对话并提问
curl -s -X POST http://localhost:8088/api/v1/chat/conversations | python3 -m json.tool
# 记录返回的 id

# 5. SSE 流式问答
curl -N -X POST http://localhost:8088/api/v1/chat/conversations/{CONV_ID}/messages \
  -H "Content-Type: application/json" \
  -d '{"question": "深圳大学怎么选课？"}'

# 6. 或直接浏览器访问 http://localhost 使用图形界面
```

---

## 常见问题

| 问题 | 解决方案 |
|------|---------|
| Milvus 启动失败 | Apple Silicon 需要使用 `platform: linux/amd64`，确保 Docker Desktop 内存 ≥ 4GB |
| 端口 8080 被占用 | 后端使用 8088 端口，不冲突 |
| 编译报 Lombok 错误 | 需要 JDK 17+，pom.xml 已配置 annotationProcessorPaths |
| 爬虫推送 404 | 先创建知识库再触发爬取 |
| SSE 流中断 | 检查 Nginx 配置是否有 `proxy_buffering off` |
| Embedding 调用失败 | 检查 BAILIAN_API_KEY 是否正确，DashScope API 是否可达 |

---

## 定时任务

爬虫自动执行计划（在 crawler 的 main.py 中配置）：

| 任务 | 时间 | 说明 |
|------|------|------|
| 全量爬取 | 每天 02:00 | 爬取所有站点 |
| 增量爬取 | 每天 06:00/12:00/18:00 | 爬取最新内容 |

---

## 数据库表结构

共 6 张表：

- `t_user` — 用户表（初始管理员: admin/admin123）
- `t_knowledge_base` — 知识库
- `t_knowledge_document` — 文档
- `t_document_chunk` — 文档分块（含向量索引）
- `t_conversation` — 对话
- `t_message` — 消息记录
