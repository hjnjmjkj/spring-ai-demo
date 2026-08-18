package com.epgis.sggis.agent.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class ChatServiceTest {

    @Test
    void passesConversationAndMessageToAgent() {
        AtomicReference<String> arguments = new AtomicReference<>();
        AgentClient client = new StubAgentClient() {
            @Override
            public AgentReply call(String conversationId, String message) {
                arguments.set(conversationId + ":" + message);
                return new AgentReply("回答", null);
            }
        };

        ChatService service = new ChatService(client);

        assertThat(service.chat("conversation-1", "你好").content()).isEqualTo("回答");
        assertThat(arguments).hasValue("conversation-1:你好");
    }

    @Test
    void streamsAllReplies() {
        AgentClient client = new StubAgentClient() {
            @Override
            public Flux<AgentReply> stream(String conversationId, String message) {
                return Flux.just(new AgentReply("你", null), new AgentReply("好", null));
            }
        };

        StepVerifier.create(new ChatService(client).stream("conversation-1", "你好"))
                .expectNextMatches(reply -> "你".equals(reply.content()))
                .expectNextMatches(reply -> "好".equals(reply.content()))
                .verifyComplete();
    }

    @Test
    void convertsProviderFailureToSafeException() {
        AgentClient client = new StubAgentClient() {
            @Override
            public AgentReply call(String conversationId, String message) {
                throw new IllegalStateException("secret provider details");
            }
        };

        assertThatThrownBy(() -> new ChatService(client).chat("conversation-1", "你好"))
                .isInstanceOf(ChatServiceException.class)
                .hasMessageNotContaining("secret provider details")
                .hasMessageContaining("暂时无法完成请求");
    }

    private static class StubAgentClient implements AgentClient {
        @Override
        public AgentReply call(String conversationId, String message) {
            return new AgentReply("", null);
        }

        @Override
        public Flux<AgentReply> stream(String conversationId, String message) {
            return Flux.empty();
        }
    }
}
