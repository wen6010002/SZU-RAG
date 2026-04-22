# SZU-RAG 校园场景垂直优化方案

> 本文档规划了针对校园场景的 RAG 特化优化策略，目标是让系统从"通用问答"升级为"真正懂校园"的垂直产品。

---

## 一、学术日历感知（P0 - 最高优先级）

### 背景

普通 RAG 不知道"现在是第几周"，但校园场景中 **80% 的查询都跟时间强相关**。

用户问"选课什么时候截止"——系统需要知道现在是第几周，才能返回本学期的选课通知而非去年的。

### 涉及改动

- 数据库新增 `t_campus_calendar` 表
- 新增 `CampusCalendarService`
- 修改 `rag/prompt/RagPromptService.java`

### 数据库设计

```sql
CREATE TABLE t_campus_calendar (
    id          BIGINT PRIMARY KEY COMMENT '雪花ID',
    academic_year VARCHAR(20)   NOT NULL COMMENT '学年，如 2025-2026',
    semester    VARCHAR(20)    NOT NULL COMMENT '学期：第一学期/第二学期/暑期',
    start_date  DATE           NOT NULL COMMENT '学期开始日期',
    end_date    DATE           NOT NULL COMMENT '学期结束日期',
    week_count  INT            NOT NULL COMMENT '总教学周数',
    event_name  VARCHAR(100)   NOT NULL COMMENT '事件名称',
    event_type  VARCHAR(50)    NOT NULL COMMENT '事件类型：teaching/exam/enrollment/holiday/registration',
    event_start DATE           NOT NULL COMMENT '事件开始日期',
    event_end   DATE           NULL     COMMENT '事件结束日期',
    description VARCHAR(500)   NULL     COMMENT '补充说明',
    created_at  DATETIME       DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 示例数据

```sql
INSERT INTO t_campus_calendar (id, academic_year, semester, start_date, end_date, week_count, event_name, event_type, event_start, event_end, description)
VALUES
(1, '2025-2026', '第二学期', '2026-02-23', '2026-07-03', 18, '第一轮选课', 'enrollment', '2026-02-20', '2026-02-25', '本科生第二学期第一轮选课'),
(2, '2025-2026', '第二学期', '2026-02-23', '2026-07-03', 18, '期中考试周', 'exam', '2026-04-14', '2026-04-25', '第9-10周期中考试'),
(3, '2025-2026', '第二学期', '2026-02-23', '2026-07-03', 18, '期末考试周', 'exam', '2026-06-15', '2026-06-26', '第17-18周期末考试'),
(4, '2025-2026', '第二学期', '2026-02-23', '2026-07-03', 18, '清明节放假', 'holiday', '2026-04-04', '2026-04-06', NULL),
(5, '2025-2026', '第二学期', '2026-02-23', '2026-07-03', 18, '暑假开始', 'holiday', '2026-07-04', NULL, NULL);
```

### CalendarService 核心逻辑

```java
// 新建 rag/calendar/CampusCalendarService.java
@Service
public class CampusCalendarService {

    @Autowired
    private CampusCalendarMapper calendarMapper;

    /**
     * 获取当前校园日历上下文，用于注入 Prompt
     */
    public String getCurrentContext() {
        LocalDate today = LocalDate.now();

        // 查询当前日期所属的学期
        CampusCalendar currentSemester = calendarMapper.findActiveSemester(today);
        if (currentSemester == null) {
            return "当前不在任何学期内";
        }

        // 计算当前教学周
        long weekNum = ChronoUnit.WEEKS.between(currentSemester.getStartDate(), today) + 1;

        // 查询近期（前后14天）重要事件
        List<CampusCalendar> upcomingEvents = calendarMapper.findUpcomingEvents(
            today, today.plusDays(14)
        );

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("当前是%s学年%s学期第%d周（%s）",
            currentSemester.getAcademicYear(),
            currentSemester.getSemester(),
            weekNum,
            today));
        sb.append(String.format("，本学期起止：%s ~ %s",
            currentSemester.getStartDate(),
            currentSemester.getEndDate()));

