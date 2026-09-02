package com.epgis.sggis.agent.skills;

import java.util.Map;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.DefaultToolMetadata;
import org.springframework.ai.tool.metadata.ToolMetadata;
import tools.jackson.databind.ObjectMapper;

public final class SkillResourceToolCallback implements ToolCallback {

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("loadSkillResource")
            .description("读取一个已加载 Agent Skill 目录内的参考文本资源。仅在 SKILL.md 指示需要该资源时调用。")
            .inputSchema("""
                    {"type":"object","properties":{"skillName":{"type":"string","description":"技能名称"},"path":{"type":"string","description":"技能目录内的相对文本文件路径"}},"required":["skillName","path"],"additionalProperties":false}
                    """)
            .build();

    private final SkillCatalog skillCatalog;
    private final ObjectMapper objectMapper;

    public SkillResourceToolCallback(SkillCatalog skillCatalog, ObjectMapper objectMapper) {
        this.skillCatalog = skillCatalog;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return DEFINITION;
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return DefaultToolMetadata.builder().build();
    }

    @Override
    public String call(String toolInput) {
        try {
            Map<?, ?> input = objectMapper.readValue(toolInput, Map.class);
            Object skillName = input.get("skillName");
            Object path = input.get("path");
            return skillName instanceof String name && path instanceof String resourcePath
                    ? skillCatalog.loadResource(name, resourcePath)
                    : "未找到指定技能资源。";
        }
        catch (Exception exception) {
            return "技能资源调用参数无效。";
        }
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return call(toolInput);
    }
}
