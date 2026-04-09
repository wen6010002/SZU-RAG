package com.szu.rag.ingestion.parser;

import java.util.List;

/**
 * 文档解析器接口
 */
public interface DocumentParser {
    List<String> supportedMimeTypes();
    String parse(byte[] content, String mimeType);
}
