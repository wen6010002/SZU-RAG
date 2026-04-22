# SZU-RAG 系统升级文档

> 版本：v3.0 | 日期：2026-04-11 | 作者：AI 辅助生成
>
> v2.0 新增校园场景特化优化（日历感知、实体词典、混合检索、角色感知等）
> v3.0 移除爬虫模块、升级 RAG 流程（Multi-Query + Reranker）、增强角色差异化

---

## 一、升级概述

### v2.0 升级概述

将 SZU-RAG 从**通用 RAG 问答系统**升级为**校园场景特化 RAG 系统**，包含 6 大优化模块（P0-P2）。

### v3.0 升级概述

1. **移除爬虫模块**：因目标站点反爬机制过于先进，爬虫效果不佳。移除 `szu-rag-crawler/` 整个目录及后端所有相关代码（`sourceSite`、站点映射、CRAWLER 类型等）。
2. **升级 RAG 流程**：引入 Multi-Query 查询分解 + DashScope gte-rerank 重排序，替换原有的单路 QueryRewriter。
3. **增强角色差异化**：学生/教职工/访客三种角色 Prompt 从 ~5 行增强为 ~10 行详细指令，真正实现差异化回答。

### v3.0 升级前后对比

| 维度 | v2.0 | v3.0 |
|------|------|------|
| 数据采集 | Scrapy 爬虫（5 站点） | 手动批量上传文档 |
| 查询扩展 | 单路 QueryRewriter | Multi-Query 生成 3 个变体 + 并行检索 |
| 检索精排 | 无 | DashScope gte-rerank 重排序 top-5 |
| 检索候选 | top-5 直接使用 | top-10 候选 → Reranker → top-5 |
| 角色差异 | 5 行 Prompt 指令 | 10+ 行详细指令（侧重点、格式、风格完全不同） |
| 服务数量 | 9 个（含 crawler） | 8 个 |

---

## 二、升级模块详细说明

### 模块 1：学术日历感知（P0）

**问题**：系统不知道"现在是第几周"，无法回答"这学期什么时候期末考试"等时间相关问题。

**解决方案**：新增校园日历表和服务，自动计算当前教学周，注入近期事件到 Prompt。

#### 2.1.1 数据库变更

新增 `t_campus_calendar` 表：

```sql
CREATE TABLE t_campus_calendar (
    id              BIGINT PRIMARY KEY,
    academic_year   VARCHAR(20)   NOT NULL,     -- 学年：2025-2026
    semester        VARCHAR(20)   NOT NULL,     -- 学期：第一学期/第二学期/暑期
    start_date      DATE          NOT NULL,     -- 学期开始日期
    end_date        DATE          NOT NULL,     -- 学期结束日期
    week_count      INT           NOT NULL,     -- 总教学周数
    event_name      VARCHAR(100)  NOT NULL,     -- 事件名称
    event_type      VARCHAR(50)   NOT NULL,     -- 类型：exam/enrollment/holiday...
    event_start     DATE          NOT NULL,
    event_end       DATE          NULL,
    description     VARCHAR(500)  NULL,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_event_dates (event_start, event_end)
);
```

预置 2025-2026、2026-2027 两个学年共 20 条日历数据。

#### 2.1.2 新增后端文件

| 文件 | 说明 |
|------|------|
| `rag/calendar/model/entity/CampusCalendar.java` | 日历实体，`@TableName("t_campus_calendar")` |
| `rag/calendar/mapper/CampusCalendarMapper.java` | MyBatis Plus Mapper，含 `findActiveSemester()`、`findUpcomingEvents()` 自定义查询 |
| `rag/calendar/CampusCalendarService.java` | 核心服务：`getCurrentContext()` 返回格式化日历字符串供 Prompt 使用，`getCalendarView()` 供前端 API |
| `rag/calendar/controller/CalendarController.java` | `GET /api/v1/calendar/context` API 端点 |

#### 2.1.3 修改的文件

