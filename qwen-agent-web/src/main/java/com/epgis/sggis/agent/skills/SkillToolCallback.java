package com.epgis.sggis.agent.skills;

import java.util.Map;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.DefaultToolMetadata;
import org.springframework.ai.tool.metadata.ToolMetadata;
import tools.jackson.databind.ObjectMapper;

public final class SkillToolCallback implements ToolCallback {

    private static final ToolDefinition DEFINITION = ToolDefinition.builder()
            .name("loadSkill")
            .description("加载一个已发现 Agent Skill 的完整 SKILL.md 指令。仅在需要使用该技能时调用。")
            .inputSchema("""
                    {"type":"object","properties":{"name":{"type":"string","description":"技能名称"}},"required":["name"],"additionalProperties":false}
                    """)
            .build();

    private final SkillCatalog skillCatalog;
    private final ObjectMapper objectMapper;

    public SkillToolCallback(SkillCatalog skillCatalog, ObjectMapper objectMapper) {
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
            Object name = input.get("name");
            return name instanceof String skillName ? skillCatalog.load(skillName) : "未找到指定技能。";
        }
        catch (Exception exception) {
            return "技能调用参数无效。";
        }
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return call(toolInput);
    }
}
