package com.szu.rag.knowledge.service;

import com.szu.rag.framework.id.SnowflakeIdWorker;
import com.szu.rag.ingestion.pipeline.IngestionContext;
import com.szu.rag.ingestion.pipeline.IngestionEngine;
import com.szu.rag.knowledge.mapper.KnowledgeBaseMapper;
import com.szu.rag.knowledge.mapper.KnowledgeDocumentMapper;
import com.szu.rag.knowledge.model.entity.KnowledgeBase;
import com.szu.rag.knowledge.model.entity.KnowledgeDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionAsyncService {

    private final IngestionEngine ingestionEngine;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeBaseMapper kbMapper;
    private final SnowflakeIdWorker idWorker;

    @Async("ingestionExecutor")
    public void processDocument(Long docId, IngestionContext context) {
        try {
            log.info("Async ingestion started for document: {}", docId);
            ingestionEngine.execute(context);

            // Update KB stats after completion
            updateKbStats(context.getKnowledgeBaseId());
            log.info("Async ingestion completed for document: {}", docId);
        } catch (Exception e) {
            log.error("Async ingestion failed for document: {}", docId, e);
            updateKbStats(context.getKnowledgeBaseId());
        }
    }

    private void updateKbStats(Long kbId) {
        try {
            KnowledgeBase kb = kbMapper.selectById(kbId);
            if (kb == null) return;

            // Count completed documents
            Long docCount = documentMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeDocument>()
                            .eq(KnowledgeDocument::getKnowledgeBaseId, kbId)
                            .eq(KnowledgeDocument::getDocumentStatus, "COMPLETED"));
            kb.setDocumentCount(docCount.intValue());

            // Sum chunk counts from completed documents
            var docs = documentMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeDocument>()
                            .eq(KnowledgeDocument::getKnowledgeBaseId, kbId)
                            .eq(KnowledgeDocument::getDocumentStatus, "COMPLETED"));
            int totalChunks = docs.stream()
                    .mapToInt(d -> d.getChunkCount() != null ? d.getChunkCount() : 0)
                    .sum();
            kb.setChunkCount(totalChunks);

            kbMapper.updateById(kb);
        } catch (Exception e) {
            log.error("Failed to update KB stats for kbId: {}", kbId, e);
        }
    }
}
