package com.epgis.sggis.agent.chat;

import reactor.core.publisher.Flux;

interface AgentClient {

    String call(String conversationId, String message);

    Flux<String> stream(String conversationId, String message);
}
