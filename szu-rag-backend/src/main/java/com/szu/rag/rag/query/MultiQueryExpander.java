package com.szu.rag.rag.query;

import com.szu.rag.infra.chat.ChatClient;
import com.szu.rag.infra.chat.ChatMessage;
import com.szu.rag.rag.calendar.CampusCalendarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-Query 查询扩展服务
 * 使用 LLM 从不同角度生成多个查询变体，并行检索后合并去重，显著提升召回率
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiQueryExpander {

    private final ChatClient chatClient;
    private final CampusCalendarService calendarService;
    private final TimeExpressionResolver timeResolver;

    @Value("${rag.multi-query.enabled:true}")
    private boolean enabled;

    @Value("${rag.multi-query.count:3}")
    private int queryCount;

    private static final String MULTI_QUERY_PROMPT = """
            你是一个校园查询扩展助手。根据用户的原始问题，从不同角度生成%d个搜索查询变体。

            规则：
            1. 每个变体应该聚焦原始问题的不同方面
            2. 补充可能缺失的实体（如"深圳大学"）和同义词
            3. 如果是办事类问题，补充"流程 条件 材料 部门 时间"等维度
            4. 如果涉及时间，转换为具体日期
            5. 每行一个查询，不要编号，不要解释
            6. 不要重复原始问题，而是生成补充性的不同角度查询
            """;

    /**
     * 扩展查询：返回原始查询 + 多个变体
     * @param originalQuery 用户原始查询
     * @return 查询列表（第一个是原始查询，后面是变体）
     */
    public List<String> expand(String originalQuery) {
        List<String> queries = new ArrayList<>();
        queries.add(originalQuery);

        if (!enabled) {
            return queries;
        }

        try {
            // Step 1: 时间表达式预解析
            String resolved = timeResolver.resolve(originalQuery);

            // Step 2: LLM 生成变体
            String calendarContext = calendarService.getCurrentContext();
            String systemPrompt = String.format(MULTI_QUERY_PROMPT, queryCount);
            String userPrompt = String.format(
                    "当前校园时间：%s\n\n用户问题：%s\n\n生成%d个不同角度的搜索查询：",
                    calendarContext, resolved, queryCount);

            String response = chatClient.chat(
                    List.of(ChatMessage.system(systemPrompt), ChatMessage.user(userPrompt)));

            if (response != null && !response.isBlank()) {
                String[] lines = response.trim().split("\n");
                for (String line : lines) {
                    String cleaned = line.replaceAll("^[\\d]+[.、)\\s]+", "").trim();
                    if (!cleaned.isEmpty() && !cleaned.equals(originalQuery)) {
                        queries.add(cleaned);
                    }
                }
            }

            log.info("Multi-Query 扩展: 原始=[{}] → 生成{}个变体", originalQuery, queries.size() - 1);
        } catch (Exception e) {
            log.warn("Multi-Query 扩展失败，仅使用原始查询: {}", e.getMessage());
        }

        return queries;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
