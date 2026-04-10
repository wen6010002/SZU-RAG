package com.szu.rag.knowledge.controller;

import com.szu.rag.framework.result.Result;
import com.szu.rag.knowledge.model.entity.KnowledgeBase;
import com.szu.rag.knowledge.model.entity.KnowledgeDocument;
import com.szu.rag.knowledge.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    // ========== Knowledge Base ==========

    @PostMapping("/bases")
    public Result<KnowledgeBase> createBase(@RequestBody CreateBaseRequest req) {
        return Result.success(knowledgeService.createKnowledgeBase(req.getName(), req.getDescription()));
    }

    @GetMapping("/bases")
    public Result<List<KnowledgeBase>> listBases() {
        return Result.success(knowledgeService.listKnowledgeBases());
    }

    @GetMapping("/bases/{id}")
    public Result<KnowledgeBase> getBase(@PathVariable Long id) {
        return Result.success(knowledgeService.listKnowledgeBases().stream()
                .filter(kb -> kb.getId().equals(id)).findFirst().orElse(null));
    }

    // ========== Document Upload ==========

    @PostMapping("/documents/upload")
    public Result<KnowledgeDocument> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("knowledgeBaseId") Long kbId,
            @RequestParam(value = "sourceUrl", required = false) String sourceUrl) {
        return Result.success(knowledgeService.uploadDocument(kbId, file, sourceUrl));
    }

    @PostMapping("/documents/upload/batch")
    public Result<List<KnowledgeDocument>> uploadDocumentsBatch(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("knowledgeBaseId") Long kbId) {
        return Result.success(knowledgeService.uploadDocumentsBatch(kbId, files));
    }

    // ========== Document Operations ==========

    @GetMapping("/bases/{kbId}/documents")
    public Result<List<KnowledgeDocument>> listDocuments(@PathVariable Long kbId) {
        return Result.success(knowledgeService.listDocuments(kbId));
    }

    @GetMapping("/documents/{docId}")
    public Result<KnowledgeDocument> getDocument(@PathVariable Long docId) {
        return Result.success(knowledgeService.getDocument(docId));
    }

    @DeleteMapping("/documents/{docId}")
    public Result<Void> deleteDocument(@PathVariable Long docId) {
        knowledgeService.deleteDocument(docId);
        return Result.success();
    }

    @PostMapping("/documents/{docId}/reprocess")
    public Result<KnowledgeDocument> reprocessDocument(@PathVariable Long docId) {
        return Result.success(knowledgeService.reprocessDocument(docId));
    }

    // ========== DTO ==========

    @lombok.Data
    public static class CreateBaseRequest {
        private String name;
        private String description;
    }
}