- **`resources/prompt/kb-qa.st`**：新增 `## 当前校园时间` 区块，注入 `<calendar_context>` 变量
- **`rag/prompt/RagPromptService.java`**：注入 `CampusCalendarService`，在 `buildPrompt()` 中加入 `calendar_context` 参数

#### 2.1.4 效果示例

Prompt 中自动注入：
```
## 当前校园时间
当前是2025-2026学年第二学期第7周（2026-04-10），本学期起止：2026-02-23 ~ 2026-07-03
近期重要节点：
- 期中考试周（2026-04-20 ~ 2026-05-01）
```

---

### 模块 2：校园实体词典（P0）

**问题**："荔园"→ 深大、"四六级"→ CET 等口语表达，embedding 模型无法正确理解。

**解决方案**：在 embedding 前自动将口语表达替换为标准术语。

#### 2.2.1 新增文件

| 文件 | 说明 |
|------|------|
| `rag/query/CampusEntityExpander.java` | 40+ 条映射规则，使用 `LinkedHashMap` 保证长词优先替换 |

#### 2.2.2 映射规则分类

| 类别 | 示例 |
|------|------|
| 校区别名 | 荔园 → 深圳大学, 丽湖校区 → 深圳大学丽湖校区 |
| 考试别名 | 四六级 → 全国大学英语四六级考试 CET, 考研 → 全国硕士研究生统一招生考试 |
| 教务术语 | 选课 → 课程选修注册, 绩点 → 学业成绩平均绩点 GPA |
| 校园生活 | 饭堂 → 食堂, 宿管 → 学生宿舍管理中心 |
| 办事流程 | 请假 → 学生请假审批, 综测 → 综合素质测评 |
| 部门别名 | 教务 → 教务部, 学工 → 学生部 |

#### 2.2.3 修改的文件

- **`rag/chat/RagChatServiceImpl.java`**：在 `chat()` 方法中，`embed()` 前调用 `campusEntityExpander.expand(query)`

**关键设计**：扩展后的查询仅用于 embedding/检索，原始 `question` 仍用于最终 Prompt 生成，避免用户看到改写后的文字。

---

### 模块 3：Multi-Query 查询扩展 + Reranker 重排序（v3.0 升级）

**问题**：v2.0 的单路 QueryRewriter 只生成一个改写查询，检索覆盖面有限。

**解决方案**：
1. **Multi-Query**：用 LLM 将用户查询分解为 3 个不同角度的子查询，并行检索后合并去重，显著提升召回率。
2. **Reranker**：检索返回的 top-10 候选送入 DashScope gte-rerank 模型重排序，取 top-5 高质量结果。

#### 2.3.1 新增文件

| 文件 | 说明 |
|------|------|
| `rag/query/MultiQueryExpander.java` | Multi-Query 查询扩展服务，LLM 生成 3 个查询变体 |
| `rag/retrieval/RerankerService.java` | DashScope gte-rerank 重排序服务 |

#### 2.3.2 移除文件

| 文件 | 说明 |
|------|------|
| `rag/query/QueryRewriter.java` | 被 MultiQueryExpander 替代 |
| `resources/prompt/query-rewrite.st` | 不再需要 |

#### 2.3.3 配置项

```yaml
rag:
  retrieval:
    top-k: 5              # 最终返回给 LLM 的数量
    candidate-count: 10   # 检索候选数量（送入 reranker）
    score-threshold: 0.3  # 相关度阈值（降低，因为 reranker 分数分布不同）
  multi-query:
    enabled: true         # Multi-Query 查询分解
    count: 3              # 生成的查询变体数量
  reranker:
    enabled: true         # DashScope Reranker 重排序
    top-n: 5              # reranker 返回数量
```

#### 2.3.4 修改的文件

- **`rag/chat/RagChatServiceImpl.java`**：chat 流程改用 Multi-Query + Reranker，移除 QueryRewriter
- **`application.yml` / `application-prod.yml`**：替换 query-rewriting 配置为 multi-query + reranker

#### 2.3.5 效果示例

