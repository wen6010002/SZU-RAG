package com.szu.rag.infra.token;

import org.springframework.stereotype.Service;

/**
 * 启发式 Token 估算（中英文代码混合）
 */
@Service
public class TokenCounterService {

    /** 估算文本的 Token 数 */
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;

        int chineseChars = 0;
        int otherChars = 0;

        for (char c : text.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chineseChars++;
            } else {
                otherChars++;
            }
        }
        // 中文约 1 字 ≈ 1.5 token，英文约 4 字符 ≈ 1 token
        return (int) (chineseChars * 1.5 + otherChars / 4.0);
    }

    /** 截断文本到指定 Token 数，在句子边界处截断 */
    public String truncateToTokens(String text, int maxTokens) {
        int estimatedMaxChars = (int) (maxTokens * 0.6);
        if (text.length() <= estimatedMaxChars) return text;

        String truncated = text.substring(0, estimatedMaxChars);
        int lastSentenceEnd = Math.max(
                Math.max(truncated.lastIndexOf('\u3002'), truncated.lastIndexOf('\uff01')),
                Math.max(truncated.lastIndexOf('\uff1f'), truncated.lastIndexOf('.'))
        );
        if (lastSentenceEnd > estimatedMaxChars * 0.8) {
            truncated = truncated.substring(0, lastSentenceEnd + 1);
        }
        return truncated;
    }
}
