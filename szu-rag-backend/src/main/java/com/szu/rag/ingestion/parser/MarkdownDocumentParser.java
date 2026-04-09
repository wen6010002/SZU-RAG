package com.szu.rag.ingestion.parser;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class MarkdownDocumentParser implements DocumentParser {

    private static final List<String> SUPPORTED = List.of("text/markdown", "text/x-markdown");

    @Override
    public List<String> supportedMimeTypes() { return SUPPORTED; }

    @Override
    public String parse(byte[] content, String mimeType) {
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.startsWith("---")) {
            int end = text.indexOf("---", 3);
            if (end > 0) {
                text = text.substring(end + 3).trim();
            }
        }
        return text;
    }
}
