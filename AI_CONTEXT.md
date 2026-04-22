# SZU-RAG 项目上下文（AI 快速了解）

> 本文档用于让 AI 快速理解 SZU-RAG 项目的全貌，包括架构、代码结构、数据流和关键实现细节。

---

## 一句话总结

**SZU-RAG（深大智答）** 是一个面向深圳大学的校园 RAG 智能问答系统，支持批量文档上传，经 Apache Tika 解析 + 多策略分块 + DashScope Embedding 向量化后存入 Milvus，结合 Multi-Query 查询分解 + DashScope Reranker 重排序 + DeepSeek V3 大模型实现带来源引用的流式问答，前端 React SPA 通过 SSE 接收实时响应。

---

## 技术架构速查

| 组件 | 技术 | 端口 | 说明 |
|------|------|------|------|
| 后端 | Java 17 + Spring Boot 3.5.7 | 8088 | REST API + SSE |
| 前端 | React 19 + Vite 6 + TypeScript | 80 (Nginx) | SPA + SSE 消费 |
| 向量库 | Milvus v2.4.17 | 19530/9091 | 向量存储与检索 |
| 数据库 | MySQL 8.0 | 3306 | 业务数据（7 张表） |
| 缓存 | Redis 7 | 6379 | 会话缓存、限流 |
| LLM | DeepSeek V3 | — | OpenAI 兼容 API |
| Embedding | 阿里百炼 DashScope | — | text-embedding-v3, 1024 维 |
| Reranker | DashScope gte-rerank | — | 检索结果重排序 |

---

## 项目目录结构

```
SZU-RAG/
├── docker-compose.yml              # 全栈编排（8 服务）
├── .env.example                    # 环境变量模板
├── docs/                           # 截图和文档
│
├── szu-rag-backend/                # ========= Java 后端 =========
│   ├── pom.xml                     # Maven 依赖
│   ├── Dockerfile                  # 后端容器
│   ├── docker-compose.dev.yml      # 开发环境（MySQL/Redis/Milvus）
│   └── src/main/
│       ├── resources/
│       │   ├── application.yml     # 开发配置
│       │   ├── application-prod.yml # 生产配置（环境变量注入）
│       │   └── db/schema.sql       # 数据库建表脚本
│       └── java/com/szu/rag/
│           ├── SzRagApplication.java           # 启动入口
│           ├── HealthController.java            # 健康检查
│           │
│           ├── framework/                       # 框架层
│           │   ├── exception/                   # 异常体系（Base → Client/Service/Remote）
│           │   ├── result/                      # Result<T> + PageResult<T>
│           │   ├── context/                     # UserContext (ThreadLocal)
│           │   ├── id/                          # SnowflakeIdWorker
│           │   └── sse/                         # SseEmitterManager + SseEmitterSender
│           │
│           ├── infra/                           # 基础设施层
│           │   ├── chat/                        # ChatClient 接口 + DeepSeekChatClient
│           │   ├── embedding/                   # EmbeddingClient 接口 + BailianEmbeddingClient
│           │   ├── stream/                      # StreamCallback 接口
│           │   ├── token/                       # TokenCounterService
│           │   └── config/                      # AiProperties, AsyncConfig, CorsConfig
│           │
│           ├── ingestion/                       # 文档处理管线
│           │   ├── parser/                      # DocumentParser → Tika/Markdown + Selector
│           │   ├── chunker/                     # FixedSize/StructureAware/Recursive + Factory
│           │   └── pipeline/                    # IngestionEngine + IngestionContext
│           │
│           ├── knowledge/                       # 知识库模块
│           │   ├── model/entity/                # KnowledgeBase, KnowledgeDocument, DocumentChunk
│           │   ├── mapper/                      # MyBatis Plus Mapper
│           │   ├── service/                     # KnowledgeService + IngestionAsyncService
│           │   └── controller/                  # KnowledgeController
│           │
│           ├── rag/                             # RAG 核心模块
│           │   ├── vector/                      # VectorStoreService + MilvusVectorStoreService
│           │   ├── prompt/                      # PromptTemplateLoader + RagPromptService
│           │   ├── memory/                      # ConversationMemory + JdbcConversationMemory
│           │   ├── chat/                        # RagChatService + RagChatServiceImpl
│           │   ├── calendar/                    # 校园日历（CampusCalendarService + Controller）
│           │   ├── query/                       # 查询处理（CampusEntityExpander + MultiQueryExpander + TimeExpressionResolver）
│           │   ├── retrieval/                   # 混合检索（HybridRetrievalService + RerankerService）
│           │   └── ratelimit/                   # RateLimitService
│           │
│           └── chat/                            # 对话管理
│               ├── model/entity/                # Conversation, Message
│               ├── mapper/                      # MyBatis Plus Mapper
│               └── controller/                  # ChatController
│
└── szu-rag-frontend/               # ========= React 前端 =========
    ├── nginx.conf                  # SPA fallback + API 代理 + SSE 无缓冲
    ├── src/
    │   ├── App.tsx                 # 主应用（对话/知识库路由）
    │   ├── api/
    │   │   ├── chat.ts             # 对话 API（含 SSE fetch）
    │   │   └── knowledge.ts        # 知识库 API
    │   ├── store/
    │   │   ├── chatStore.ts        # 对话状态（Zustand）
    │   │   └── knowledgeStore.ts   # 知识库状态
    │   └── components/
    │       ├── ChatWindow.tsx       # 对话窗口
    │       ├── MessageBubble.tsx    # 消息气泡（Markdown 渲染 + 来源）
    │       ├── ChatInput.tsx        # 输入框
    │       ├── KnowledgePanel.tsx   # 知识库面板
    │       └── CreateKBDialog.tsx   # 创建知识库对话框
    └── dist/                       # 构建产物
```

