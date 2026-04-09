package com.szu.rag.rag.prompt;

import com.szu.rag.rag.vector.VectorStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RagPromptService {

    private final PromptTemplateLoader templateLoader;

    public String buildPrompt(String question,
                              List<VectorStoreService.SearchResult> searchResults,
                              String conversationHistory) {
        String context = formatContext(searchResults);
        return templateLoader.render("kb-qa", Map.of(
                "question", question,
                "context", context,
                "conversation_history", conversationHistory != null ? conversationHistory : "（无历史记录）"
        ));
    }

    private String formatContext(List<VectorStoreService.SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return "（无相关参考资料）";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            VectorStoreService.SearchResult result = results.get(i);
            Map<String, Object> meta = result.metadata();
            String title = meta.getOrDefault("source_title", "未知来源").toString();
            String text = meta.getOrDefault("chunk_text", "").toString();
            String url = meta.getOrDefault("source_url", "").toString();
            sb.append("### 参考资料 ").append(i + 1).append("\n");
            sb.append("- 来源：").append(title);
            if (url != null && !url.isEmpty()) sb.append("（").append(url).append("）");
            sb.append("\n- 相关度：").append(String.format("%.2f", result.score())).append("\n");
            sb.append(text).append("\n\n");
        }
        return sb.toString();
    }
}
