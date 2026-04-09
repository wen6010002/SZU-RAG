package com.szu.rag.infra.embedding;

import java.util.List;

/**
 * Embedding 客户端策略接口
 * MVP 使用阿里百炼，V2 可加本地 Ollama
 */
public interface EmbeddingClient {

    /** 单文本向量化 */
    List<Float> embed(String text);

    /** 批量文本向量化 */
    List<List<Float>> embedBatch(List<String> texts);

    /** 向量维度 */
    int getDimension();
}