```
原始查询: "四六级怎么报名？"
     ↓ TimeExpressionResolver（无时间表达式，跳过）
     ↓ MultiQueryExpander (LLM)
  变体1: "深圳大学 英语四六级考试 报名流程 条件"
  变体2: "CET 报名截止时间 材料 费用"
  变体3: "全国大学英语四六级 深大 报名入口"
     ↓ CampusEntityExpander（每路查询）
     ↓ 并行 embed + 检索（4路 × top-10 = 最多 40 个候选）
     ↓ 合并去重（按 id）
     ↓ RerankerService（gte-rerank 重排序 → top-5）
     ↓ 送入 Prompt
```

---

### 模块 4：校园文档特化分块 + 元数据增强（P1）

**问题**：短通知被截断、长政策文档分块不合理；检索结果缺少部门/分类等筛选维度。

> **v3.0 变更**：移除了 `CampusNoticeChunker`（爬虫专用）和 `source_site` 字段，统一使用 RecursiveChunker。

#### 2.4.1 数据库变更

**`t_knowledge_document` 表新增 6 列（v3.0 移除了 `source_site`）：**

| 列名 | 类型 | 说明 |
|------|------|------|
| `source_department` | VARCHAR(128) | 来源部门：教务部/学生部/后勤保障部 |
| `document_type` | VARCHAR(32) | 文档类型：通知公告/政策文件/办事指南 |
| `category` | VARCHAR(64) | 分类：教务/学工/后勤/综合/行政 |
| `academic_year` | VARCHAR(20) | 学年 |
| `semester` | VARCHAR(20) | 学期 |
| `target_audience` | VARCHAR(32) | 目标受众：undergraduate/postgraduate/staff/all |

**`t_document_chunk` 表新增 3 列（v3.0 移除了 `source_site`）：**

| 列名 | 类型 | 说明 |
|------|------|------|
| `source_department` | VARCHAR(128) | 来源部门 |
| `document_type` | VARCHAR(32) | 文档类型 |
| `category` | VARCHAR(64) | 分类 |

**新增全文索引：**

```sql
ALTER TABLE t_document_chunk ADD FULLTEXT INDEX ft_chunk_text (chunk_text) WITH PARSER ngram;
```

#### 2.4.2 Milvus Schema 扩展

每个 Collection 新增 4 个标量字段（v3.0 移除了 `source_site`）：

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `source_department` | VarChar(256) | 来源部门 |
| `document_type` | VarChar(64) | 文档类型 |
| `publish_date` | VarChar(32) | 发布日期 |
| `category` | VarChar(128) | 分类 |

支持 Milvus filter expression 过滤检索，如：`category == "教务"`

#### 2.4.3 新增文件（v2.0）

| 文件 | 说明 |
|------|------|
| `ingestion/chunker/CampusNoticeChunker.java` | 校园通知专用分块器 |

> **v3.0**：此文件已移除（爬虫专用）。现在统一使用 `RecursiveChunker`。

#### 2.4.4 修改的文件

| 文件 | 改动 |
|------|------|
| `ingestion/pipeline/IngestionEngine.java` | v3.0：移除 CAMPUS_NOTICE 检测，统一使用 RECURSIVE；移除 sourceSite |
| `rag/vector/MilvusVectorStoreService.java` | v3.0：移除 source_site 字段 |
| `rag/vector/VectorStoreService.java` | 新增带 `Map<String, String> filters` 的 search 方法签名 |
| `knowledge/model/entity/DocumentChunk.java` | v3.0：移除 sourceSite 字段 |
| `knowledge/model/entity/KnowledgeDocument.java` | v3.0：移除 sourceSite 字段 |
| `knowledge/service/KnowledgeService.java` | v3.0：移除 sourceSite 参数和站点映射方法 |
| `knowledge/controller/KnowledgeController.java` | v3.0：移除 sourceSite 参数 |

---

### 模块 5：混合检索 + RRF 融合（P2）

