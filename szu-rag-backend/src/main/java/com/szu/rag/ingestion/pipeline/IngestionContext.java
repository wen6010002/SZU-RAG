package com.szu.rag.ingestion.pipeline;

import lombok.Data;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class IngestionContext {
    private Long knowledgeBaseId;
    private Long documentId;
    private String fileName;
    private String filePath;
    private String mimeType;
    private String sourceUrl;
    private String rawText;
    private List<String> chunks = new ArrayList<>();
    private List<Long> chunkIds = new ArrayList<>();
    private List<Long> milvusIds = new ArrayList<>();
    private Map<String, Object> metadata = new HashMap<>();
    private String status = "PENDING";
    private String errorMessage;
}