        if (!upcomingEvents.isEmpty()) {
            sb.append("，近期重要节点：");
            for (CampusCalendar event : upcomingEvents) {
                sb.append(String.format("\n- %s（%s ~ %s）",
                    event.getEventName(),
                    event.getEventStart(),
                    event.getEventEnd() != null ? event.getEventEnd() : event.getEventStart()));
            }
        }

        return sb.toString();
    }
}
```

### Prompt 注入

修改 `rag/prompt/RagPromptService.java`：

```java
public String buildPrompt(String question, List<RetrievalResult> results) {
    String calendarContext = campusCalendarService.getCurrentContext();

    return """
        你是深大智答，深圳大学校园智能问答助手。

        ## 当前校园时间上下文
        %s

        ## 检索到的参考内容
        %s

        ## 回答要求
        1. 基于检索到的参考内容回答，不要编造信息
        2. 如果涉及截止日期，明确写出具体日期
        3. 如果涉及办事流程，给出步骤和负责部门
        4. 优先引用最新来源，注意区分不同学年的政策

        用户问题：%s
        """.formatted(calendarContext, formatResults(results), question);
}
```

### 查询预处理：时间词替换

用户说"这学期"/"下学期"/"开学初" → 自动转换为具体日期范围：

```java
public class TimeExpressionResolver {

    public String resolve(String query) {
        CampusCalendar current = calendarService.getCurrentSemester();
        CampusCalendar next = calendarService.getNextSemester();

        query = query.replace("这学期", current.getAcademicYear() + current.getSemester());
        query = query.replace("下学期", next.getAcademicYear() + next.getSemester());
        query = query.replace("开学初", current.getStartDate().toString());
        query = query.replace("期末", "期末考试周");

        return query;
    }
}
```

---

## 二、校园实体词典（P0 - 投入极小，效果显著）

### 背景

深大学生有大量口语化、非正式的表达，与官方文档中的标准术语存在鸿沟。如果直接对口语化查询做 embedding，召回效果会很差。

### 涉及改动

- 新增 `rag/query/CampusEntityExpander.java`

### 实体映射表

```java
@Component
public class CampusEntityExpander {

    private static final Map<String, String> ENTITY_MAP = Map.ofEntries(
        // 校区别名
        entry("荔园",     "深圳大学"),
        entry("荔天",     "深圳大学丽湖校区"),
        entry("粤海",     "深圳大学粤海校区"),
        entry("沧海",     "深圳大学沧海校区"),
        entry("后海",     "深圳大学粤海校区"),
        entry("罗湖",     "深圳大学罗湖校区"),

        // 考试别名
        entry("四六级",   "全国大学英语四六级考试 CET"),
        entry("六级",     "大学英语六级考试 CET-6"),
        entry("四级",     "大学英语四级考试 CET-4"),
        entry("雅思",     "IELTS 雅思考试"),
        entry("托福",     "TOEFL 托福考试"),
        entry("考研",     "全国硕士研究生招生考试"),
        entry("国考",     "国家公务员考试"),
        entry("教资",     "中小学教师资格考试"),

        // 教务术语
        entry("选课",     "本科生选课"),
        entry("退课",     "本科生退课 补退选"),
        entry("教务",     "教务部"),
        entry("挂科",     "课程不及格 补考 重修"),
        entry("综测",     "综合测评"),
        entry("学分",     "学分认定 学分转换"),
        entry("绩点",     "GPA 绩点计算"),
        entry("保研",     "推荐免试攻读硕士研究生"),
        entry("毕业论文", "本科毕业论文 学位论文"),
        entry("答辩",     "毕业论文答辩"),

        // 校园生活
        entry("宿舍报修", "学生宿舍维修报修"),
        entry("校园卡",   "校园卡 读者证 一卡通"),
        entry("食堂",     "餐厅 食堂 荔山餐厅 听荔餐厅"),
        entry("校巴",     "校园巴士 校内接驳车"),
        entry("图书馆",   "图书馆 馆藏 借阅"),
        entry("体育馆",   "体育场馆 运动场"),

        // 办事流程
        entry("请假",     "学生请假 销假"),
        entry("奖学金",   "奖学金评定 国家奖学金 校级奖学金"),
        entry("助学金",   "助学金 国家助学金"),
        entry("贷款",     "助学贷款 生源地信用助学贷款"),
        entry("医保",     "学生医疗保险 大学生医保"),

        // 部门别名
        entry("学工",     "学生部"),
        entry("后勤",     "后勤保障部"),
        entry("信息中心", "信息中心 信息化建设处"),
        entry("财务",     "财务部"),
        entry("招生办",   "招生办公室"),
        entry("就业",     "就业指导中心")
    );

