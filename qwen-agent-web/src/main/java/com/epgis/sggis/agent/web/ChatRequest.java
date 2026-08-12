package com.epgis.sggis.agent.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank(message = "conversationId 不能为空")
        @Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
                message = "conversationId 必须是有效的 UUID")
        String conversationId,

        @NotBlank(message = "message 不能为空")
        @Size(max = 4000, message = "message 不能超过 4000 个字符")
        String message) {
}
