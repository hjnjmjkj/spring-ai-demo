package com.epgis.sggis.agent.web;

import com.epgis.sggis.agent.chat.AgentReply;
import com.epgis.sggis.agent.chat.ChatService;
import com.epgis.sggis.agent.chat.ChatServiceException;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        AgentReply reply = chatService.chat(request.conversationId(), request.message());
        return ResponseEntity.ok(new ChatResponse(
                request.conversationId(), reply.content(), reply.toolResults()));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> stream(@Valid @RequestBody ChatRequest request) {
        Flux<ServerSentEvent<Object>> events = chatService
                .stream(request.conversationId(), request.message())
                .concatMap(reply -> Flux.concat(
                        Flux.fromIterable(reply.toolResults()).map(result -> event("tool", result)),
                        Flux.just(event("token", new ChatStreamEvent(reply.content())))));

        return events
                .concatWithValues(event("done", new ChatStreamEvent("")))
                .onErrorResume(ChatServiceException.class,
                        exception -> Flux.just(event("error", new ChatStreamEvent(exception.getMessage()))));
    }

    private ServerSentEvent<Object> event(String name, Object data) {
        return ServerSentEvent.builder(data)
                .event(name)
                .build();
    }
}
