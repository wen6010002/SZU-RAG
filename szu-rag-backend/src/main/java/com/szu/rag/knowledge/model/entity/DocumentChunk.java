package com.szu.rag.knowledge.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_document_chunk")
public class DocumentChunk {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long documentId;
    private Long knowledgeBaseId;
    private Integer chunkIndex;
    private String chunkText;
    private Integer charCount;
    private String chunkHash;
    private String sourceTitle;
    private String sourceUrl;
    private LocalDate publishDate;
    private String sourceDepartment;
    private String documentType;
    private String category;
    private String metadata;
    private Long milvusId;
    private LocalDateTime createdAt;
}
