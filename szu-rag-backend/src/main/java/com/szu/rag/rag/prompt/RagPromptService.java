package com.szu.rag.rag.prompt;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.szu.rag.infra.chat.ChatClient;
import com.szu.rag.infra.chat.ChatMessage;
import com.szu.rag.rag.calendar.CampusCalendarService;
import com.szu.rag.rag.vector.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagPromptService {

    private final PromptTemplateLoader templateLoader;
    private final CampusCalendarService calendarService;
    private final ChatClient chatClient;

    @Value("${rag.conversation-summary.summary-max-chars:300}")
    private int summaryMaxChars;

    public String buildPrompt(String question,
                              List<VectorStoreService.SearchResult> searchResults,
                              String conversationHistory) {
        return buildPrompt(question, searchResults, conversationHistory, "student");
    }

    public String buildPrompt(String question,
                              List<VectorStoreService.SearchResult> searchResults,
                              String conversationHistory,
                              String role) {
        String context = formatContext(searchResults);
        String roleInstruction = getRoleInstruction(role);

        return templateLoader.render("answer-chat-kb", Map.of(
                "question", question,
                "context", context,
                "calendar_context", calendarService.getCurrentContext(),
                "role_instruction", roleInstruction,
                "conversation_history", conversationHistory != null ? conversationHistory : "（无历史记录）"
        ));
    }

    /**
     * 查询改写：将用户问题改写为适合检索的查询，解析校园缩写和指代消解
     */
    public QueryRewriteResult rewriteQuery(String question, String conversationHistory) {
        try {
            String historyParam = (conversationHistory != null && !conversationHistory.isEmpty())
                    ? conversationHistory : "（无历史记录）";

            String prompt = templateLoader.render("user-question-rewrite", Map.of(
                    "question", question,
                    "conversation_history", historyParam
            ));

            String response = chatClient.chat(List.of(
                    ChatMessage.system("你是一个查询改写助手，严格返回JSON格式。"),
                    ChatMessage.user(prompt)
            ));

            // 解析 JSON 响应
            String jsonStr = response.trim();
            if (jsonStr.startsWith("```")) {
                jsonStr = jsonStr.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
            }

            JSONObject json = JSON.parseObject(jsonStr);
            return new QueryRewriteResult(
                    json.getString("rewrite"),
                    json.getBooleanValue("should_split", false),
                    json.getList("sub_questions", String.class)
            );
        } catch (Exception e) {
            log.warn("查询改写失败，使用原始问题: {}", e.getMessage());
            return new QueryRewriteResult(question, false, List.of(question));
        }
    }

    /**
     * 会话摘要：将长对话压缩为话题导向的摘要
     */
    public String summarizeConversation(String conversationHistory) {
        try {
            String prompt = templateLoader.render("conversation-summary", Map.of(
                    "conversation_history", conversationHistory,
                    "summary_max_chars", String.valueOf(summaryMaxChars)
            ));

            return chatClient.chat(List.of(
                    ChatMessage.system("你是会话记忆摘要器，输出单行摘要文本。"),
                    ChatMessage.user(prompt)
            )).trim();
        } catch (Exception e) {
            log.warn("会话摘要生成失败，使用原始历史: {}", e.getMessage());
            return conversationHistory;
        }
    }

    private String getRoleInstruction(String role) {
        if (role == null) role = "student";
        return switch (role) {
            case "teacher" -> """
                    ## 角色与回答风格
                    你是深圳大学教职工办公助手，面向教师、辅导员和行政人员提供专业服务。
                    - 称呼用户为"老师您好"
                    - 使用正式书面语，注重政策准确性和流程规范性
                    - 侧重：政策依据（标注文号和发布日期）、审批流程、跨部门协调指引、人事/财务/科研管理
                    - 引用通知时必须标注发布部门和完整标题
                    - 涉及跨部门事务时，列出相关部门名称、对接科室和办公电话（如有）
                    - 涉及学生管理时，引用学生手册或教务规定的具体条款
                    - 对于科研经费、职称评审等敏感话题，提醒以学校正式通知为准
                    - 回答结构：政策依据 → 办理流程 → 所需材料 → 注意事项 → 对接方式
                    """;
            case "visitor" -> """
                    ## 角色与回答风格
                    你是深圳大学招生咨询和校园导览助手，面向考生、家长和校外访客。
                    - 称呼用户为"您好"
                    - 使用简洁易懂的日常语言，所有校内术语必须附带解释（如"教务部=负责本科生教学管理的部门"）
                    - 侧重：招生政策与分数线、校园介绍与设施、入学报到流程、校园地图与交通
                    - 不使用校内缩写（"荔园"→"深圳大学（别称荔园）"），不说"综测""选课系统"等需要校内账号的术语
                    - 主动提供官方网站链接和招生办电话供进一步咨询
                    - 如果问题涉及校内系统登录，提示这是在校生使用的功能
                    - 回答结构：简要结论 → 详细说明 → 官方链接/联系方式
                    """;
            default -> """
                    ## 角色与回答风格
                    你是深圳大学在校学生的学长/学姐助手，用亲切的同学口吻帮助解决校园生活中的各种问题。
                    - 称呼用户为"同学你好"或"同学您好"
                    - 侧重：办事流程（步骤化清单）、截止日期（用【⚠️重要日期】醒目标注）、所需材料、费用金额、常见坑点提醒
                    - 涉及选课/考试时主动提醒常见注意事项（如"选课别忘点确认""考试带学生证和身份证"）
                    - 涉及费用时给出具体金额（如"报名费30元"）
                    - 如果有具体联系方式（部门名称+办公地点+电话+办公时间），务必附上
                    - 遇到不确定或过时的信息，明确说"这个可能有变动，建议打XX部门电话确认：XXX"
                    - 回答结构：结论先行 → 分步骤操作指南 → 材料清单 → 截止日期 → 常见坑点 → 联系方式
                    """;
        };
    }

    private String formatContext(List<VectorStoreService.SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return "（无相关参考资料）";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            VectorStoreService.SearchResult result = results.get(i);
            Map<String, Object> meta = result.metadata();
            String title = meta.getOrDefault("source_title", "未知来源").toString();
            String text = meta.getOrDefault("chunk_text", "").toString();
            String url = meta.getOrDefault("source_url", "").toString();
            sb.append("### 参考资料 ").append(i + 1).append("\n");
            sb.append("- 来源：").append(title);
            if (url != null && !url.isEmpty()) sb.append("（").append(url).append("）");
            sb.append("\n- 相关度：").append(String.format("%.2f", result.score())).append("\n");
            sb.append(text).append("\n\n");
        }
        return sb.toString();
    }

    public record QueryRewriteResult(String rewrite, boolean shouldSplit, List<String> subQuestions) {}
}
