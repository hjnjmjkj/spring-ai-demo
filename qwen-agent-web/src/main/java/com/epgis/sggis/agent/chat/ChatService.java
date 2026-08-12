package com.epgis.sggis.agent.chat;

import java.util.Objects;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class ChatService {

    private static final String FAILURE_MESSAGE = "智能助手暂时无法完成请求，请确认 Qwen 和 MCP Server 均可用后重试。";

    private final AgentClient agentClient;

    ChatService(AgentClient agentClient) {
        this.agentClient = agentClient;
    }

    public String chat(String conversationId, String message) {
        try {
            String content = agentClient.call(conversationId, message);
            return Objects.requireNonNullElse(content, "");
        }
        catch (RuntimeException exception) {
            throw new ChatServiceException(FAILURE_MESSAGE, exception);
        }
    }

    public Flux<String> stream(String conversationId, String message) {
        try {
            return agentClient.stream(conversationId, message)
                    .filter(Objects::nonNull)
                    .onErrorMap(exception -> new ChatServiceException(FAILURE_MESSAGE, exception));
        }
        catch (RuntimeException exception) {
            return Flux.error(new ChatServiceException(FAILURE_MESSAGE, exception));
        }
    }
}