**问题**：纯语义检索无法精确匹配"四六级报名"等关键词，可能漏掉包含精确关键词但不语义相近的文档。

#### 2.5.1 新增文件

| 文件 | 说明 |
|------|------|
| `rag/retrieval/HybridRetrievalService.java` | 混合检索服务 |

#### 2.5.2 检索流程

```
用户查询
  ├─ 语义检索路径：embed → Milvus search → top-K 结果（按 cosine score 排序）
  ├─ 关键词检索路径：MySQL FULLTEXT MATCH(chunk_text) AGAINST(query) → top-K 结果
  └─ RRF 融合：对两路结果用 Reciprocal Rank Fusion 合并
      score_rrf = Σ 1/(k + rank_i)，k=60
```

#### 2.5.3 修改的文件

| 文件 | 改动 |
|------|------|
| `knowledge/mapper/DocumentChunkMapper.java` | 新增 `fullTextSearch()` XML 映射方法 |
| `rag/chat/RagChatServiceImpl.java` | 检索调用切换为 `HybridRetrievalService` |

#### 2.5.4 配置项

```yaml
rag:
  hybrid-retrieval:
    enabled: false     # 需先建立 FULLTEXT 索引后开启
```

> **注意**：FULLTEXT 索引已创建，但混合检索默认关闭。开启前需确认 ngram 索引生效（`SET GLOBAL innodb_ft_cache_size = 33554432;`）。

---

### 模块 6：角色差异化 Prompt + 前端增强（P2，v3.0 增强）

**问题**：v2.0 三种角色的 Prompt 指令差异太小（各约 5 行），实际回答看不出区别。

**v3.0 解决方案**：大幅增强 Prompt 指令，让不同角色的回答风格、内容侧重、表达方式真正不同。

#### 2.6.1 后端改动

**修改文件：**

| 文件 | 改动 |
|------|------|
| `chat/controller/ChatController.java` | `SendMessageRequest` 新增 `role` 字段（默认 "student"） |
| `rag/chat/RagChatService.java` | 新增 `chat(convId, question, role)` 重载方法 |
| `rag/chat/RagChatServiceImpl.java` | `chat()` 方法传递 role 到 `buildPrompt()` |
| `rag/prompt/RagPromptService.java` | v3.0：增强 `getRoleInstruction()` 为每角色 10+ 行详细指令 |
| `resources/prompt/kb-qa.st` | 新增 `<role_instruction>` 变量 |

**角色指令（v3.0 增强）：**

| 角色 | 指令风格 |
|------|----------|
| student | **学长/学姐助手**，亲切口吻。侧重：办事流程（步骤化清单）、截止日期（⚠️醒目标注）、所需材料、费用金额、常见坑点提醒 |
| teacher | **教职工办公助手**，正式书面语。侧重：政策依据（标注文号和发布日期）、审批流程、跨部门协调指引、人事/财务/科研管理 |
| visitor | **招生咨询和校园导览助手**，简洁易懂。侧重：招生政策、校园介绍、入学流程。所有校内术语必须附带解释 |

#### 2.6.2 前端新增文件

| 文件 | 说明 |
|------|------|
| `components/CampusCalendarWidget.tsx` | 顶部日历周次徽章 + 近期事件彩色标签 |
| `components/RoleSelector.tsx` | 分段控制器：学生/教职工/访客 |
| `components/MessageBubble.tsx`（重写） | 富来源卡片：部门徽章（蓝色）、分类徽章（彩色）、日期（灰色）、相关度 |

#### 2.6.3 前端修改文件

| 文件 | 改动 |
|------|------|
| `api/chat.ts` | Source 接口新增元数据字段；`sendMessageStream()` 新增 role 参数；新增 `getCalendarContext()` API |
| `store/chatStore.ts` | 新增 `userRole` 状态和 `setUserRole` action |
| `components/ChatWindow.tsx` | 传递 role 参数到 API |
| `App.tsx` | 挂载日历组件、角色选择器；动态快捷提问根据日历事件生成 |

---

## 三、v3.0 变更：爬虫移除

