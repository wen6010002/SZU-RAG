package com.szu.rag.rag.memory;

import com.szu.rag.chat.mapper.MessageMapper;
import com.szu.rag.chat.model.entity.Message;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JdbcConversationMemory implements ConversationMemory {

    private final MessageMapper messageMapper;

    @Override
    public List<MessagePair> getRecentMessages(Long conversationId, int maxTurns) {
        List<Message> messages = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)
                        .in(Message::getRole, List.of("USER", "ASSISTANT"))
                        .orderByDesc(Message::getCreatedAt)
                        .last("LIMIT " + (maxTurns * 2))
        );

        List<Message> sorted = messages.stream()
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .toList();

        List<MessagePair> pairs = new ArrayList<>();
        for (int i = 0; i < sorted.size() - 1; i++) {
            if ("USER".equals(sorted.get(i).getRole()) && i + 1 < sorted.size()
                    && "ASSISTANT".equals(sorted.get(i + 1).getRole())) {
                pairs.add(new MessagePair(sorted.get(i).getContent(), sorted.get(i + 1).getContent()));
            }
        }
        return pairs;
    }

    @Override
    public void addMessage(Long conversationId, String role, String content) {
        Message msg = new Message();
        msg.setConversationId(conversationId);
        msg.setRole(role);
        msg.setContent(content);
        messageMapper.insert(msg);
    }

    @Override
    public String formatHistory(Long conversationId, int maxTurns) {
        List<MessagePair> pairs = getRecentMessages(conversationId, maxTurns);
        if (pairs.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (MessagePair pair : pairs) {
            sb.append("学生：").append(pair.userMessage()).append("\n");
            sb.append("助手：").append(pair.assistantMessage()).append("\n");
        }
        return sb.toString();
    }
}