    /**
     * 将口语化表达替换为标准术语
     */
    public String expand(String query) {
        String expanded = query;
        for (Map.Entry<String, String> entry : ENTITY_MAP.entrySet()) {
            if (expanded.contains(entry.getKey())) {
                expanded = expanded.replace(entry.getKey(), entry.getValue());
            }
        }
        return expanded;
    }
}
```

### 集成到 RAG 流程

在 `rag/chat/RagChatServiceImpl.java` 的 `chat()` 方法中，embedding 之前调用：

```java
// 在 embed(question) 之前插入
String expandedQuery = campusEntityExpander.expand(question);
float[] queryVector = embeddingClient.embed(expandedQuery);
```

---

## 三、查询意图识别 + Query Rewriting（P1）

### 背景

校园用户的提问模式高度可预测，可以通过 LLM 将口语化查询改写为更适合检索的标准化查询。

### 用户查询 → 改写示例

| 用户原始查询 | 意图分类 | 改写后查询 |
|-------------|---------|-----------|
| 怎么请假 | 办事流程 | 深圳大学本科生请假流程 所需材料 审批流程 |
| 奖学金 | 政策查询 | 深圳大学本科生奖学金评定办法 申请条件 金额 标准 |
| 宿舍报修 | 办事入口 | 深圳大学宿舍报修流程 报修电话 线上报修入口 |
| 这学期什么时候放暑假 | 日程查询 | 2025-2026学年第二学期暑假放假时间 |
| 选课 | 办事指南 | 本科生选课时间 选课规则 选课系统操作指南 |
| 转专业 | 政策查询 | 深圳大学本科生转专业办法 申请条件 考核方式 |

### 涉及改动

- 新增 `rag/query/QueryRewriter.java`
- 修改 `rag/chat/RagChatServiceImpl.java`

### QueryRewriter 实现

```java
@Service
public class QueryRewriter {

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private CampusCalendarService calendarService;

    private static final String REWRITE_PROMPT = """
        你是一个校园查询优化器。根据当前校园时间上下文，将用户的口语化查询改写为更适合知识库检索的查询。

        规则：
        1. 保留原始问题的核心意图
        2. 补充可能缺失的实体信息（学校名、部门名等）
        3. 将时间表达转换为具体日期（如"这学期"→"2025-2026学年第二学期"）
        4. 扩展同义词（如"挂科"→"补考 重修"）
        5. 只输出改写后的查询，不要解释

        当前校园时间上下文：{calendar}

        用户原始查询：{query}

        改写后的查询：
        """;

