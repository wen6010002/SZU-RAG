package com.szu.rag.framework.sse;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class SseEmitterManager {
    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter create(String sessionId, long timeoutMs) {
        SseEmitter emitter = new SseEmitter(timeoutMs);
        emitters.put(sessionId, emitter);
        emitter.onCompletion(() -> emitters.remove(sessionId));
        emitter.onTimeout(() -> emitters.remove(sessionId));
        emitter.onError(e -> emitters.remove(sessionId));
        return emitter;
    }

    public void remove(String sessionId) {
        emitters.remove(sessionId);
    }

    public SseEmitter get(String sessionId) {
        return emitters.get(sessionId);
    }
}