---

## 核心数据流

### 1. 文档处理流（上传 → 向量索引）
```
上传文档 → KnowledgeService
  → IngestionAsyncService（异步）
    → IngestionEngine
      → DocumentParserSelector → TikaDocumentParser / MarkdownDocumentParser
      → ChunkingStrategyFactory → FixedSizeChunker / StructureAwareChunker / RecursiveChunker
      → BailianEmbeddingClient.embed()（批量向量化）
      → MilvusVectorStoreService.upsert()（写入 Milvus）
    → 更新文档状态：UPLOADED → PARSING → CHUNKING → INDEXING → COMPLETED
```

### 2. RAG 问答流（提问 → 流式回答）
```
用户提问 POST /api/v1/chat/conversations/{id}/messages
  → RagChatServiceImpl.chat()
    ① 保存用户消息（Message 表）
    ② 获取会话记忆（JdbcConversationMemory，滑动窗口 max-turns=10）
    ③ SSE 发送 "thinking" 事件
    ④ MultiQueryExpander.expand() → 生成 3 个查询变体（含原始共 4 路）
    ⑤ CampusEntityExpander 实体扩展（每路查询）
    ⑥ 并行 embed + 检索（4 路 × top-10 候选 = 最多 40 个）
       → 合并去重（按 id）
    ⑦ RerankerService.rerank() → DashScope gte-rerank 重排序 → top-5
    ⑧ SSE 发送 "sources" 事件（标题/URL/相关度/摘要）
    ⑨ RagPromptService.buildPrompt() → 组装 system prompt（检索结果 + 历史记忆 + 日历 + 角色指令）
    ⑩ DeepSeekChatClient.chatStream() → 流式生成
    ⑪ SSE 逐个发送 "content" 事件
    ⑫ 生成完毕 → SSE 发送 "complete" 事件（含 tokenCount/durationMs）
    ⑬ 保存 AI 消息（含 sources JSON）
```

---

## 关键代码入口