### 删除的文件/目录

| 路径 | 说明 |
|------|------|
| `szu-rag-crawler/` | 整个爬虫模块（Scrapy 爬虫 + FastAPI 管理 + 站点配置） |
| `ingestion/chunker/CampusNoticeChunker.java` | 爬虫专用分块器 |
| `rag/query/QueryRewriter.java` | 被 MultiQueryExpander 替代 |
| `resources/prompt/query-rewrite.st` | 不再需要 |

### 后端修改

| 文件 | 改动 |
|------|------|
| `knowledge/controller/KnowledgeController.java` | 移除 `sourceSite` 参数 |
| `knowledge/service/KnowledgeService.java` | 移除 sourceSite 参数、mapSiteToDepartment()、mapSiteToCategory()；sourceType 统一为 MANUAL |
| `ingestion/pipeline/IngestionEngine.java` | 移除 CAMPUS_NOTICE 检测，统一返回 RECURSIVE；移除 sourceSite 元数据 |
| `rag/chat/RagChatServiceImpl.java` | 集成 Multi-Query + Reranker，移除 QueryRewriter 和 sourceSite |
| `rag/retrieval/HybridRetrievalService.java` | 移除 source_site 字段 |
| `rag/vector/MilvusVectorStoreService.java` | 移除 source_site 字段 |
| `knowledge/model/entity/DocumentChunk.java` | 移除 sourceSite 字段 |
| `knowledge/model/entity/KnowledgeDocument.java` | 移除 sourceSite 字段 |

### 前端修改

| 文件 | 改动 |
|------|------|
| `src/api/chat.ts` | Source 接口移除 sourceSite 字段 |
| `src/components/RoleSelector.tsx` | 增强角色标签（在校学习·办事流程 / 教职工·政策依据 / 访客/考生·招生信息） |

### 配置/部署修改

| 文件 | 改动 |
|------|------|
| `docker-compose.yml` | 移除 crawler 服务和 crawler-data volume |
| `application.yml` / `application-prod.yml` | 替换 query-rewriting 为 multi-query + reranker 配置 |

---

## 四、数据库变更汇总（v2.0）

### 新增表

| 表名 | 行数 | 说明 |
|------|------|------|
| `t_campus_calendar` | 20 | 校园日历（2025-2026、2026-2027 学年） |

### 扩展列

**`t_knowledge_document`（v3.0 后为 +6 列，移除了 `source_site`）：**
`source_department`, `document_type`, `category`, `academic_year`, `semester`, `target_audience`

**`t_document_chunk`（v3.0 后为 +3 列，移除了 `source_site`）：**
`source_department`, `document_type`, `category`

### 新增索引

| 表 | 索引 | 类型 |
|----|------|------|
| `t_document_chunk` | `ft_chunk_text` | FULLTEXT (ngram) |
| `t_campus_calendar` | `idx_event_dates` | NORMAL |
| `t_campus_calendar` | `idx_event_type` | NORMAL |

---

## 五、新增 API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/calendar/context` | 获取当前教学周、学期、近期事件 |

### 已有 API 变更

| 端点 | 变更 |
|------|------|
| `POST /api/v1/chat/conversations/{id}/messages` | 请求体新增 `role` 字段（可选，默认 "student"） |
| `POST /api/v1/knowledge/documents/upload` | v3.0 移除 `sourceSite` 参数 |

---

## 六、新增配置项

```yaml
rag:
  retrieval:
    top-k: 5              # 最终返回给 LLM 的数量
    candidate-count: 10   # 检索候选数量（送入 reranker）
    score-threshold: 0.3  # 相关度阈值（reranker 后）
  multi-query:
    enabled: true         # Multi-Query 查询分解
    count: 3              # 生成的查询变体数量
  reranker:
    enabled: true         # DashScope Reranker 重排序
    top-n: 5              # reranker 返回数量
  hybrid-retrieval:
    enabled: false        # 混合检索开关（需 FULLTEXT 索引就绪）
```

---

