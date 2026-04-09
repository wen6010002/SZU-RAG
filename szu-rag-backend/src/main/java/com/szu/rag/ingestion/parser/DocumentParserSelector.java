package com.szu.rag.ingestion.parser;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DocumentParserSelector {

    private final List<DocumentParser> parsers;

    public DocumentParserSelector(List<DocumentParser> parsers) {
        this.parsers = parsers;
    }

    public DocumentParser select(String mimeType) {
        return parsers.stream()
                .filter(p -> p.supportedMimeTypes().contains(mimeType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No parser for MIME type: " + mimeType));
    }
}
