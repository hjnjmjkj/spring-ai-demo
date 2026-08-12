package com.epgis.sggis.agent.knowledge;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeService {

    private final VectorStore vectorStore;

    public KnowledgeService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public KnowledgeDocument add(String content, Map<String, Object> metadata) {
        try {
            Map<String, Object> safeMetadata = new LinkedHashMap<>();
            if (metadata != null) {
                safeMetadata.putAll(metadata);
            }
            safeMetadata.put("createdAt", Instant.now().toString());
            Document document = Document.builder()
                    .text(content)
                    .metadata(safeMetadata)
                    .build();
            vectorStore.add(List.of(document));
            return KnowledgeDocument.from(document);
        }
        catch (RuntimeException exception) {
            throw new KnowledgeServiceException("知识入库失败，请检查向量模型和 PostgreSQL 连接", exception);
        }
    }

    public List<KnowledgeDocument> search(String query, int topK, double similarityThreshold) {
        try {
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .similarityThreshold(similarityThreshold)
                    .build();
            return vectorStore.similaritySearch(request).stream()
                    .map(KnowledgeDocument::from)
                    .toList();
        }
        catch (RuntimeException exception) {
            throw new KnowledgeServiceException("知识检索失败，请检查向量模型和 PostgreSQL 连接", exception);
        }
    }

    public void delete(String id) {
        try {
            vectorStore.delete(List.of(id));
        }
        catch (RuntimeException exception) {
            throw new KnowledgeServiceException("删除知识失败", exception);
        }
    }
}
