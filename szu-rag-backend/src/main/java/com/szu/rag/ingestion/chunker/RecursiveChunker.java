package com.szu.rag.ingestion.chunker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 递归标点分块器：按分隔符优先级逐层拆分文本。
 * 优先级：双换行 → 单换行 → 中文句号/感叹/问号 → 逗号/分号 → 强制截断
 * 保证每个 chunk 不超过 chunkSize，同时尽量在自然语义边界切割。
 */
@Slf4j
@Component
public class RecursiveChunker implements ChunkingStrategy {

    private static final String[] SEPARATORS = {"\n\n", "\n", "。", "！", "？", ".", "!", "?", "，", "；", ",", ";", " "};

    @Override
    public String getName() { return "RECURSIVE"; }

    @Override
    public List<String> chunk(String text, int chunkSize, int overlap) {
        if (text == null || text.isEmpty()) return List.of();
        text = text.trim();
        if (text.length() <= chunkSize) return List.of(text);

        List<String> chunks = new ArrayList<>();
        splitRecursive(text, chunkSize, 0, chunks);

        // Safety: cap any chunk that somehow exceeds chunkSize
        List<String> safeChunks = new ArrayList<>();
        for (String c : chunks) {
            if (c.length() > chunkSize) {
                log.warn("Chunk exceeds chunkSize ({} > {}), splitting further", c.length(), chunkSize);
                int start = 0;
                while (start < c.length()) {
                    int end = Math.min(start + chunkSize, c.length());
                    String sub = c.substring(start, end).trim();
                    if (!sub.isBlank()) safeChunks.add(sub);
                    start = end;
                }
            } else {
                safeChunks.add(c);
            }
        }
        chunks = safeChunks;

        // Apply overlap
        if (overlap > 0 && chunks.size() > 1) {
            List<String> overlapped = new ArrayList<>();
            overlapped.add(chunks.get(0));
            for (int i = 1; i < chunks.size(); i++) {
                String prev = chunks.get(i - 1);
                String curr = chunks.get(i);
                String overlapText = prev.length() > overlap ? prev.substring(prev.length() - overlap) : prev;
                overlapped.add(overlapText + curr);
            }
            // Log max chunk size for debugging
            int maxLen = overlapped.stream().mapToInt(String::length).max().orElse(0);
            log.info("RecursiveChunker produced {} chunks (with overlap), max length: {}, min length: {}",
                    overlapped.size(), maxLen, overlapped.stream().mapToInt(String::length).min().orElse(0));
            return overlapped;
        }

        int maxLen = chunks.stream().mapToInt(String::length).max().orElse(0);
        log.info("RecursiveChunker produced {} chunks, max length: {}, min length: {}",
                chunks.size(), maxLen, chunks.stream().mapToInt(String::length).min().orElse(0));
        return chunks;
    }

    private void splitRecursive(String text, int chunkSize, int separatorIdx, List<String> result) {
        if (text.length() <= chunkSize) {
            if (!text.isBlank()) result.add(text.trim());
            return;
        }

        // No more separators to try — hard split
        if (separatorIdx >= SEPARATORS.length) {
            hardSplit(text, chunkSize, result);
            return;
        }

        String sep = SEPARATORS[separatorIdx];
        String[] parts = splitBySeparator(text, sep);

        StringBuilder current = new StringBuilder();
        for (String part : parts) {
            String piece = part.trim();
            if (piece.isEmpty()) continue;

            // If single piece already exceeds limit, recurse with next separator
            if (piece.length() > chunkSize) {
                // Flush current buffer first
                if (current.length() > 0) {
                    result.add(current.toString().trim());
                    current = new StringBuilder();
                }
                splitRecursive(piece, chunkSize, separatorIdx + 1, result);
                continue;
            }

            if (current.length() > 0 && current.length() + sep.length() + piece.length() > chunkSize) {
                result.add(current.toString().trim());
                current = new StringBuilder(piece);
            } else {
                if (current.length() > 0) current.append(sep);
                current.append(piece);
            }
        }

        if (current.length() > 0) {
            String last = current.toString().trim();
            if (last.length() <= chunkSize) {
                if (!last.isBlank()) result.add(last);
            } else {
                splitRecursive(last, chunkSize, separatorIdx + 1, result);
            }
        }
    }

    private String[] splitBySeparator(String text, String sep) {
        // Split but keep the separator attached to the end of each part
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (true) {
            int idx = text.indexOf(sep, start);
            if (idx == -1) break;
            parts.add(text.substring(start, idx + sep.length()));
            start = idx + sep.length();
        }
        if (start < text.length()) {
            parts.add(text.substring(start));
        }
        return parts.toArray(new String[0]);
    }

    private void hardSplit(String text, int chunkSize, List<String> result) {
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            result.add(text.substring(start, end).trim());
            start = end;
        }
    }
}