## 七、Milvus 变更

### Schema 扩展

每个 Collection 新增 4 个标量字段（v3.0 移除了 `source_site`，与数据库元数据对应）：

```
source_department  VarChar(256)  -- 来源部门
document_type      VarChar(64)   -- 文档类型
publish_date       VarChar(32)   -- 发布日期
category           VarChar(128)  -- 分类
```

### 兼容性说明

旧 Collection 不含新字段，需要**删除并重建**。已有向量数据需要重新上传来重新索引。

---

## 八、文件变更清单

### v3.0 删除文件

```
szu-rag-crawler/                                -- 整个爬虫模块
src/main/java/com/szu/rag/
├── ingestion/chunker/CampusNoticeChunker.java  -- 爬虫专用分块器
├── rag/query/QueryRewriter.java                -- 被 MultiQueryExpander 替代
src/main/resources/
└── prompt/query-rewrite.st                     -- 不再需要
```

### v3.0 新增文件

```
src/main/java/com/szu/rag/
├── rag/query/MultiQueryExpander.java           -- Multi-Query 查询扩展
├── rag/retrieval/RerankerService.java          -- DashScope gte-rerank 重排序
```

### 后端新增（v2.0，12 文件）

```
src/main/java/com/szu/rag/
├── rag/calendar/
│   ├── model/entity/CampusCalendar.java
│   ├── mapper/CampusCalendarMapper.java
│   ├── CampusCalendarService.java
│   └── controller/CalendarController.java
├── rag/query/
│   ├── CampusEntityExpander.java
│   └── TimeExpressionResolver.java
├── rag/retrieval/
│   └── HybridRetrievalService.java
src/main/resources/
└── db/seed_calendar.sql
```

### 后端修改（v2.0 + v3.0 合计）

```
src/main/java/com/szu/rag/
├── rag/chat/RagChatServiceImpl.java          -- v3.0：集成 Multi-Query + Reranker
├── rag/chat/RagChatService.java              -- 新增 role 参数重载
├── rag/prompt/RagPromptService.java          -- v3.0：增强角色差异化指令
├── rag/vector/MilvusVectorStoreService.java  -- v3.0：移除 source_site
├── rag/vector/VectorStoreService.java        -- 新增带 filter 的 search 签名
├── rag/retrieval/HybridRetrievalService.java -- v3.0：移除 source_site
├── knowledge/model/entity/DocumentChunk.java -- v3.0：移除 sourceSite
├── knowledge/model/entity/KnowledgeDocument.java -- v3.0：移除 sourceSite
├── knowledge/mapper/DocumentChunkMapper.java -- FULLTEXT 搜索方法
├── ingestion/pipeline/IngestionEngine.java   -- v3.0：移除 CAMPUS_NOTICE
├── chat/controller/ChatController.java       -- role 参数
├── knowledge/controller/KnowledgeController.java -- v3.0：移除 sourceSite 参数
├── knowledge/service/KnowledgeService.java   -- v3.0：移除 sourceSite/站点映射
src/main/resources/
├── prompt/kb-qa.st                           -- 日历上下文 + 角色指令
├── db/schema.sql                             -- 新表 + 新列 + FULLTEXT 索引
├── application.yml                           -- v3.0：multi-query + reranker 配置
└── application-prod.yml                      -- 同上
```

### 前端修改（v2.0 + v3.0 合计）

```
szu-rag-frontend/src/
├── api/chat.ts              -- v3.0：移除 sourceSite；v2.0：+元数据、role参数、日历API
├── store/chatStore.ts       -- userRole 状态
├── components/ChatWindow.tsx -- 传递 role
├── components/RoleSelector.tsx -- v3.0：增强角色标签描述
└── App.tsx                  -- 日历组件、角色选择器、动态建议
```

---

## 九、RAG 对话流程（v3.0 升级后）

