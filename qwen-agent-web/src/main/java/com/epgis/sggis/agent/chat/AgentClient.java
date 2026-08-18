package com.epgis.sggis.agent.chat;

import reactor.core.publisher.Flux;

interface AgentClient {

    AgentReply call(String conversationId, String message);

    Flux<AgentReply> stream(String conversationId, String message);
}
