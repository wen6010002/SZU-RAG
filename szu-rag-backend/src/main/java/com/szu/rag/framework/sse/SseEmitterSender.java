package com.szu.rag.framework.sse;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
public class SseEmitterSender {

    public void sendEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(JSON.toJSONString(data)));
        } catch (IOException e) {
            log.warn("SSE send failed for event {}: {}", eventName, e.getMessage());
        }
    }

    public void sendContent(SseEmitter emitter, String content) {
        sendEvent(emitter, "content", Map.of("content", content));
    }

    public void sendThinking(SseEmitter emitter, String content) {
        sendEvent(emitter, "thinking", Map.of("content", content));
    }

    public void sendSources(SseEmitter emitter, Object sources) {
        sendEvent(emitter, "sources", sources);
    }

    public void sendComplete(SseEmitter emitter, Object data) {
        sendEvent(emitter, "complete", data);
        emitter.complete();
    }

    public void sendError(SseEmitter emitter, String code, String message) {
        sendEvent(emitter, "error", Map.of("code", code, "message", message));
        emitter.complete();
    }
}