    /**
     * 改写查询，用于检索（不用于最终生成）
     */
    public String rewrite(String originalQuery) {
        String calendarContext = calendarService.getCurrentContext();
        String prompt = REWRITE_PROMPT
            .replace("{calendar}", calendarContext)
            .replace("{query}", originalQuery);

        return chatClient.chat(prompt);  // 非流式调用
    }
}
```

### 集成到 RAG 流程

在 `RagChatServiceImpl.chat()` 中：

```java
public SseEmitter chat(Long conversationId, String question) {
    // ... 前置逻辑 ...

    // ① 查询改写（用于检索，不影响最终回答）
    String rewrittenQuery = queryRewriter.rewrite(question);
    log.info("查询改写: {} → {}", question, rewrittenQuery);

    // ② 用改写后的查询做 embedding 和检索
    float[] queryVector = embeddingClient.embed(
        campusEntityExpander.expand(rewrittenQuery)
    );
    List<RetrievalResult> results = vectorStore.search(queryVector, topK);

    // ③ 最终生成仍用原始问题
    String prompt = promptService.buildPrompt(question, results);

    // ... 后续流式生成 ...
}
```

---

## 四、校园文档特化分块 + 元数据增强（P1）

### 背景

校园文档有鲜明的结构特征，不能用通用分块策略一刀切。通知公告通常短小完整，政策文件则按条款结构化。

### 文档分类与分块策略

| 文档类型 | 结构特征 | 分块策略 | 来源站点 |
|---------|---------|---------|---------|
| 通知公告 | 标题 + 正文 + 日期 | 整篇保留（通常 < 1000 字） | jwb, www, hqb, xsb |
| 政策文件 | 章节 + 条款 + 附件 | 按条款分块，保留章节路径 | 上传文档 |
| 办事流程 | 步骤式 | 按步骤分块，每步保留上下文 | 上传文档 |
| 课程/考试安排 | 表格密集 | 表格整体作为一个 chunk | jwb |

### 涉及改动

- 新增 `ingestion/chunker/CampusNoticeChunker.java`
- 修改 `t_document_chunk` 表结构（增加 metadata 字段）
- 修改 `knowledge/service/KnowledgeService.java`（文档分类逻辑）

### 元数据字段设计

在 Milvus 中为每个 chunk 存储以下 metadata 字段，用于过滤检索：

```json
{
    "source_department": "教务部",
    "document_type": "通知公告",
    "publish_date": "2026-03-15",
    "academic_year": "2025-2026",
    "semester": "第二学期",
    "category": "选课",
    "target_audience": "本科生",
    "source_site": "jwb",
    "source_url": "https://jwb.szu.edu.cn/info/1234.htm"
}
```

### 分块实现

```java
@Component
public class CampusNoticeChunker implements ChunkingStrategy {

    private static final int SHORT_NOTICE_THRESHOLD = 800; // 字符数

    @Override
    public List<DocumentChunk> chunk(String text, Map<String, Object> metadata) {
        String title = (String) metadata.getOrDefault("title", "");
        String department = extractDepartment(metadata);
        String category = classifyBySource(metadata);
        String publishDate = (String) metadata.getOrDefault("publish_date", "");

        if (text.length() <= SHORT_NOTICE_THRESHOLD) {
            // 短通知：整篇作为一个 chunk
            return List.of(DocumentChunk.builder()
                .chunkText(text)
                .metadata(Map.of(
                    "source_department", department,
                    "document_type", "通知公告",
                    "category", category,
                    "publish_date", publishDate
                ))
                .build());
        }

        // 长文档：按段落分块，每个 chunk 都带上元数据
        return splitByParagraphs(text).stream()
            .map(paragraph -> DocumentChunk.builder()
                .chunkText(paragraph)
                .metadata(Map.of(
                    "source_department", department,
                    "document_type", "政策文件",
                    "category", category,
                    "publish_date", publishDate,
                    "title", title
                ))
                .build())
            .collect(Collectors.toList());
    }

