package com.szu.rag.knowledge.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_knowledge_document")
public class KnowledgeDocument {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long knowledgeBaseId;
    private String title;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String mimeType;
    private String sourceUrl;
    private String sourceType;
    private String documentStatus;
    private String processMode;
    private String errorMessage;
    private Integer chunkCount;
    private String sourceDepartment;
    private String documentType;
    private String category;
    private String academicYear;
    private String semester;
    private String targetAudience;
    private Long userId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
