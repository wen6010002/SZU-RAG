package com.szu.rag.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.szu.rag.framework.context.UserContext;
import com.szu.rag.framework.exception.ClientException;
import com.szu.rag.framework.id.SnowflakeIdWorker;
import com.szu.rag.ingestion.pipeline.IngestionContext;
import com.szu.rag.ingestion.pipeline.IngestionEngine;
import com.szu.rag.knowledge.mapper.DocumentChunkMapper;
import com.szu.rag.knowledge.mapper.KnowledgeBaseMapper;
import com.szu.rag.knowledge.mapper.KnowledgeDocumentMapper;
import com.szu.rag.knowledge.model.entity.DocumentChunk;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final KnowledgeBaseMapper kbMapper;
    private final KnowledgeDocumentMapper docMapper;
    private final DocumentChunkMapper chunkMapper;
    private final IngestionAsyncService ingestionAsyncService;
    private final VectorStoreService vectorStoreService;
    private final SnowflakeIdWorker idWorker;

    @Value("${rag.storage.path:./data/storage}")
    private String storagePath;

    private static final int MAX_BATCH_SIZE = 20;
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB

    // ========== Knowledge Base ==========

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
        kb.setUserId(UserContext.getUserId());
        kbMapper.insert(kb);

        vectorStoreService.createCollection(kb.getCollectionName(), kb.getEmbeddingDim());
        return kb;
    }

    public List<KnowledgeBase> listKnowledgeBases() {
        return kbMapper.selectList(null);
    }

    // ========== Document Upload ==========

    public KnowledgeDocument uploadDocument(Long kbId, MultipartFile file, String sourceUrl) {
        KnowledgeDocument doc = saveFileAndCreateDoc(kbId, file, sourceUrl);
        triggerAsyncIngestion(doc, sourceUrl);
        return doc;
    }

    public List<KnowledgeDocument> uploadDocumentsBatch(Long kbId, MultipartFile[] files) {
        KnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb == null) throw new ClientException("404", "知识库不存在");
        if (files == null || files.length == 0) throw new ClientException("400", "请选择文件");
        if (files.length > MAX_BATCH_SIZE) throw new ClientException("400", "单次最多上传" + MAX_BATCH_SIZE + "个文件");

        List<KnowledgeDocument> docs = new ArrayList<>();
        for (MultipartFile file : files) {
            try {
                KnowledgeDocument doc = saveFileAndCreateDoc(kbId, file, null);
                docs.add(doc);
                triggerAsyncIngestion(doc, null);
            } catch (Exception e) {
                log.error("Batch upload failed for file: {}", file.getOriginalFilename(), e);
                // Create a failed record so the frontend can show the error
                KnowledgeDocument failedDoc = new KnowledgeDocument();
                failedDoc.setId(idWorker.nextId());
                failedDoc.setKnowledgeBaseId(kbId);
                failedDoc.setFileName(file.getOriginalFilename());
                failedDoc.setTitle(file.getOriginalFilename());
                failedDoc.setFileSize(file.getSize());
                failedDoc.setDocumentStatus("FAILED");
                failedDoc.setErrorMessage("文件保存失败: " + e.getMessage());
                failedDoc.setUserId(UserContext.getUserId());
                docMapper.insert(failedDoc);
                docs.add(failedDoc);
            }
        }
        return docs;
    }

    // ========== Document Operations ==========

    public KnowledgeDocument getDocument(Long docId) {
        return docMapper.selectById(docId);
    }

    public List<KnowledgeDocument> listDocuments(Long kbId) {
        return docMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getKnowledgeBaseId, kbId)
                        .orderByDesc(KnowledgeDocument::getCreatedAt));
    }

    public void deleteDocument(Long docId) {
        KnowledgeDocument doc = docMapper.selectById(docId);
        if (doc == null) throw new ClientException("404", "文档不存在");

        String collectionName = "szu_rag_kb_" + doc.getKnowledgeBaseId();

        // 1. Delete chunks from Milvus and DB
        List<DocumentChunk> chunks = chunkMapper.selectList(
                new LambdaQueryWrapper<DocumentChunk>().eq(DocumentChunk::getDocumentId, docId));
        if (!chunks.isEmpty()) {
            List<Long> milvusIds = chunks.stream().map(DocumentChunk::getMilvusId).toList();
            try {
                vectorStoreService.delete(collectionName, milvusIds);
            } catch (Exception e) {
                log.warn("Failed to delete vectors from Milvus for doc: {}", docId, e);
            }
            for (DocumentChunk chunk : chunks) {
                chunkMapper.deleteById(chunk.getId());
            }
        }

        // 2. Delete physical file
        try {
            Files.deleteIfExists(Path.of(doc.getFilePath()));
        } catch (IOException e) {
            log.warn("Failed to delete file: {}", doc.getFilePath(), e);
        }

        // 3. Delete document record
        docMapper.deleteById(docId);

        // 4. Update KB stats
        refreshKbStats(doc.getKnowledgeBaseId());
        log.info("Document deleted: {}", docId);
    }

    public KnowledgeDocument reprocessDocument(Long docId) {
        KnowledgeDocument doc = docMapper.selectById(docId);
        if (doc == null) throw new ClientException("404", "文档不存在");

        // Only allow reprocess from terminal states
        String status = doc.getDocumentStatus();
        if (!"COMPLETED".equals(status) && !"FAILED".equals(status)) {
            throw new ClientException("409", "文档正在处理中，无法重新处理");
        }

        String collectionName = "szu_rag_kb_" + doc.getKnowledgeBaseId();

        // Delete existing chunks
        List<DocumentChunk> chunks = chunkMapper.selectList(
                new LambdaQueryWrapper<DocumentChunk>().eq(DocumentChunk::getDocumentId, docId));
        if (!chunks.isEmpty()) {
            List<Long> milvusIds = chunks.stream().map(DocumentChunk::getMilvusId).toList();
            try {
                vectorStoreService.delete(collectionName, milvusIds);
            } catch (Exception e) {
                log.warn("Failed to delete old vectors for reprocess, doc: {}", docId, e);
            }
            for (DocumentChunk chunk : chunks) {
                chunkMapper.deleteById(chunk.getId());
            }
        }

        // Reset status and re-trigger ingestion
        doc.setDocumentStatus("UPLOADED");
        doc.setErrorMessage("");
        doc.setChunkCount(0);
        docMapper.updateById(doc);

        triggerAsyncIngestion(doc, doc.getSourceUrl());
        return doc;
    }

    // ========== Private Helpers ==========

    private KnowledgeDocument saveFileAndCreateDoc(Long kbId, MultipartFile file, String sourceUrl) {
        KnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb == null) throw new ClientException("404", "知识库不存在");

        // Validate file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ClientException("400", "文件大小不能超过100MB: " + file.getOriginalFilename());
        }

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
            doc.setSourceType("MANUAL");
            doc.setDocumentStatus("UPLOADED");
            doc.setProcessMode("PIPELINE");
            doc.setUserId(UserContext.getUserId());
            docMapper.insert(doc);
            log.info("Document record inserted: id={}", doc.getId());

            return doc;
        } catch (IOException e) {
            log.error("File save failed", e);
            throw new RuntimeException("文件保存失败: " + e.getMessage(), e);
        }
    }

    private void triggerAsyncIngestion(KnowledgeDocument doc, String sourceUrl) {
        IngestionContext context = new IngestionContext();
        context.setKnowledgeBaseId(doc.getKnowledgeBaseId());
        context.setDocumentId(doc.getId());
        context.setFileName(doc.getFileName());
        context.setFilePath(doc.getFilePath());
        context.setMimeType(doc.getMimeType());
        context.setSourceUrl(sourceUrl != null ? sourceUrl : doc.getSourceUrl());
        ingestionAsyncService.processDocument(doc.getId(), context);
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
        if (lower.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        return "application/octet-stream";
    }

    private void refreshKbStats(Long kbId) {
        try {
            KnowledgeBase kb = kbMapper.selectById(kbId);
            if (kb == null) return;

            Long docCount = docMapper.selectCount(
                    new LambdaQueryWrapper<KnowledgeDocument>()
                            .eq(KnowledgeDocument::getKnowledgeBaseId, kbId)
                            .eq(KnowledgeDocument::getDocumentStatus, "COMPLETED"));
            kb.setDocumentCount(docCount.intValue());

            var completedDocs = docMapper.selectList(
                    new LambdaQueryWrapper<KnowledgeDocument>()
                            .eq(KnowledgeDocument::getKnowledgeBaseId, kbId)
                            .eq(KnowledgeDocument::getDocumentStatus, "COMPLETED"));
            int totalChunks = completedDocs.stream()
                    .mapToInt(d -> d.getChunkCount() != null ? d.getChunkCount() : 0)
                    .sum();
            kb.setChunkCount(totalChunks);

            kbMapper.updateById(kb);
        } catch (Exception e) {
            log.error("Failed to refresh KB stats for kbId: {}", kbId, e);
        }
    }
}