    /**
     * 根据来源站点自动分类
     */
    private String classifyBySource(Map<String, Object> metadata) {
        String source = (String) metadata.getOrDefault("source_site", "");
        return switch (source) {
            case "jwb" -> "教务";
            case "xsb" -> "学工";
            case "hqb" -> "后勤";
            case "gwt" -> "行政";
            default -> "综合";
        };
    }
}
```

### 带元数据的向量检索

修改 `MilvusVectorStoreService.search()`，支持按 metadata 过滤：

```java
public List<RetrievalResult> search(float[] vector, int topK, Map<String, String> filters) {
    SearchParam.Builder builder = SearchParam.newBuilder()
        .withCollectionName(collectionName)
        .withVectors(List.of(vector))
        .withTopK(topK)
        .withOutputFields(List.of("chunk_text", "source_title", "source_url",
            "source_department", "publish_date", "category"));

    // 如果有过滤条件，添加 filter expression
    if (filters != null && !filters.isEmpty()) {
        String expr = filters.entrySet().stream()
            .map(e -> String.format("%s == \"%s\"", e.getKey(), e.getValue()))
            .collect(Collectors.joining(" and "));
        builder.withExpr(expr);
    }

    R<SearchResults> response = milvusClient.search(builder.build());
    // ... 解析结果 ...
}
```

---

## 五、混合检索 + Rerank（P2）

### 背景

纯语义检索在精确关键词匹配上存在不足。用户问"四六级报名"→ 语义检索可能召回"六级考试安排"但漏掉包含"四六级"精确关键词的通知。

### 架构

```
用户查询 "四六级报名时间"
    ├─ 路径A: 语义检索 (Milvus) → 召回语义相关文档
    ├─ 路径B: 关键词检索 (MySQL FULLTEXT) → 精确匹配 "四六级"
    └─ 融合: RRF (Reciprocal Rank Fusion) → 统一排序
        └─ 精排: Reranker → 最终 Top-K 结果
```

### 涉及改动

- MySQL 表添加 FULLTEXT 索引
- 新增 `rag/retrieval/HybridRetrievalService.java`
- 新增 `rag/retrieval/RerankerService.java`

### MySQL 全文检索

```sql
ALTER TABLE t_document_chunk ADD FULLTEXT INDEX ft_chunk_text (chunk_text) WITH PARSER ngram;
```

```java
@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunk> {

    @Select("SELECT *, MATCH(chunk_text) AGAINST(#{query} IN NATURAL LANGUAGE MODE) AS score " +
            "FROM t_document_chunk " +
            "WHERE MATCH(chunk_text) AGAINST(#{query} IN NATURAL LANGUAGE MODE) " +
            "ORDER BY score DESC LIMIT #{topK}")
    List<DocumentChunk> fullTextSearch(@Param("query") String query, @Param("topK") int topK);
}
```

### RRF 融合算法

```java
@Service
public class HybridRetrievalService {

    private static final int RRF_K = 60; // RRF 平滑参数

    /**
     * 融合向量检索和关键词检索结果
     */
    public List<RetrievalResult> hybridMerge(
            List<RetrievalResult> semanticResults,
            List<RetrievalResult> keywordResults) {

        Map<String, Double> scoreMap = new HashMap<>();
        Map<String, RetrievalResult> resultMap = new HashMap<>();

        // 语义检索排序得分
        for (int i = 0; i < semanticResults.size(); i++) {
            String id = semanticResults.get(i).getChunkId();
            double rrfScore = 1.0 / (RRF_K + i + 1);
            scoreMap.merge(id, rrfScore, Double::sum);
            resultMap.putIfAbsent(id, semanticResults.get(i));
        }

        // 关键词检索排序得分
        for (int i = 0; i < keywordResults.size(); i++) {
            String id = keywordResults.get(i).getChunkId();
            double rrfScore = 1.0 / (RRF_K + i + 1);
            scoreMap.merge(id, rrfScore, Double::sum);
            resultMap.putIfAbsent(id, keywordResults.get(i));
        }

        // 按 RRF 得分排序
        return scoreMap.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .map(entry -> resultMap.get(entry.getKey()).withScore(entry.getValue()))
            .collect(Collectors.toList());
    }
}
```

---

## 六、角色感知 Prompt（P2）

### 背景

不同角色（学生、教师、访客）关心的问题完全不同，回答风格也应不同。

### 涉及改动

- 修改 `rag/prompt/RagPromptService.java`

### 角色化 Prompt

```java
private String buildSystemPrompt(String userRole, String calendarContext) {
    String basePrompt = """
        你是深大智答，深圳大学校园智能问答助手。

        ## 当前校园时间上下文
        %s
        """.formatted(calendarContext);

    String rolePrompt = switch (userRole) {
        case "student" -> """

            ## 回答风格（面向学生）
            - 优先给出具体的办事步骤和所需材料清单
            - 注明截止日期和负责部门，附上联系方式
            - 如果涉及费用，给出具体金额
            - 如果用户问的是流程性问题，用步骤列表回答
            - 遇到不确定的信息，明确告知并建议咨询相关部门
            """;
        case "teacher" -> """

            ## 回答风格（面向教职工）
            - 侧重教学安排、科研政策、人事流程
            - 引用政策文件时标注文号和发布日期
            - 如涉及跨部门协调，列出相关部门和对接人
            """;
        case "visitor" -> """

            ## 回答风格（面向访客/考生）
            - 侧重招生信息、校园介绍、入学流程
            - 提供官方链接供进一步了解
            - 语言简洁易懂，避免过多校内术语
            """;
        default -> """

            ## 回答风格
            - 基于检索到的参考内容回答，不要编造信息
            - 如果涉及截止日期，明确写出具体日期
            - 如果涉及办事流程，给出步骤和负责部门
            """;
    };

    return basePrompt + rolePrompt;
}
```

---

## 七、校园知识图谱（P3 - 长期方向）

### 背景

校园信息存在大量实体关联关系。例如"奖学金"关联"综测"、"绩点"、"辅导员"，这些关系用纯向量检索无法捕捉。

### 示例知识结构

```
奖学金申请
    ├── 前置条件: 综合测评 B级以上
    ├── 关联: 绩点计算 → GPA ≥ 3.0
    ├── 关联: 学分要求 → 已修满 X 学分
    ├── 负责部门: 学生部奖助学金管理办公室
    ├── 申请时间: 每年9月
    └── 关联政策: 《深圳大学奖学金评定办法》
