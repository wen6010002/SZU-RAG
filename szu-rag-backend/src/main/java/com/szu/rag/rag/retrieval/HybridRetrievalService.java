package com.szu.rag.rag.retrieval;

import com.szu.rag.knowledge.mapper.DocumentChunkMapper;
import com.szu.rag.knowledge.mapper.KnowledgeBaseMapper;
import com.szu.rag.knowledge.model.entity.DocumentChunk;
import com.szu.rag.knowledge.model.entity.KnowledgeBase;
import com.szu.rag.rag.vector.VectorStoreService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

/**
 * 混合检索服务 —— 融合语义检索（Milvus）和关键词检索（MySQL FULLTEXT）
 * 使用 RRF (Reciprocal Rank Fusion) 算法融合两路结果
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridRetrievalService {

    private final VectorStoreService vectorStoreService;
    private final DocumentChunkMapper chunkMapper;
    private final KnowledgeBaseMapper kbMapper;

    @Value("${rag.retrieval.top-k:5}")
    private int topK;

    @Value("${rag.retrieval.score-threshold:0.5}")
    private float scoreThreshold;

    @Value("${rag.hybrid-retrieval.enabled:false}")
    private boolean hybridEnabled;

    private static final int RRF_K = 60;

    /**
     * 混合检索：语义 + 关键词 → RRF 融合
     * @param queryText    查询文本（用于关键词检索）
     * @param queryVector  查询向量（用于语义检索）
     * @return 融合排序后的检索结果
     */
    public List<VectorStoreService.SearchResult> retrieve(String queryText, List<Float> queryVector) {
        List<KnowledgeBase> kbs = kbMapper.selectList(
                new LambdaQueryWrapper<KnowledgeBase>().eq(KnowledgeBase::getStatus, "ACTIVE"));

        if (!hybridEnabled) {
            // 仅语义检索（原有逻辑）
            return semanticSearchOnly(kbs, queryVector);
        }

        // 混合检索：两路并行
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // 路径 A: 语义检索
            Future<List<VectorStoreService.SearchResult>> semanticFuture = executor.submit(
                    () -> semanticSearchOnly(kbs, queryVector));

            // 路径 B: 关键词检索
            Future<List<VectorStoreService.SearchResult>> keywordFuture = executor.submit(
                    () -> keywordSearchOnly(queryText));

            List<VectorStoreService.SearchResult> semanticResults = semanticFuture.get(5, TimeUnit.SECONDS);
            List<VectorStoreService.SearchResult> keywordResults = keywordFuture.get(5, TimeUnit.SECONDS);

            // RRF 融合
            return rrfMerge(semanticResults, keywordResults);
        } catch (Exception e) {
            log.warn("混合检索失败，回退到纯语义检索: {}", e.getMessage());
            return semanticSearchOnly(kbs, queryVector);
        } finally {
            executor.shutdown();
        }
    }

    /**
     * 纯语义检索
     */
    private List<VectorStoreService.SearchResult> semanticSearchOnly(
            List<KnowledgeBase> kbs, List<Float> queryVector) {
        List<VectorStoreService.SearchResult> allResults = new ArrayList<>();
        for (KnowledgeBase kb : kbs) {
            try {
                allResults.addAll(
                        vectorStoreService.search(kb.getCollectionName(), queryVector, topK * 2, scoreThreshold));
            } catch (Exception e) {
                log.warn("语义检索失败 KB {}: {}", kb.getId(), e.getMessage());
            }
        }
        return allResults.stream()
                .sorted((a, b) -> Float.compare(b.score(), a.score()))
                .limit(topK * 2)
                .toList();
    }

    /**
     * MySQL FULLTEXT 关键词检索
     */
    private List<VectorStoreService.SearchResult> keywordSearchOnly(String queryText) {
        List<VectorStoreService.SearchResult> results = new ArrayList<>();
        try {
            // 跨所有活跃知识库的 FULLTEXT 搜索
            List<DocumentChunk> chunks = chunkMapper.fullTextSearch(queryText, null, topK * 2);
            for (int i = 0; i < chunks.size(); i++) {
                DocumentChunk chunk = chunks.get(i);
                Map<String, Object> meta = new HashMap<>();
                meta.put("document_id", chunk.getDocumentId());
                meta.put("chunk_index", chunk.getChunkIndex());
                meta.put("chunk_text", chunk.getChunkText());
                meta.put("source_title", chunk.getSourceTitle() != null ? chunk.getSourceTitle() : "");
                meta.put("source_url", chunk.getSourceUrl() != null ? chunk.getSourceUrl() : "");
                meta.put("source_department", chunk.getSourceDepartment() != null ? chunk.getSourceDepartment() : "");
                meta.put("document_type", chunk.getDocumentType() != null ? chunk.getDocumentType() : "");
                meta.put("category", chunk.getCategory() != null ? chunk.getCategory() : "");

                // 用排名的倒数作为伪分数
                float pseudoScore = 1.0f / (i + 1);
                results.add(new VectorStoreService.SearchResult(chunk.getId(), pseudoScore, meta));
            }
        } catch (Exception e) {
            log.warn("FULLTEXT 检索失败: {}", e.getMessage());
        }
        return results;
    }

    /**
     * RRF 融合算法
     */
    private List<VectorStoreService.SearchResult> rrfMerge(
            List<VectorStoreService.SearchResult> semanticResults,
            List<VectorStoreService.SearchResult> keywordResults) {

        Map<Long, Double> scoreMap = new HashMap<>();
        Map<Long, VectorStoreService.SearchResult> resultMap = new HashMap<>();

        // 语义检索排名得分
        for (int i = 0; i < semanticResults.size(); i++) {
            Long id = semanticResults.get(i).id();
            double rrfScore = 1.0 / (RRF_K + i + 1);
            scoreMap.merge(id, rrfScore, Double::sum);
            resultMap.putIfAbsent(id, semanticResults.get(i));
        }

        // 关键词检索排名得分
        for (int i = 0; i < keywordResults.size(); i++) {
            Long id = keywordResults.get(i).id();
            double rrfScore = 1.0 / (RRF_K + i + 1);
            scoreMap.merge(id, rrfScore, Double::sum);
            resultMap.putIfAbsent(id, keywordResults.get(i));
        }

        // 按 RRF 得分排序
        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> {
                    VectorStoreService.SearchResult original = resultMap.get(entry.getKey());
                    return new VectorStoreService.SearchResult(
                            original.id(),
                            entry.getValue().floatValue(),
                            original.metadata()
                    );
                })
                .toList();
    }
}
