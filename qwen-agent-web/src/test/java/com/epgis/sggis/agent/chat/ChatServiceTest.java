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
            public String call(String conversationId, String message) {
                arguments.set(conversationId + ":" + message);
                return "回答";
            }
        };

        ChatService service = new ChatService(client);

        assertThat(service.chat("conversation-1", "你好")).isEqualTo("回答");
        assertThat(arguments).hasValue("conversation-1:你好");
    }

    @Test
    void streamsAllFragments() {
        AgentClient client = new StubAgentClient() {
            @Override
            public Flux<String> stream(String conversationId, String message) {
                return Flux.just("你", "好");
            }
        };

        StepVerifier.create(new ChatService(client).stream("conversation-1", "你好"))
                .expectNext("你", "好")
                .verifyComplete();
    }

    @Test
    void convertsProviderFailureToSafeException() {
        AgentClient client = new StubAgentClient() {
            @Override
            public String call(String conversationId, String message) {
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
        public String call(String conversationId, String message) {
            return "";
        }

        @Override
        public Flux<String> stream(String conversationId, String message) {
            return Flux.empty();
        }
    }
}
