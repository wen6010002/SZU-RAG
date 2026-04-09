# SZU-RAG MVP — Phase 3 执行总结报告

**执行日期**: 2026-04-08
**状态**: 已完成

## 完成清单

- [x] **MilvusVectorStoreService** — 集合创建/向量插入/删除/相似搜索（适配SDK v2.4.8 API）
- [x] **文档解析器** — TikaDocumentParser(PDF/Office) + MarkdownDocumentParser + DocumentParserSelector
- [x] **分块策略** — ChunkingStrategy接口 + FixedSizeChunker + StructureAwareChunker + ChunkingStrategyFactory
- [x] **入库Pipeline** — IngestionEngine(4节点: Fetch→Parse→Chunk→Index) + IngestionContext
- [x] **知识库CRUD** — KnowledgeBase/Document/Chunk实体 + Mapper + KnowledgeService + KnowledgeController
- [x] 编译通过

## 新增文件 (18个)
```
rag/vector/VectorStoreService.java, MilvusVectorStoreService.java
ingestion/parser/DocumentParser.java, TikaDocumentParser.java, MarkdownDocumentParser.java, DocumentParserSelector.java
ingestion/chunker/ChunkingStrategy.java, FixedSizeChunker.java, StructureAwareChunker.java, ChunkingStrategyFactory.java
ingestion/pipeline/IngestionContext.java, IngestionEngine.java
knowledge/model/entity/KnowledgeBase.java, KnowledgeDocument.java, DocumentChunk.java
knowledge/mapper/KnowledgeBaseMapper.java, KnowledgeDocumentMapper.java, DocumentChunkMapper.java
knowledge/service/KnowledgeService.java, controller/KnowledgeController.java
```

## 修复的问题
- Milvus SDK v2.4.8: `AddFieldReq.builder().fieldName()` 替代 `FieldSchema`, `CollectionSchema.builder()` 替代 `newBuilder()`
- InsertReq 接受 Gson `JsonObject`, SearchReq.data 接受 `FloatVec`

## 下一步: Phase 4 — RAG核心-对话引擎