| 功能 | 文件路径 | 关键方法/类 |
|------|---------|------------|
| RAG 对话主流程 | `rag/chat/RagChatServiceImpl.java` | `chat()` — 完整 RAG 流程编排 |
| 向量检索 | `rag/vector/MilvusVectorStoreService.java` | `search()` / `upsert()` |
| Multi-Query 扩展 | `rag/query/MultiQueryExpander.java` | LLM 生成多路查询变体 |
| Reranker 重排序 | `rag/retrieval/RerankerService.java` | DashScope gte-rerank 精排 |
| 文档处理引擎 | `ingestion/pipeline/IngestionEngine.java` | 文档处理管线 |
| 分块策略选择 | `ingestion/chunker/ChunkingStrategyFactory.java` | 根据 strategy 名创建分块器 |
| LLM 客户端 | `infra/chat/DeepSeekChatClient.java` | OpenAI 兼容 API 流式调用 |
| Embedding 客户端 | `infra/embedding/BailianEmbeddingClient.java` | DashScope text-embedding-v3 |
| SSE 推送 | `framework/sse/SseEmitterSender.java` | content/thinking/sources/complete/error |
| Prompt 组装 | `rag/prompt/RagPromptService.java` | 检索结果 + 历史记忆 + 角色指令 → system prompt |
| 会话记忆 | `rag/memory/JdbcConversationMemory.java` | 滑动窗口，从 DB 读取历史 |
| 对话 API | `chat/controller/ChatController.java` | REST 端点 |
| 知识库 API | `knowledge/controller/KnowledgeController.java` | CRUD + 文档上传 |
| 前端 SSE 消费 | `szu-rag-frontend/src/api/chat.ts` | fetch + ReadableStream |
| 前端状态管理 | `szu-rag-frontend/src/store/chatStore.ts` | Zustand store |

---

## 数据库表关系

```
t_user (用户)
  │ id, username, password, role
  │
  ├── 1:N → t_knowledge_base (知识库)
  │           │ id, name, collection_name, embedding_dim, chunk_strategy, chunk_size
  │           │
  │           └── 1:N → t_knowledge_document (文档)
  │                       │ id, title, source_url, source_type, document_status
  │                       │       document_status: UPLOADED → PARSING → CHUNKING → INDEXING → COMPLETED
  │                       │
  │                       └── 1:N → t_document_chunk (分块)
  │                                   │ id, chunk_text, char_count, milvus_id, source_title, source_url
  │                                   │ metadata: JSON（扩展元数据）
  │
  └── 1:N → t_conversation (对话)
              │ id, title, status
              │
              └── 1:N → t_message (消息)
                          │ id, role(USER/ASSISTANT), content, sources(JSON), token_count, duration_ms
```

**分块与向量的关系**：`t_document_chunk.milvus_id` 关联 Milvus 中的向量记录，`t_knowledge_base.collection_name` 对应 Milvus Collection。

---

## API 路由清单

### 后端 (:8088)

```
GET  /health                                        # 健康检查

# 知识库管理
POST /api/v1/knowledge/bases                        # 创建知识库
GET  /api/v1/knowledge/bases                        # 列出知识库
POST /api/v1/knowledge/documents/upload             # 上传文档（multipart）
GET  /api/v1/knowledge/bases/{id}/documents         # 文档列表

# 对话
POST /api/v1/chat/conversations                     # 创建对话
GET  /api/v1/chat/conversations                     # 对话列表
POST /api/v1/chat/conversations/{id}/messages       # SSE 流式问答（支持 role 参数）
GET  /api/v1/chat/conversations/{id}/messages       # 历史消息
DELETE /api/v1/chat/conversations/{id}              # 删除对话

# 校园日历
GET  /api/v1/calendar/context                       # 获取当前教学周/学期/近期事件
```

---

## 关键配置项

### .env 环境变量

| 变量 | 必填 | 说明 |
|------|------|------|
| `DEEPSEEK_API_KEY` | 是 | DeepSeek V3 API Key（OpenAI 兼容） |
| `BAILIAN_API_KEY` | 是 | 阿里百炼 DashScope API Key |
| `MYSQL_ROOT_PASSWORD` | 否 | MySQL root 密码（默认 szu_rag_2024） |

### application.yml 关键配置

