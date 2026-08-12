package com.epgis.sggis.agent.web;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epgis.sggis.agent.knowledge.KnowledgeDocument;
import com.epgis.sggis.agent.knowledge.KnowledgeService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class KnowledgeControllerTest {

    private KnowledgeService knowledgeService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        knowledgeService = mock(KnowledgeService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new KnowledgeController(knowledgeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void addsKnowledgeDocument() throws Exception {
        when(knowledgeService.add(org.mockito.ArgumentMatchers.eq("内容"), anyMap()))
                .thenReturn(new KnowledgeDocument("doc-1", "内容", Map.of("source", "manual"), null));

        mockMvc.perform(post("/api/knowledge/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"内容\",\"metadata\":{\"source\":\"manual\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("doc-1"))
                .andExpect(jsonPath("$.content").value("内容"));
    }

    @Test
    void rejectsBlankKnowledge() throws Exception {
        mockMvc.perform(post("/api/knowledge/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("知识内容不能为空"));
    }

    @Test
    void usesSearchDefaults() throws Exception {
        when(knowledgeService.search("问题", 5, 0.65)).thenReturn(List.of());

        mockMvc.perform(post("/api/knowledge/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"问题\"}"))
                .andExpect(status().isOk());

        verify(knowledgeService).search("问题", 5, 0.65);
    }

    @Test
    void deletesKnowledgeDocument() throws Exception {
        mockMvc.perform(delete("/api/knowledge/documents/doc-1"))
                .andExpect(status().isNoContent());

        verify(knowledgeService).delete("doc-1");
    }
}
