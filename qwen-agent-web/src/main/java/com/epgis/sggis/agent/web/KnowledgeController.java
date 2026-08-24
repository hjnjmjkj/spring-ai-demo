package com.epgis.sggis.agent.web;

import com.epgis.sggis.agent.knowledge.KnowledgeDocument;
import com.epgis.sggis.agent.knowledge.KnowledgePage;
import com.epgis.sggis.agent.knowledge.KnowledgeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/knowledge")
@Validated
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.CREATED)
    KnowledgeDocument add(@Valid @RequestBody KnowledgeAddRequest request) {
        return knowledgeService.add(request.content(), request.metadata());
    }

    @GetMapping("/documents")
    KnowledgePage list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "") String keyword) {
        return knowledgeService.list(keyword, page, size);
    }

    @GetMapping("/documents/{id}")
    KnowledgeDocument get(@PathVariable String id) {
        return knowledgeService.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "知识不存在"));
    }

    @PutMapping("/documents/{id}")
    KnowledgeDocument update(@PathVariable String id, @Valid @RequestBody KnowledgeAddRequest request) {
        return knowledgeService.update(id, request.content(), request.metadata())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "知识不存在"));
    }

    @PostMapping("/search")
    List<KnowledgeDocument> search(@Valid @RequestBody KnowledgeSearchRequest request) {
        return knowledgeService.search(
                request.query(), request.effectiveTopK(), request.effectiveSimilarityThreshold());
    }

    @DeleteMapping("/documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String id) {
        knowledgeService.delete(id);
    }
}
