package com.szu.rag.ingestion.pipeline;

import com.szu.rag.infra.embedding.EmbeddingClient;
import com.szu.rag.ingestion.chunker.ChunkingStrategy;
import com.szu.rag.ingestion.chunker.ChunkingStrategyFactory;
import com.szu.rag.ingestion.parser.DocumentParserSelector;
import com.szu.rag.rag.vector.VectorStoreService;
import com.szu.rag.knowledge.model.entity.KnowledgeDocument;
import com.szu.rag.knowledge.model.entity.DocumentChunk;
import com.szu.rag.knowledge.mapper.KnowledgeDocumentMapper;
import com.szu.rag.knowledge.mapper.KnowledgeBaseMapper;
import com.szu.rag.knowledge.mapper.DocumentChunkMapper;
import com.szu.rag.knowledge.model.entity.KnowledgeBase;
import com.szu.rag.framework.id.SnowflakeIdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionEngine {

    private final DocumentParserSelector parserSelector;
    private final ChunkingStrategyFactory chunkingFactory;
    private final EmbeddingClient embeddingClient;
    private final VectorStoreService vectorStoreService;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeBaseMapper kbMapper;
    private final DocumentChunkMapper chunkMapper;
    private final SnowflakeIdWorker idWorker;

    public void execute(IngestionContext context) {
        try {
            // Step 1: Fetch
            context.setStatus("FETCHING");
            byte[] fileContent = Files.readAllBytes(Path.of(context.getFilePath()));
            log.info("Fetched file: {}, size: {} bytes", context.getFileName(), fileContent.length);

            // Step 2: Parse
            context.setStatus("PARSING");
            updateDocumentStatus(context.getDocumentId(), "PARSING", null);
            String parsedText = parserSelector.select(context.getMimeType()).parse(fileContent, context.getMimeType());
            context.setRawText(parsedText);
            log.info("Parsed document: {} chars", parsedText.length());

            // Step 3: Chunk
            context.setStatus("CHUNKING");
            updateDocumentStatus(context.getDocumentId(), "CHUNKING", null);
            ChunkingStrategy chunker = chunkingFactory.getDefault();
            List<String> chunks = chunker.chunk(parsedText, 500, 50);
            context.setChunks(chunks);
            log.info("Chunked into {} pieces", chunks.size());

            // Step 4: Index
            context.setStatus("INDEXING");
            updateDocumentStatus(context.getDocumentId(), "INDEXING", null);
            indexChunks(context);

            context.setStatus("COMPLETED");
            updateDocumentStatus(context.getDocumentId(), "COMPLETED", null);

            // Write back chunk count to document record
            KnowledgeDocument completedDoc = documentMapper.selectById(context.getDocumentId());
            if (completedDoc != null) {
                completedDoc.setChunkCount(context.getChunks().size());
                documentMapper.updateById(completedDoc);
            }

            log.info("Ingestion completed for document: {}", context.getDocumentId());

        } catch (Exception e) {
            log.error("Ingestion failed for document: {}", context.getDocumentId(), e);
            context.setStatus("FAILED");
            context.setErrorMessage(e.getMessage());
            updateDocumentStatus(context.getDocumentId(), "FAILED", e.getMessage());
        }
    }

    private void indexChunks(IngestionContext context) {
        // Ensure collection exists (auto-create if lost, e.g. after Milvus restart)
        String collectionName = getCollectionName(context.getKnowledgeBaseId());
        KnowledgeBase kb = kbMapper.selectById(context.getKnowledgeBaseId());
        int dimension = kb != null ? kb.getEmbeddingDim() : 1024;
        vectorStoreService.createCollection(collectionName, dimension);

        List<String> chunks = context.getChunks();
        List<Long> milvusIds = new ArrayList<>();
        List<Long> chunkIds = new ArrayList<>();

        int batchSize = 20;
        for (int i = 0; i < chunks.size(); i += batchSize) {
            List<String> batch = chunks.subList(i, Math.min(i + batchSize, chunks.size()));
            List<List<Float>> vectors = embeddingClient.embedBatch(batch);

            List<Long> batchMilvusIds = new ArrayList<>();
            List<Map<String, Object>> metadataList = new ArrayList<>();

            for (int j = 0; j < batch.size(); j++) {
                int chunkIndex = i + j;
                long chunkId = idWorker.nextId();
                long milvusId = idWorker.nextId();

                chunkIds.add(chunkId);
                batchMilvusIds.add(milvusId);

                DocumentChunk chunkEntity = new DocumentChunk();
                chunkEntity.setId(chunkId);
                chunkEntity.setDocumentId(context.getDocumentId());
                chunkEntity.setKnowledgeBaseId(context.getKnowledgeBaseId());
                chunkEntity.setChunkIndex(chunkIndex);
                chunkEntity.setChunkText(batch.get(j));
                chunkEntity.setCharCount(batch.get(j).length());
                chunkEntity.setSourceTitle(context.getFileName());
                chunkEntity.setSourceUrl(context.getSourceUrl());
                chunkEntity.setMilvusId(milvusId);
                chunkMapper.insert(chunkEntity);

                Map<String, Object> meta = new HashMap<>();
                meta.put("document_id", context.getDocumentId());
                meta.put("chunk_index", chunkIndex);
                meta.put("chunk_text", batch.get(j));
                meta.put("source_title", context.getFileName());
                meta.put("source_url", context.getSourceUrl() != null ? context.getSourceUrl() : "");
                metadataList.add(meta);
            }

            vectorStoreService.insert(collectionName, batchMilvusIds, vectors, metadataList);
            milvusIds.addAll(batchMilvusIds);
        }

        context.setChunkIds(chunkIds);
        context.setMilvusIds(milvusIds);
    }

    private String getCollectionName(Long kbId) {
        return "szu_rag_kb_" + kbId;
    }

    private void updateDocumentStatus(Long docId, String status, String errorMsg) {
        KnowledgeDocument doc = documentMapper.selectById(docId);
        if (doc != null) {
            doc.setDocumentStatus(status);
            doc.setErrorMessage(errorMsg != null ? errorMsg : "");
            documentMapper.updateById(doc);
        }
    }
}
