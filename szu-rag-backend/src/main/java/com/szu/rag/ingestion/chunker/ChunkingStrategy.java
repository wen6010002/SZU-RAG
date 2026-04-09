package com.szu.rag.ingestion.chunker;

import java.util.List;

/**
 * 分块策略接口
 */
public interface ChunkingStrategy {
    String getName();
    List<String> chunk(String text, int chunkSize, int overlap);
}
