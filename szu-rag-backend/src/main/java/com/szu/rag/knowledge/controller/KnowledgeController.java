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

    @PostMapping("/documents/upload")
    public Result<KnowledgeDocument> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("knowledgeBaseId") Long kbId,
            @RequestParam(value = "sourceUrl", required = false) String sourceUrl) {
        return Result.success(knowledgeService.uploadDocument(kbId, file, sourceUrl));
    }

    @GetMapping("/bases/{kbId}/documents")
    public Result<List<KnowledgeDocument>> listDocuments(@PathVariable Long kbId) {
        return Result.success(knowledgeService.listDocuments(kbId));
    }

    @lombok.Data
    public static class CreateBaseRequest {
        private String name;
        private String description;
    }
}
