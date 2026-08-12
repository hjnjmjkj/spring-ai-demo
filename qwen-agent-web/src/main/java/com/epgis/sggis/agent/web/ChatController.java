package com.epgis.sggis.agent.web;

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
        String content = chatService.chat(request.conversationId(), request.message());
        return ResponseEntity.ok(new ChatResponse(request.conversationId(), content));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatStreamEvent>> stream(@Valid @RequestBody ChatRequest request) {
        Flux<ServerSentEvent<ChatStreamEvent>> tokens = chatService
                .stream(request.conversationId(), request.message())
                .map(token -> event("token", token));

        return tokens
                .concatWithValues(event("done", ""))
                .onErrorResume(ChatServiceException.class,
                        exception -> Flux.just(event("error", exception.getMessage())));
    }

    private ServerSentEvent<ChatStreamEvent> event(String name, String content) {
        return ServerSentEvent.<ChatStreamEvent>builder()
                .event(name)
                .data(new ChatStreamEvent(content))
                .build();
    }
}
