package com.szu.rag.infra.embedding;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.szu.rag.infra.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class BailianEmbeddingClient implements EmbeddingClient {

    private final AiProperties properties;
    private final OkHttpClient httpClient;

    public BailianEmbeddingClient(AiProperties properties) {
        this.properties = properties;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public List<Float> embed(String text) {
        List<List<Float>> results = embedBatch(List.of(text));
        if (results.isEmpty()) {
            throw new RuntimeException("Embedding returned empty results for text: " + text.substring(0, Math.min(50, text.length())));
        }
        return results.get(0);
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        // Split into smaller sub-batches to avoid API token limits
        int subBatchSize = 5;
        List<List<Float>> allEmbeddings = new ArrayList<>();
        for (int i = 0; i < texts.size(); i += subBatchSize) {
            List<String> subBatch = texts.subList(i, Math.min(i + subBatchSize, texts.size()));
            List<List<Float>> batchResult = callEmbeddingApi(subBatch);
            allEmbeddings.addAll(batchResult);
        }
        return allEmbeddings;
    }

    private List<List<Float>> callEmbeddingApi(List<String> texts) {
        // Truncate texts exceeding ~8000 chars (~8192 tokens for Chinese text)
        List<String> truncated = new ArrayList<>();
        for (String text : texts) {
            if (text.length() > 8000) {
                log.warn("Truncating embedding input from {} to 8000 chars", text.length());
                truncated.add(text.substring(0, 8000));
            } else {
                truncated.add(text);
            }
        }
        texts = truncated;

        // Log sizes for debugging
        for (int i = 0; i < texts.size(); i++) {
            log.debug("Embedding input[{}] length: {} chars", i, texts.get(i).length());
        }

        AiProperties.BailianConfig config = properties.getEmbedding().getBailian();

        JSONObject body = new JSONObject();
        body.put("model", config.getModel());
        body.put("input", texts);
        body.put("dimensions", config.getDimension());

        Request request = new Request.Builder()
                .url(config.getBaseUrl() + "/embeddings")
                .addHeader("Authorization", "Bearer " + config.getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body().string();
            JSONObject json = JSON.parseObject(responseBody);

            if (json.containsKey("error")) {
                log.error("Embedding API error: {}", json.getString("error"));
                throw new RuntimeException("Embedding API error: " + json.getString("error"));
            }

            JSONArray data = json.getJSONArray("data");
            List<List<Float>> embeddings = new ArrayList<>();
            for (int i = 0; i < data.size(); i++) {
                JSONArray embeddingArray = data.getJSONObject(i).getJSONArray("embedding");
                List<Float> embedding = new ArrayList<>();
                for (int j = 0; j < embeddingArray.size(); j++) {
                    embedding.add(embeddingArray.getFloat(j));
                }
                embeddings.add(embedding);
            }
            return embeddings;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Embedding request failed", e);
            throw new RuntimeException("Embedding failed", e);
        }
    }

    @Override
    public int getDimension() {
        return properties.getEmbedding().getBailian().getDimension();
    }
}
