package com.szu.rag.chat.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_message")
public class Message {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long conversationId;
    private String role;
    private String content;
    private String sources;
    private Integer tokenCount;
    private String modelName;
    private Integer durationMs;
    private LocalDateTime createdAt;
}
