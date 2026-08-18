package com.epgis.sggis.agent.web;

import com.epgis.sggis.agent.chat.McpToolResult;
import java.util.List;

public record ChatResponse(String conversationId, String content, List<McpToolResult> toolResults) {
}