```yaml
rag:
  retrieval:
    top-k: 5                    # 最终返回给 LLM 的数量
    candidate-count: 10         # 检索候选数量（送入 reranker）
    score-threshold: 0.3        # 相关度阈值（reranker 后）
  memory:
    max-turns: 10               # 会话记忆轮次
  multi-query:
    enabled: true               # Multi-Query 查询分解
    count: 3                    # 生成的查询变体数量
  reranker:
    enabled: true               # DashScope Reranker 重排序
    top-n: 5                    # reranker 返回数量

milvus:
  host: localhost
  port: 19530

ai:
  deepseek:
    api-key: ${DEEPSEEK_API_KEY}
    model: deepseek-chat
  bailian:
    api-key: ${BAILIAN_API_KEY}
    model: text-embedding-v3
    dimension: 1024
```

---

## 开发约定

### 包结构分层
```
com.szu.rag
├── framework/     # 通用框架（异常、响应、SSE、ID生成）
├── infra/         # 基础设施（外部服务客户端：LLM、Embedding）
├── ingestion/     # 文档处理管线（解析器、分块器）
├── knowledge/     # 知识库业务（entity → mapper → service → controller）
├── rag/           # RAG 核心（向量检索、Prompt、记忆、对话）
└── chat/          # 对话管理（entity → mapper → controller）
```

### 异常体系
- `BaseException` → `ClientException`（4xx）/ `ServiceException`（5xx 业务）/ `RemoteException`（外部服务）
- `GlobalExceptionHandler` 统一捕获，返回 `Result<T>` 格式

### 命名规范
- Entity：与数据库表对应，字段驼峰
- Mapper：MyBatis Plus `BaseMapper`
- Service：接口 + Impl（如 `RagChatService` / `RagChatServiceImpl`）
- Controller：RESTful 风格，统一返回 `Result<T>`
- ID：雪花算法（`SnowflakeIdWorker`），Long 类型

---

## 已知限制 & 后续规划

### 校园特化优化（已完成）
- **学术日历感知（P0）**：`t_campus_calendar` 表 + `CampusCalendarService` 自动注入当前教学周/近期事件到 Prompt
- **校园实体词典（P0）**：`CampusEntityExpander` 40+ 口语→术语映射，embedding 前自动扩展
- **Multi-Query 查询扩展（P1）**：`MultiQueryExpander` LLM 生成多路查询变体，扩大检索覆盖面
- **Reranker 精排（P1）**：`RerankerService` DashScope gte-rerank 对检索结果重排序
- **时间表达式解析（P1）**：`TimeExpressionResolver` 解析"下周三""本学期"等时间表达
- **混合检索（P2）**：`HybridRetrievalService` 语义(Milvus)+关键词(MySQL FULLTEXT) → RRF 融合
- **角色差异化（P2）**：学生（办事流程+截止提醒）/ 教职工（政策依据+审批流程）/ 访客（招生信息+校园导览），前端角色选择器

### 当前已知限制
- Sa-Token 认证框架已配置但未实现登录接口，无 RBAC
- 无用户注册/登录界面
- 单 Embedding 模型（DashScope），未支持多模型切换
- 混合检索默认关闭（需先建 FULLTEXT 索引后开启 `rag.hybrid-retrieval.enabled=true`）

### MCP 集成规划（下一阶段重点）
计划在现有 RAG 架构之上引入 MCP Server 模块，接入学校业务系统接口作为 MCP Tool：

1. **学生教务**：课表查询、成绩查询、选课提醒、学分进度
2. **图书馆**：馆藏检索、借阅续借、座位查询
3. **行政办公**：公文追踪、会议室预约、报修提交、报销查询
4. **校园生活**：校园卡余额、食堂菜单、校巴时刻

架构演进方向：**RAG 知识问答** → **RAG + MCP Tool 业务查询** → **AI Agent 自主任务编排**

---

## Docker 服务清单

| 服务 | 镜像 | 端口 | 依赖 |
|------|------|------|------|
| mysql | mysql:8.0 | 3306 | — |
| redis | redis:7-alpine | 6379 | — |
| milvus-etcd | etcd:v3.5.5 | — | — |
| milvus-minio | minio:RELEASE.2023-03-20 | — | — |
| milvus | milvusdb/milvus:v2.4.17 | 19530/9091 | etcd + minio |
| backend | 自建（Java 17） | 8088 | mysql + redis + milvus |
| frontend | 自建（Nginx） | 80 | backend |

---
