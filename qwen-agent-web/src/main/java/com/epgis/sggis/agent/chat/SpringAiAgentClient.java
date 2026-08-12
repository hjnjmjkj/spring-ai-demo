package com.epgis.sggis.agent.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
final class SpringAiAgentClient implements AgentClient {

    private final ChatClient chatClient;
    private final ToolCallbackProvider mcpTools;

    SpringAiAgentClient(ChatClient chatClient, ToolCallbackProvider mcpTools) {
        this.chatClient = chatClient;
        this.mcpTools = mcpTools;
    }

    @Override
    public String call(String conversationId, String message) {
        return chatClient.prompt()
                .user(message)
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                .tools(mcpTools)
                .call()
                .content();
    }

    @Override
    public Flux<String> stream(String conversationId, String message) {
        // Spring AI 2.0.0 cannot reliably merge tool-call stream chunks emitted by
        // some OpenAI-compatible providers, including Qwen. Keep the public SSE
        // contract, but execute the model/tool loop through the stable synchronous
        // path and emit the completed answer as a single token event.
        return Mono.fromCallable(() -> call(conversationId, message))
                .subscribeOn(Schedulers.boundedElastic())
                .flux();
    }
}
