package com.epgis.sggis.agent.knowledge;

import java.util.List;

public record KnowledgePage(
        List<KnowledgeDocument> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static KnowledgePage of(List<KnowledgeDocument> items, int page, int size, long totalElements) {
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new KnowledgePage(items, page, size, totalElements, totalPages);
    }
}
