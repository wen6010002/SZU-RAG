package com.szu.rag.rag.vector;

import java.util.List;
import java.util.Map;

/**
 * 向量存储服务接口
 */
public interface VectorStoreService {

    /** 创建集合 */
    void createCollection(String collectionName, int dimension);

    /** 插入向量 */
    void insert(String collectionName, List<Long> ids, List<List<Float>> vectors, List<Map<String, Object>> metadataList);

    /** 删除向量 */
    void delete(String collectionName, List<Long> ids);

    /** 相似搜索 */
    List<SearchResult> search(String collectionName, List<Float> queryVector, int topK, float scoreThreshold);

    /** 搜索结果 */
    record SearchResult(Long id, float score, Map<String, Object> metadata) {}
}
