package com.szu.rag.infra.chat;

import java.util.List;

/**
 * LLM Chat 客户端策略接口
 * MVP 只有 DeepSeekChatClient 一个实现，V2 加供应商只需新增实现类
 */
public interface ChatClient {

    /**
     * 流式对话
     */
    void chatStream(List<ChatMessage> messages, com.szu.rag.infra.stream.StreamCallback callback);

    /**
     * 同步对话
     */
    String chat(List<ChatMessage> messages);

    /**
     * 获取模型名称
     */
    String getModelName();
}
