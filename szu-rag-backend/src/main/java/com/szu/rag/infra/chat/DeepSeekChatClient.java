package com.szu.rag.infra.chat;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.szu.rag.infra.config.AiProperties;
import com.szu.rag.infra.stream.StreamCallback;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class DeepSeekChatClient implements ChatClient {

    private final AiProperties properties;
    private final OkHttpClient httpClient;

    public DeepSeekChatClient(AiProperties properties) {
        this.properties = properties;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public void chatStream(List<ChatMessage> messages, StreamCallback callback) {
        AiProperties.DeepSeekConfig config = properties.getChat().getDeepseek();

        JSONObject body = new JSONObject();
        body.put("model", config.getModel());
        body.put("messages", convertMessages(messages));
        body.put("stream", true);
        body.put("max_tokens", config.getMaxTokens());
        body.put("temperature", config.getTemperature());

        Request request = new Request.Builder()
                .url(config.getBaseUrl() + "/chat/completions")
                .addHeader("Authorization", "Bearer " + config.getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                .build();

        StringBuilder fullResponse = new StringBuilder();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "unknown";
                callback.onError(new RuntimeException("LLM API error: " + response.code() + " " + errorBody));
                return;
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (callback.isCancelled()) break;
                if (line.startsWith("data: ")) {
                    String data = line.substring(6).trim();
                    if ("[DONE]".equals(data)) break;

                    JSONObject chunk = JSON.parseObject(data);
                    JSONArray choices = chunk.getJSONArray("choices");
                    if (choices != null && !choices.isEmpty()) {
                        JSONObject delta = choices.getJSONObject(0).getJSONObject("delta");
                        if (delta != null && delta.containsKey("content")) {
                            String content = delta.getString("content");
                            if (content != null) {
                                fullResponse.append(content);
                                callback.onContent(content);
                            }
                        }
                    }
                }
            }
            callback.onComplete(fullResponse.toString());
        } catch (Exception e) {
            log.error("Chat stream error", e);
            callback.onError(e);
        }
    }

    @Override
    public String chat(List<ChatMessage> messages) {
        AiProperties.DeepSeekConfig config = properties.getChat().getDeepseek();

        JSONObject body = new JSONObject();
        body.put("model", config.getModel());
        body.put("messages", convertMessages(messages));
        body.put("max_tokens", config.getMaxTokens());
        body.put("temperature", config.getTemperature());

        Request request = new Request.Builder()
                .url(config.getBaseUrl() + "/chat/completions")
                .addHeader("Authorization", "Bearer " + config.getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body().string();
            JSONObject json = JSON.parseObject(responseBody);
            return json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");
        } catch (Exception e) {
            throw new RuntimeException("Chat request failed", e);
        }
    }

    @Override
    public String getModelName() {
        return properties.getChat().getDeepseek().getModel();
    }

    private JSONArray convertMessages(List<ChatMessage> messages) {
        JSONArray arr = new JSONArray();
        for (ChatMessage msg : messages) {
            arr.add(new JSONObject().fluentPut("role", msg.getRole()).fluentPut("content", msg.getContent()));
        }
        return arr;
    }
}
