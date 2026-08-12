package com.epgis.sggis.agent.web;

import com.epgis.sggis.agent.knowledge.KnowledgeDocument;
import com.epgis.sggis.agent.knowledge.KnowledgeService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge")
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