```
用户提问（含 role 参数）
  │
  ├── ① 保存用户消息（t_message）
  ├── ② 获取会话记忆（JdbcConversationMemory，滑动窗口 10 轮）
  ├── ③ SSE 发送 "thinking" 事件
  ├── ④ TimeExpressionResolver（"这学期" → "2025-2026学年第二学期"）
  ├── ⑤ MultiQueryExpander（LLM 生成 3 个查询变体，含原始共 4 路）
  ├── ⑥ CampusEntityExpander（"四六级" → "全国大学英语四六级考试 CET"，每路查询）
  ├── ⑦ 并行 embed + 检索（4 路 × top-10 = 最多 40 个候选）
  │     └── 合并去重（按 id）
  ├── ⑧ RerankerService（DashScope gte-rerank 重排序 → top-5）
  ├── ⑨ SSE 发送 "sources" 事件（含部门/分类/日期/相关度）
  ├── ⑩ RagPromptService.buildPrompt(原始问题, top-5结果, 历史, 日历上下文, 角色)
  ├── ⑪ DeepSeekChatClient.chatStream() → 流式生成
  ├── ⑫ SSE 逐个发送 "content" 事件
  ├── ⑬ SSE 发送 "complete" 事件（含 tokenCount/durationMs）
  └── ⑭ 保存 AI 消息（含 sources JSON）
```

---

## 十、部署注意事项

### 1. 数据库迁移

```bash
# 在 MySQL 中执行 schema.sql 中新增的部分
# 新增表
CREATE TABLE t_campus_calendar (...)

# 扩展列
ALTER TABLE t_knowledge_document ADD COLUMN source_department VARCHAR(128) DEFAULT '' ...
ALTER TABLE t_document_chunk ADD COLUMN source_department VARCHAR(128) DEFAULT '' ...

# 全文索引
ALTER TABLE t_document_chunk ADD FULLTEXT INDEX ft_chunk_text (chunk_text) WITH PARSER ngram;

# 种子数据（必须使用 --default-character-set=utf8mb4 避免乱码）
mysql --default-character-set=utf8mb4 szu_rag < seed_calendar.sql
```

### 2. Milvus Collection 重建

旧 Collection 不含新元数据字段，需要删除重建：

```bash
# 通过 Milvus REST API 删除旧 Collection
curl -X POST http://localhost:19530/v2/vectordb/collections/drop \
  -H "Content-Type: application/json" \
  -d '{"collectionName": "szu_rag_kb_XXXXX"}'
```

重建后需重新上传文档来填充向量数据。

### 3. 编码问题修复

JDBC URL 需添加 `characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://mysql:3306/szu_rag?...&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci
```

### 4. Docker 重建

v3.0 后服务从 9 个减少为 8 个（移除 crawler）：

```bash
docker compose up -d --build backend frontend
```

---

## 十一、验证清单

| # | 测试项 | 验证方式 | 预期结果 |
|---|--------|----------|----------|
| 1 | 日历 API | `GET /api/v1/calendar/context` | 返回正确学期、教学周、近期事件（中文无乱码） |
| 2 | Multi-Query | 问"怎么请假" | 后端日志显示生成 3 个查询变体，检索候选数 > 5 |
| 3 | Reranker | 问"四六级报名" | 后端日志显示 rerank 得分，回答引用更精准 |
| 4 | 实体扩展 | 问"去荔园图书馆" | embedding 查询被扩展为"深圳大学 图书馆" |
| 5 | 角色差异化 | 同一问题分别用学生/教职工/访客角色 | 回答风格明显不同 |
| 6 | 元数据展示 | 聊天后查看来源 | 来源卡片显示部门、分类、相关度 |
| 7 | 日历组件 | 前端顶部栏 | 显示"第X周"徽章 + 近期事件标签 |
| 8 | 角色选择器 | 前端顶部栏 | 可切换在校学生/教职工/访客/考生 |
| 9 | 文档上传 | 上传 PDF/Word | 处理状态正常流转，使用 RECURSIVE 分块策略 |
| 10 | 爬虫移除 | `docker compose ps` | 无 crawler 服务，仅 8 个服务运行 |
