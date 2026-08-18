package com.epgis.sggis.agent.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;

@Component
final class SpringAiAgentClient implements AgentClient {

    private final ChatClient chatClient;
    private final ToolCallbackProvider mcpTools;
    private final ObjectMapper objectMapper;

    SpringAiAgentClient(ChatClient chatClient, ToolCallbackProvider mcpTools, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.mcpTools = mcpTools;
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentReply call(String conversationId, String message) {
        List<McpToolResult> toolResults = new ArrayList<>();
        ToolCallbackProvider recordingTools = recordingTools(toolResults);
        String content = chatClient.prompt()
                .user(message)
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                .tools(recordingTools)
                .call()
                .content();
        return new AgentReply(content, toolResults);
    }

    @Override
    public Flux<AgentReply> stream(String conversationId, String message) {
        // Spring AI 2.0.0 cannot reliably merge tool-call stream chunks emitted by
        // some OpenAI-compatible providers, including Qwen. Keep the public SSE
        // contract, but execute the model/tool loop through the stable synchronous
        // path and emit the completed answer as a single token event.
        return Mono.fromCallable(() -> call(conversationId, message))
                .subscribeOn(Schedulers.boundedElastic())
                .flux();
    }

    private ToolCallbackProvider recordingTools(List<McpToolResult> results) {
        ToolCallback[] callbacks = mcpTools.getToolCallbacks();
        ToolCallback[] wrapped = new ToolCallback[callbacks.length];
        for (int index = 0; index < callbacks.length; index++) {
            wrapped[index] = new RecordingToolCallback(callbacks[index], results);
        }
        return ToolCallbackProvider.from(wrapped);
    }

    private Object json(String value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, Object.class);
        }
        catch (Exception ignored) {
            return value;
        }
    }

    private Object mcpResult(String output) {
        Object parsed = json(output);
        if (parsed instanceof List<?> content && content.size() == 1
                && content.getFirst() instanceof Map<?, ?> block
                && block.get("text") instanceof String text) {
            return json(text);
        }
        return parsed;
    }

    private final class RecordingToolCallback implements ToolCallback {

        private final ToolCallback delegate;
        private final List<McpToolResult> results;

        private RecordingToolCallback(ToolCallback delegate, List<McpToolResult> results) {
            this.delegate = delegate;
            this.results = results;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return delegate.getToolMetadata();
        }

        @Override
        public String call(String toolInput) {
            String output = delegate.call(toolInput);
            record(toolInput, output);
            return output;
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            String output = delegate.call(toolInput, toolContext);
            record(toolInput, output);
            return output;
        }

        private void record(String input, String output) {
            results.add(new McpToolResult(getToolDefinition().name(), json(input), mcpResult(output)));
        }
    }
}
