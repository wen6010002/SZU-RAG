package com.szu.rag.ingestion.chunker;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class StructureAwareChunker implements ChunkingStrategy {

    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+.+", Pattern.MULTILINE);
    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\n{2,}");

    @Override
    public String getName() { return "STRUCTURE_AWARE"; }

    @Override
    public List<String> chunk(String text, int chunkSize, int overlap) {
        if (text == null || text.isEmpty()) return List.of();
        if (text.length() <= chunkSize) return List.of(text);

        String[] sections = HEADING.split(text);
        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();

        for (String section : sections) {
            String trimmed = section.trim();
            if (trimmed.isEmpty()) continue;

            if (currentChunk.length() + trimmed.length() > chunkSize && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                if (overlap > 0 && currentChunk.length() > overlap) {
                    currentChunk = new StringBuilder(currentChunk.substring(currentChunk.length() - overlap));
                } else {
                    currentChunk = new StringBuilder();
                }
            }
            currentChunk.append(trimmed).append("\n\n");
        }

        if (currentChunk.length() > 0) {
            if (currentChunk.length() > chunkSize) {
                String[] paragraphs = PARAGRAPH_BREAK.split(currentChunk);
                StringBuilder subChunk = new StringBuilder();
                for (String para : paragraphs) {
                    if (subChunk.length() + para.length() > chunkSize && subChunk.length() > 0) {
                        chunks.add(subChunk.toString().trim());
                        subChunk = new StringBuilder();
                    }
                    subChunk.append(para).append("\n\n");
                }
                if (subChunk.length() > 0) {
                    chunks.add(subChunk.toString().trim());
                }
            } else {
                chunks.add(currentChunk.toString().trim());
            }
        }

        return chunks;
    }
}
