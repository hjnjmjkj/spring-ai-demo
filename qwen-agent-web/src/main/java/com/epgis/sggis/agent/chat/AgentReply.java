package com.epgis.sggis.agent.chat;

import java.util.List;

public record AgentReply(String content, List<McpToolResult> toolResults) {

    public AgentReply {
        content = content == null ? "" : content;
        toolResults = toolResults == null ? List.of() : List.copyOf(toolResults);
    }
}
