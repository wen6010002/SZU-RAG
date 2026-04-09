package com.szu.rag.rag.memory;

import java.util.List;

public interface ConversationMemory {
    List<MessagePair> getRecentMessages(Long conversationId, int maxTurns);
    void addMessage(Long conversationId, String role, String content);
    String formatHistory(Long conversationId, int maxTurns);

    record MessagePair(String userMessage, String assistantMessage) {}
}
