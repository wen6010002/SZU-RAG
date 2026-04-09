package com.szu.rag.rag.chat;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface RagChatService {
    SseEmitter chat(Long conversationId, String question);
}
