package com.epgis.sggis.agent.web;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.epgis.sggis.agent.chat.AgentReply;
import com.epgis.sggis.agent.chat.ChatService;
import com.epgis.sggis.agent.chat.McpToolResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import reactor.core.publisher.Flux;

class ChatControllerTest {

    private static final String CONVERSATION_ID = "7f3ad908-937f-4d5f-b4d1-fd5aef7d44a1";

    private ChatService chatService;
    private MockMvc mockMvc;
    @BeforeEach
    void setUp() {
        chatService = mock(ChatService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new ChatController(chatService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void returnsSynchronousStructuredResponse() throws Exception {
        McpToolResult toolResult = toolResult();
        when(chatService.chat(CONVERSATION_ID, "你好"))
                .thenReturn(new AgentReply("你好！", List.of(toolResult)));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest("你好")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(CONVERSATION_ID))
                .andExpect(jsonPath("$.content").value("你好！"))
                .andExpect(jsonPath("$.toolResults[0].toolName").value("locateEquipment"))
                .andExpect(jsonPath("$.toolResults[0].arguments.keyword").value("中闽线"))
                .andExpect(jsonPath("$.toolResults[0].result.status").value("COMPLETED"))
                .andExpect(jsonPath("$.toolResults[0].result.action").value("LOCATE_EQUIPMENT"));
    }

    @Test
    void rejectsBlankMessage() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest("")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("message 不能为空"));
    }

    @Test
    void rejectsInvalidConversationId() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"not-a-uuid\",\"message\":\"你好\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("conversationId 必须是有效的 UUID"));
    }

    @Test
    void streamsToolTokenAndDoneEvents() throws Exception {
        when(chatService.stream(anyString(), anyString()))
                .thenReturn(Flux.just(new AgentReply("你好", List.of(toolResult()))));

        MvcResult pending = mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(jsonRequest("你好")))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:tool")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("locateEquipment")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:token")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:done")));
    }

    private McpToolResult toolResult() {
        return new McpToolResult(
                "locateEquipment",
                Map.of("keyword", "中闽线"),
                Map.of(
                        "status", "COMPLETED",
                        "action", "LOCATE_EQUIPMENT",
                        "actionParams", Map.of("highlight", true)));
    }

    private String jsonRequest(String message) {
        return "{\"conversationId\":\"" + CONVERSATION_ID + "\",\"message\":\"" + message + "\"}";
    }
}
