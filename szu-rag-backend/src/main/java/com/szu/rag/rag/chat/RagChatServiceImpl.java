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
import com.szu.rag.knowledge.mapper.KnowledgeBaseMapper;
import com.szu.rag.knowledge.model.entity.KnowledgeBase;
import com.szu.rag.rag.memory.ConversationMemory;
import com.szu.rag.rag.prompt.RagPromptService;
import com.szu.rag.rag.vector.VectorStoreService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
    private final VectorStoreService vectorStoreService;
    private final RagPromptService promptService;
    private final ConversationMemory memory;
    private final SseEmitterSender sseSender;
    private final TokenCounterService tokenCounter;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final KnowledgeBaseMapper kbMapper;
    private final SnowflakeIdWorker idWorker;

    @Value("${rag.retrieval.top-k:5}")
    private int topK;
    @Value("${rag.retrieval.score-threshold:0.5}")
    private float scoreThreshold;
    @Value("${rag.memory.max-turns:10}")
    private int maxMemoryTurns;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public SseEmitter chat(Long conversationId, String question) {
        SseEmitter emitter = new SseEmitter(120_000L);

        executor.submit(() -> {
            long startTime = System.currentTimeMillis();
            try {
                // Step 1: 保存用户消息
                memory.addMessage(conversationId, "USER", question);

                // Step 2: 获取会话记忆
                String history = memory.formatHistory(conversationId, maxMemoryTurns);

                // Step 3: 向量检索
                sseSender.sendEvent(emitter, "thinking", Map.of("content", "正在检索相关知识库..."));
                List<Float> queryVector = embeddingClient.embed(question);
                List<VectorStoreService.SearchResult> searchResults = searchAllKnowledgeBases(queryVector);

                List<Map<String, Object>> sources = extractSources(searchResults);
                if (!sources.isEmpty()) {
                    sseSender.sendSources(emitter, sources);
                }

                // Step 4: 组装 Prompt
                String systemPrompt = promptService.buildPrompt(question, searchResults, history);

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

    private List<VectorStoreService.SearchResult> searchAllKnowledgeBases(List<Float> queryVector) {
        List<KnowledgeBase> kbs = kbMapper.selectList(
                new LambdaQueryWrapper<KnowledgeBase>().eq(KnowledgeBase::getStatus, "ACTIVE"));
        List<VectorStoreService.SearchResult> allResults = new ArrayList<>();
        for (KnowledgeBase kb : kbs) {
            try {
                List<VectorStoreService.SearchResult> results =
                        vectorStoreService.search(kb.getCollectionName(), queryVector, topK, scoreThreshold);
                allResults.addAll(results);
            } catch (Exception e) {
                log.warn("Search failed for KB {}: {}", kb.getId(), e.getMessage());
            }
        }
        return allResults.stream()
                .sorted((a, b) -> Float.compare(b.score(), a.score()))
                .limit(topK)
                .toList();
    }

    private List<Map<String, Object>> extractSources(List<VectorStoreService.SearchResult> results) {
        return results.stream().map(r -> {
            Map<String, Object> source = new HashMap<>();
            source.put("title", r.metadata().getOrDefault("source_title", ""));
            source.put("url", r.metadata().getOrDefault("source_url", ""));
            source.put("relevance", String.format("%.2f", r.score()));
            String text = r.metadata().getOrDefault("chunk_text", "").toString();
            source.put("snippet", text.substring(0, Math.min(100, text.length())) + "...");
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
