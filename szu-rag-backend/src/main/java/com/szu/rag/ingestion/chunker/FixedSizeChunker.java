package com.szu.rag.ingestion.chunker;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class FixedSizeChunker implements ChunkingStrategy {

    @Override
    public String getName() { return "FIXED_SIZE"; }

    @Override
    public List<String> chunk(String text, int chunkSize, int overlap) {
        if (text == null || text.isEmpty()) return List.of();
        if (text.length() <= chunkSize) return List.of(text);

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            if (end < text.length()) {
                int lastPeriod = text.lastIndexOf('\u3002', end);
                int lastNewline = text.lastIndexOf('\n', end);
                int boundary = Math.max(lastPeriod, lastNewline);
                if (boundary > start + chunkSize / 2) {
                    end = boundary + 1;
                }
            }
            chunks.add(text.substring(start, end).trim());
            start = end - overlap;
            if (start >= text.length()) break;
        }
        return chunks;
    }
}
