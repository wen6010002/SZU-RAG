package com.szu.rag.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.szu.rag.framework.exception.ClientException;
import com.szu.rag.framework.id.SnowflakeIdWorker;
import com.szu.rag.ingestion.pipeline.IngestionContext;
import com.szu.rag.ingestion.pipeline.IngestionEngine;
import com.szu.rag.knowledge.mapper.KnowledgeBaseMapper;
import com.szu.rag.knowledge.mapper.KnowledgeDocumentMapper;
import com.szu.rag.knowledge.model.entity.KnowledgeBase;
import com.szu.rag.knowledge.model.entity.KnowledgeDocument;
import com.szu.rag.rag.vector.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final KnowledgeBaseMapper kbMapper;
    private final KnowledgeDocumentMapper docMapper;
    private final IngestionEngine ingestionEngine;
    private final VectorStoreService vectorStoreService;
    private final SnowflakeIdWorker idWorker;

    @Value("${rag.storage.path:./data/storage}")
    private String storagePath;

    public KnowledgeBase createKnowledgeBase(String name, String description) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(idWorker.nextId());
        kb.setName(name);
        kb.setDescription(description);
        kb.setCollectionName("szu_rag_kb_" + kb.getId());
        kb.setEmbeddingDim(1024);
        kb.setChunkStrategy("STRUCTURE_AWARE");
        kb.setChunkSize(500);
        kb.setChunkOverlap(50);
        kb.setDocumentCount(0);
        kb.setChunkCount(0);
        kb.setStatus("ACTIVE");
        kb.setUserId(1L);
        kbMapper.insert(kb);

        vectorStoreService.createCollection(kb.getCollectionName(), kb.getEmbeddingDim());
        return kb;
    }

    public KnowledgeDocument uploadDocument(Long kbId, MultipartFile file, String sourceUrl) {
        KnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb == null) throw new ClientException("404", "知识库不存在");

        String originalName = file.getOriginalFilename();
        String ext = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf(".")) : "";
        String storedName = UUID.randomUUID().toString() + ext;

        try {
            Path dir = Path.of(storagePath).toAbsolutePath();
            Files.createDirectories(dir);
            Path filePath = dir.resolve(storedName);
            log.info("Saving file to: {}, size: {} bytes", filePath, file.getSize());
            file.transferTo(filePath);
            log.info("File saved successfully");

            KnowledgeDocument doc = new KnowledgeDocument();
            doc.setId(idWorker.nextId());
            doc.setKnowledgeBaseId(kbId);
            doc.setTitle(originalName);
            doc.setFileName(originalName);
            doc.setFilePath(filePath.toString());
            doc.setFileSize(file.getSize());
            doc.setMimeType(detectMimeType(originalName, file.getContentType()));
            doc.setSourceUrl(sourceUrl != null ? sourceUrl : "");
            doc.setSourceType(sourceUrl != null && !sourceUrl.isEmpty() ? "CRAWLER" : "MANUAL");
            doc.setDocumentStatus("UPLOADED");
            doc.setProcessMode("PIPELINE");
            doc.setUserId(1L);
            docMapper.insert(doc);
            log.info("Document record inserted: id={}", doc.getId());

            IngestionContext context = new IngestionContext();
            context.setKnowledgeBaseId(kbId);
            context.setDocumentId(doc.getId());
            context.setFileName(originalName);
            context.setFilePath(filePath.toString());
            context.setMimeType(doc.getMimeType());
            context.setSourceUrl(sourceUrl);
            ingestionEngine.execute(context);

            return doc;
        } catch (IOException e) {
            log.error("File save failed", e);
            throw new RuntimeException("文件保存失败", e);
        } catch (Exception e) {
            log.error("Upload failed", e);
            throw new RuntimeException("上传失败: " + e.getMessage(), e);
        }
    }

    public List<KnowledgeBase> listKnowledgeBases() {
        return kbMapper.selectList(null);
    }

    public List<KnowledgeDocument> listDocuments(Long kbId) {
        return docMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getKnowledgeBaseId, kbId)
                        .orderByDesc(KnowledgeDocument::getCreatedAt));
    }

    private String detectMimeType(String fileName, String contentType) {
        if (contentType != null && !contentType.equals("application/octet-stream")) {
            return contentType;
        }
        if (fileName == null) return "application/octet-stream";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".md")) return "text/markdown";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        return "application/octet-stream";
    }
}
