package com.szu.rag.infra.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private ChatConfig chat = new ChatConfig();
    private EmbeddingConfig embedding = new EmbeddingConfig();

    @Data
    public static class ChatConfig {
        private String provider = "deepseek";
        private DeepSeekConfig deepseek = new DeepSeekConfig();
    }

    @Data
    public static class DeepSeekConfig {
        private String baseUrl = "https://api.deepseek.com";
        private String apiKey;
        private String model = "deepseek-chat";
        private int maxTokens = 2048;
        private double temperature = 0.7;
    }

    @Data
    public static class EmbeddingConfig {
        private String provider = "bailian";
        private BailianConfig bailian = new BailianConfig();
    }

    @Data
    public static class BailianConfig {
        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        private String apiKey;
        private String model = "text-embedding-v3";
        private int dimension = 1024;
    }
}
