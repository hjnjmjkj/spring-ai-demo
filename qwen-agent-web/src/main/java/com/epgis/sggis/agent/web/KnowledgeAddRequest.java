package com.epgis.sggis.agent.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record KnowledgeAddRequest(
        @NotBlank(message = "知识内容不能为空")
        @Size(max = 20000, message = "知识内容不能超过 20000 个字符")
        String content,
        Map<String, Object> metadata) {
}
