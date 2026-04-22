package com.szu.rag.rag.retrieval;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.szu.rag.infra.config.AiProperties;
import com.szu.rag.rag.vector.VectorStoreService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * DashScope Reranker 重排序服务
 * 使用 gte-rerank 模型对检索结果进行精排，提升 top-K 结果质量
 */
@Slf4j
@Service
public class RerankerService {

    private final AiProperties aiProperties;
    private final OkHttpClient httpClient;

    @Value("${rag.reranker.enabled:true}")
    private boolean enabled;

    @Value("${rag.reranker.top-n:5}")
    private int topN;

    private static final String RERANK_URL = "https://dashscope.aliyuncs.com/api/v1/services/rerank";
    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    public RerankerService(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 对检索结果进行重排序
     * @param query  匇定查询
     * @param results 检索结果列表
     * @return 重排序后的结果（截取 topN）
     */
    public List<VectorStoreService.SearchResult> rerank(String query,
                                                        List<VectorStoreService.SearchResult> results) {
        if (!enabled || results.isEmpty()) {
            return results;
        }

        try {
            // 构建文档列表
            List<String> documents = new ArrayList<>();
            for (VectorStoreService.SearchResult r : results) {
                String text = r.metadata().getOrDefault("chunk_text", "").toString();
                String title = r.metadata().getOrDefault("source_title", "").toString();
                documents.add("[" + title + "] " + text);
            }

            // 调用 DashScope rerank API
            JSONObject body = new JSONObject();
            body.put("model", "gte-rerank");
            body.put("query", query);
            body.put("documents", documents);
            body.put("top_n", Math.min(topN, documents.size()));
            body.put("return_documents", false);

            String apiKey = aiProperties.getEmbedding().getBailian().getApiKey();
            Request request = new Request.Builder()
                    .url(RERANK_URL)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(body.toJSONString(), JSON_TYPE))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body().string();
                JSONObject json = JSON.parseObject(responseBody);

                JSONObject output = json.getJSONObject("output");
                if (output == null) {
                    log.warn("Reranker 返回空结果，使用原始排序");
                    return results;
                }

                JSONArray rerankResults = output.getJSONArray("results");
                if (rerankResults == null || rerankResults.isEmpty()) {
                    return results;
                }

                // 按 rerank score 重排
                List<VectorStoreService.SearchResult> reranked = new ArrayList<>();
                for (int i = 0; i < rerankResults.size(); i++) {
                    JSONObject item = rerankResults.getJSONObject(i);
                    int index = item.getIntValue("index", -1);
                    double score = item.containsKey("relevance_score") ? item.getDouble("relevance_score") : 0.0;
                    if (index >= 0 && index < results.size()) {
                        VectorStoreService.SearchResult original = results.get(index);
                        reranked.add(new VectorStoreService.SearchResult(
                                original.id(), (float) score, original.metadata()));
                    }
                }

                log.info("Reranker 重排序: {} 个候选 → {} 个结果, top score={}",
                        results.size(), reranked.size(),
                        reranked.isEmpty() ? "N/A" : String.format("%.4f", reranked.get(0).score()));

                return reranked;
            }
        } catch (Exception e) {
            log.warn("Reranker 调用失败，使用原始排序: {}", e.getMessage());
            return results;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
