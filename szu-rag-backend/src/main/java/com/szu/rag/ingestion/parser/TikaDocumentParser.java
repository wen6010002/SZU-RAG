package com.szu.rag.ingestion.parser;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.List;

@Component
public class TikaDocumentParser implements DocumentParser {

    private static final List<String> SUPPORTED = List.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain"
    );

    private final Tika tika = new Tika();

    @Override
    public List<String> supportedMimeTypes() { return SUPPORTED; }

    @Override
    public String parse(byte[] content, String mimeType) {
        try {
            return tika.parseToString(new ByteArrayInputStream(content));
        } catch (Exception e) {
            throw new RuntimeException("Document parsing failed for " + mimeType, e);
        }
    }
}
