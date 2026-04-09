package com.szu.rag.chat.controller;

import com.szu.rag.chat.mapper.ConversationMapper;
import com.szu.rag.chat.mapper.MessageMapper;
import com.szu.rag.chat.model.entity.Conversation;
import com.szu.rag.chat.model.entity.Message;
import com.szu.rag.framework.exception.ClientException;
import com.szu.rag.framework.id.SnowflakeIdWorker;
import com.szu.rag.framework.result.Result;
import com.szu.rag.rag.chat.RagChatService;
import com.szu.rag.rag.ratelimit.RateLimitService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final RagChatService ragChatService;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final SnowflakeIdWorker idWorker;
    private final RateLimitService rateLimitService;

    @PostMapping("/conversations")
    public Result<Conversation> createConversation() {
        Conversation conv = new Conversation();
        conv.setId(idWorker.nextId());
        conv.setUserId(1L);
        conv.setTitle("");
        conv.setStatus("ACTIVE");
        conv.setMessageCount(0);
        conversationMapper.insert(conv);
        return Result.success(conv);
    }

    @GetMapping("/conversations")
    public Result<List<Conversation>> listConversations() {
        return Result.success(conversationMapper.selectList(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getUserId, 1L)
                        .eq(Conversation::getStatus, "ACTIVE")
                        .orderByDesc(Conversation::getUpdatedAt)));
    }

    @PostMapping(value = "/conversations/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(@PathVariable Long id, @RequestBody SendMessageRequest req) {
        if (!rateLimitService.allowRequest("chat:user:1", 20, 60)) {
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event().name("error")
                        .data("{\"code\":\"429\",\"message\":\"请求过于频繁，请稍后再试\"}"));
            } catch (Exception ignored) {}
            emitter.complete();
            return emitter;
        }
        return ragChatService.chat(id, req.getQuestion());
    }

    @GetMapping("/conversations/{id}/messages")
    public Result<List<Message>> getMessages(@PathVariable Long id) {
        return Result.success(messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, id)
                        .orderByAsc(Message::getCreatedAt)));
    }

    @DeleteMapping("/conversations/{id}")
    public Result<Void> deleteConversation(@PathVariable Long id) {
        Conversation conv = conversationMapper.selectById(id);
        if (conv != null) {
            conv.setStatus("ARCHIVED");
            conversationMapper.updateById(conv);
        }
        return Result.success();
    }

    @lombok.Data
    public static class SendMessageRequest {
        private String question;
    }
}
