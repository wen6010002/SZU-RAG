package com.szu.rag.rag.chat;

import com.szu.rag.chat.mapper.ConversationMapper;
import com.szu.rag.chat.mapper.MessageMapper;
import com.szu.rag.chat.model.entity.Conversation;
import com.szu.rag.chat.model.entity.Message;
import com.szu.rag.framework.id.SnowflakeIdWorker;
import com.szu.rag.framework.sse.SseEmitterSender;
import com.szu.rag.infra.chat.ChatClient;
import com.szu.rag.infra.chat.ChatMessage;
import com.szu.rag.infra.embedding.EmbeddingClient;
import com.szu.rag.infra.stream.StreamCallback;
import com.szu.rag.infra.token.TokenCounterService;
import com.szu.rag.rag.memory.ConversationMemory;
import com.szu.rag.rag.prompt.RagPromptService;
import com.szu.rag.rag.query.CampusEntityExpander;
import com.szu.rag.rag.query.MultiQueryExpander;
import com.szu.rag.rag.retrieval.HybridRetrievalService;
import com.szu.rag.rag.retrieval.RerankerService;
import com.szu.rag.rag.vector.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagChatServiceImpl implements RagChatService {

    private final ChatClient chatClient;
    private final EmbeddingClient embeddingClient;
    private final RagPromptService promptService;
    private final CampusEntityExpander entityExpander;
    private final MultiQueryExpander multiQueryExpander;
    private final HybridRetrievalService hybridRetrievalService;
    private final RerankerService rerankerService;
    private final ConversationMemory memory;
    private final SseEmitterSender sseSender;
    private final TokenCounterService tokenCounter;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final SnowflakeIdWorker idWorker;

    @Value("${rag.retrieval.top-k:5}")
    private int topK;
    @Value("${rag.retrieval.candidate-count:10}")
    private int candidateCount;
    @Value("${rag.retrieval.score-threshold:0.3}")
    private float scoreThreshold;
    @Value("${rag.memory.max-turns:10}")
    private int maxMemoryTurns;
    @Value("${rag.query-rewrite.enabled:true}")
    private boolean queryRewriteEnabled;
    @Value("${rag.conversation-summary.enabled:true}")
    private boolean conversationSummaryEnabled;
    @Value("${rag.conversation-summary.max-turns:10}")
    private int summaryTriggerTurns;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public SseEmitter chat(Long conversationId, String question) {
        return chat(conversationId, question, "student");
    }

    @Override
    public SseEmitter chat(Long conversationId, String question, String role) {
        SseEmitter emitter = new SseEmitter(120_000L);

        executor.submit(() -> {
            long startTime = System.currentTimeMillis();
            try {
                // Step 1: 保存用户消息
                memory.addMessage(conversationId, "USER", question);

                // Step 2: 获取会话记忆
                String history = memory.formatHistory(conversationId, maxMemoryTurns);

                // Step 2.5: 会话摘要（可配置，超过阈值轮次时压缩历史）
                if (conversationSummaryEnabled && history != null && !history.isEmpty()) {
                    long turnCount = history.lines().filter(l -> l.startsWith("用户：") || l.startsWith("助手：")).count();
                    if (turnCount >= summaryTriggerTurns * 2) {
                        log.info("会话轮次超过阈值({}), 触发摘要压缩", summaryTriggerTurns);
                        String summary = promptService.summarizeConversation(history);
                        history = "（以下为历史对话摘要）\n" + summary + "\n（以上为历史对话摘要）";
                    }
                }

                // Step 3: 查询改写 + Multi-Query 扩展 + 实体扩展 + 并行检索 + Reranker 重排
                sseSender.sendEvent(emitter, "thinking", Map.of("content", "正在检索知识库..."));

                // 3a: 查询改写（可配置，解析缩写和指代消解）
                String retrievalQuery = question;
                if (queryRewriteEnabled) {
                    RagPromptService.QueryRewriteResult rewriteResult =
                            promptService.rewriteQuery(question, history);
                    if (rewriteResult.shouldSplit() && rewriteResult.subQuestions() != null
                            && rewriteResult.subQuestions().size() > 1) {
                        // 多子问题：将子问题也加入查询列表
                        retrievalQuery = rewriteResult.rewrite();
                        log.info("查询改写(拆分): {} -> {}", question, rewriteResult.subQuestions());
                    } else {
                        retrievalQuery = rewriteResult.rewrite();
                        log.info("查询改写: {} -> {}", question, retrievalQuery);
                    }
                }

                // 3b: Multi-Query 生成查询变体（基于改写后的查询）
                List<String> queries = multiQueryExpander.expand(retrievalQuery);

                // 3b: 对每路查询做实体扩展 → embed → 检索
                List<VectorStoreService.SearchResult> allCandidates = new ArrayList<>();
                Set<Long> seenIds = new HashSet<>();

                for (String q : queries) {
                    String expandedQuery = entityExpander.expand(q);
                    List<Float> queryVector = embeddingClient.embed(expandedQuery);
                    List<VectorStoreService.SearchResult> results =
                            hybridRetrievalService.retrieve(expandedQuery, queryVector);

                    // 合并去重
                    for (VectorStoreService.SearchResult r : results) {
                        if (seenIds.add(r.id())) {
                            allCandidates.add(r);
                        }
                    }
                }

                log.info("Multi-Query 检索: {} 路查询, 去重后 {} 个候选", queries.size(), allCandidates.size());

                // 3c: Reranker 重排序 → 取 top-K
                List<VectorStoreService.SearchResult> searchResults;
                if (allCandidates.size() > topK && rerankerService.isEnabled()) {
                    searchResults = rerankerService.rerank(question, allCandidates);
                } else {
                    // 无需 rerank 或候选数不足，按原始分数排序截取
                    searchResults = allCandidates.stream()
                            .sorted((a, b) -> Float.compare(b.score(), a.score()))
                            .limit(topK)
                            .toList();
                }

                List<Map<String, Object>> sources = extractSources(searchResults);
                if (!sources.isEmpty()) {
                    sseSender.sendSources(emitter, sources);
                }

                // Step 4: 组装 Prompt（带角色）
                String systemPrompt = promptService.buildPrompt(question, searchResults, history, role);

                // Step 5: LLM 流式生成
                List<ChatMessage> messages = List.of(
                        ChatMessage.system(systemPrompt),
                        ChatMessage.user(question)
                );

                StringBuilder fullResponse = new StringBuilder();

                chatClient.chatStream(messages, new StreamCallback() {
                    @Override
                    public void onContent(String content) {
                        fullResponse.append(content);
                        sseSender.sendContent(emitter, content);
                    }

                    @Override
                    public void onComplete(String response) {
                        long duration = System.currentTimeMillis() - startTime;
                        saveAssistantMessage(conversationId, response, sources, duration);
                        sseSender.sendComplete(emitter, Map.of(
                                "messageId", idWorker.nextId(),
                                "tokenCount", tokenCounter.estimateTokens(response),
                                "durationMs", duration
                        ));
                    }

                    @Override
                    public void onError(Throwable error) {
                        log.error("Stream error in conversation {}", conversationId, error);
                        sseSender.sendError(emitter, "LLM_ERROR", error.getMessage());
                    }
                });

            } catch (Exception e) {
                log.error("RAG chat error", e);
                sseSender.sendError(emitter, "INTERNAL_ERROR", "处理问题时出错：" + e.getMessage());
            }
        });

        return emitter;
    }

    private List<Map<String, Object>> extractSources(List<VectorStoreService.SearchResult> results) {
        return results.stream().map(r -> {
            Map<String, Object> source = new HashMap<>();
            source.put("title", r.metadata().getOrDefault("source_title", ""));
            source.put("url", r.metadata().getOrDefault("source_url", ""));
            source.put("relevance", String.format("%.2f", r.score()));
            String text = r.metadata().getOrDefault("chunk_text", "").toString();
            source.put("snippet", text.substring(0, Math.min(100, text.length())) + "...");

            // 元数据字段
            source.put("department", r.metadata().getOrDefault("source_department", ""));
            source.put("documentType", r.metadata().getOrDefault("document_type", ""));
            source.put("publishDate", r.metadata().getOrDefault("publish_date", ""));
            source.put("category", r.metadata().getOrDefault("category", ""));

            return source;
        }).toList();
    }

    private void saveAssistantMessage(Long conversationId, String content,
                                       List<Map<String, Object>> sources, long durationMs) {
        Message msg = new Message();
        msg.setId(idWorker.nextId());
        msg.setConversationId(conversationId);
        msg.setRole("ASSISTANT");
        msg.setContent(content);
        msg.setSources(com.alibaba.fastjson2.JSON.toJSONString(sources));
        msg.setModelName(chatClient.getModelName());
        msg.setTokenCount(tokenCounter.estimateTokens(content));
        msg.setDurationMs((int) durationMs);
        messageMapper.insert(msg);

        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv != null) {
            if (conv.getTitle() == null || conv.getTitle().isEmpty()) {
                conv.setTitle(content.substring(0, Math.min(30, content.length())) + "...");
            }
            conv.setMessageCount(conv.getMessageCount() != null ? conv.getMessageCount() + 2 : 2);
            conversationMapper.updateById(conv);
        }
    }
}
