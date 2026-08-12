package com.epgis.sggis.agent.knowledge;

import java.util.Map;
import org.springframework.ai.document.Document;

public record KnowledgeDocument(String id, String content, Map<String, Object> metadata, Double score) {

    static KnowledgeDocument from(Document document) {
        return new KnowledgeDocument(
                document.getId(),
                document.getText(),
                document.getMetadata(),
                document.getScore());
    }
}
