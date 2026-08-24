package com.epgis.sggis.agent.knowledge;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class KnowledgeService {

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String qualifiedTableName;

    public KnowledgeService(
            VectorStore vectorStore,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${spring.ai.vectorstore.pgvector.schema-name:public}") String schemaName,
            @Value("${spring.ai.vectorstore.pgvector.table-name:vector_store}") String tableName) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.qualifiedTableName = quoteIdentifier(schemaName) + "." + quoteIdentifier(tableName);
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

    public KnowledgePage list(String keyword, int page, int size) {
        try {
            String normalizedKeyword = keyword == null ? "" : keyword.trim();
            String filter = normalizedKeyword.isEmpty()
                    ? ""
                    : " WHERE content ILIKE ? OR COALESCE(metadata->>'source', '') ILIKE ?";
            Object[] filterArguments = normalizedKeyword.isEmpty()
                    ? new Object[0]
                    : new Object[] { "%" + normalizedKeyword + "%", "%" + normalizedKeyword + "%" };

            Long total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + qualifiedTableName + filter,
                    Long.class,
                    filterArguments);

            List<Object> arguments = new java.util.ArrayList<>(List.of(filterArguments));
            arguments.add(size);
            arguments.add((long) page * size);
            List<KnowledgeDocument> items = jdbcTemplate.query(
                    "SELECT id::text AS id, content, metadata::text AS metadata FROM " + qualifiedTableName
                            + filter
                            + " ORDER BY COALESCE(metadata->>'updatedAt', metadata->>'createdAt') DESC NULLS LAST, id DESC"
                            + " LIMIT ? OFFSET ?",
                    (resultSet, rowNumber) -> new KnowledgeDocument(
                            resultSet.getString("id"),
                            resultSet.getString("content"),
                            readMetadata(resultSet.getString("metadata")),
                            null),
                    arguments.toArray());
            return KnowledgePage.of(items, page, size, total == null ? 0 : total);
        }
        catch (RuntimeException exception) {
            throw new KnowledgeServiceException("读取知识库失败，请检查 PostgreSQL 连接", exception);
        }
    }

    public Optional<KnowledgeDocument> find(String id) {
        try {
            return jdbcTemplate.query(
                            "SELECT id::text AS id, content, metadata::text AS metadata FROM "
                                    + qualifiedTableName + " WHERE id::text = ?",
                            (resultSet, rowNumber) -> new KnowledgeDocument(
                                    resultSet.getString("id"),
                                    resultSet.getString("content"),
                                    readMetadata(resultSet.getString("metadata")),
                                    null),
                            id)
                    .stream()
                    .findFirst();
        }
        catch (RuntimeException exception) {
            throw new KnowledgeServiceException("读取知识详情失败", exception);
        }
    }

    public Optional<KnowledgeDocument> update(String id, String content, Map<String, Object> metadata) {
        Optional<KnowledgeDocument> existing = find(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        try {
            Map<String, Object> mergedMetadata = new LinkedHashMap<>(existing.get().metadata());
            if (metadata != null) {
                metadata.forEach((key, value) -> {
                    if (value == null || (value instanceof String text && text.isBlank())) {
                        mergedMetadata.remove(key);
                    }
                    else {
                        mergedMetadata.put(key, value);
                    }
                });
            }
            mergedMetadata.putIfAbsent("createdAt", Instant.now().toString());
            mergedMetadata.put("updatedAt", Instant.now().toString());
            Document document = Document.builder()
                    .id(id)
                    .text(content)
                    .metadata(mergedMetadata)
                    .build();
            vectorStore.add(List.of(document));
            return Optional.of(KnowledgeDocument.from(document));
        }
        catch (RuntimeException exception) {
            throw new KnowledgeServiceException("更新知识失败，请检查向量模型和 PostgreSQL 连接", exception);
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

    private Map<String, Object> readMetadata(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        }
        catch (Exception exception) {
            throw new IllegalStateException("Invalid knowledge metadata", exception);
        }
    }

    private static String quoteIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid PostgreSQL identifier");
        }
        return '"' + identifier + '"';
    }
}
