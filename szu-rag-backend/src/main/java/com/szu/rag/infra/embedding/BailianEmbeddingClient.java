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
        return results.isEmpty() ? List.of() : results.get(0);
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
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