```

### 技术选型

- 图数据库：Neo4j
- 实体抽取：LLM + 预定义 schema
- 查询方式：先图谱定位实体，再向量检索具体文档

> 此项为长期方向，建议在完成 P0-P2 后再启动。

---

## 优化优先级总览

| 优先级 | 优化项 | 预计工作量 | 效果 | 差异化程度 |
|-------|--------|----------|------|-----------|
| **P0** | 学术日历感知 | 2-3 天 | 极高 | 极高 |
| **P0** | 校园实体词典 | 0.5 天 | 高 | 高 |
| **P1** | 查询意图识别 + Query Rewriting | 2-3 天 | 高 | 高 |
| **P1** | 校园文档特化分块 + 元数据 | 3-4 天 | 高 | 高 |
| **P2** | 混合检索 + Rerank | 3-5 天 | 中高 | 中 |
| **P2** | 角色感知 Prompt | 1 天 | 中 | 中 |
| **P3** | 校园知识图谱 | 2-3 周 | 高 | 极高 |

### 建议实施顺序

```
第 1 周：P0（日历感知 + 实体词典）→ 立即可感受到差异化
第 2 周：P1（Query Rewriting + 特化分块）→ 检索质量大幅提升
第 3 周：P2（混合检索 + 角色 Prompt）→ 系统趋于完善
后续：  P3（知识图谱）→ 长期壁垒
```

---

## 附录：与通用 RAG 的差异化对比

| 能力 | 通用 RAG | 校园特化 RAG（本方案） |
|------|---------|---------------------|
| 时间感知 | 无 | 知道当前学期、教学周、近期节点 |
| 实体理解 | 依赖模型通用知识 | 校区别名、办事术语、部门简称全覆盖 |
| 查询理解 | 直接 embedding | 口语→标准术语→检索查询三级转换 |
| 文档处理 | 统一分块 | 按文档类型（通知/政策/流程）特化分块 |
| 检索策略 | 纯向量检索 | 语义+关键词混合检索+Rerank |
| 回答风格 | 千篇一律 | 根据角色（学生/教师/访客）动态调整 |
| 元数据利用 | 无 | 按部门、日期、分类精确过滤 |
| 知识关联 | 无 | 实体关系图谱（长期） |
