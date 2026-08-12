package com.epgis.sggis.agent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class KnowledgeServiceTest {

    @Test
    void addsDocumentWithMetadataAndTimestamp() {
        VectorStore vectorStore = mock(VectorStore.class);
        KnowledgeService service = new KnowledgeService(vectorStore);

        KnowledgeDocument saved = service.add("Spring AI 支持 RAG", Map.of("source", "manual"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        Document document = captor.getValue().getFirst();
        assertThat(document.getText()).isEqualTo("Spring AI 支持 RAG");
        assertThat(document.getMetadata()).containsEntry("source", "manual").containsKey("createdAt");
        assertThat(saved.id()).isEqualTo(document.getId());
    }

    @Test
    void searchesWithRequestedLimits() {
        VectorStore vectorStore = mock(VectorStore.class);
        Document match = Document.builder().id("doc-1").text("命中的知识").score(0.92).build();
        when(vectorStore.similaritySearch(org.mockito.ArgumentMatchers.any(SearchRequest.class)))
                .thenReturn(List.of(match));

        List<KnowledgeDocument> results = new KnowledgeService(vectorStore).search("问题", 3, 0.7);

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.id()).isEqualTo("doc-1");
            assertThat(result.content()).isEqualTo("命中的知识");
            assertThat(result.score()).isEqualTo(0.92);
        });
    }

    @Test
    void hidesStorageFailureBehindSafeMessage() {
        VectorStore vectorStore = mock(VectorStore.class);
        doThrow(new IllegalStateException("database secret")).when(vectorStore).add(anyList());

        assertThatThrownBy(() -> new KnowledgeService(vectorStore).add("内容", Map.of()))
                .isInstanceOf(KnowledgeServiceException.class)
                .hasMessageNotContaining("database secret")
                .hasMessageContaining("知识入库失败");
    }
}
