package com.epgis.sggis.mcp.scene2;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/** 场景四的接口级 Tool 版本；当前未注册到 MCP。 */
@Service
public class ConstructionProjectTools {

    public static final String PROJECT_LIST_ENDPOINT = "/epgis3d/swgis/build/getBuildServiceList";

    private static final List<ProjectInfo> PROJECTS = List.of(
            new ProjectInfo("BUILD-2026-001", "厦门同安输变电工程", "CONSTRUCTION", "同安区", List.of(
                    new ModelService("MODEL-001-A", "变电站主体模型", "3DTILES",
                            "/models/build-2026-001/station/tileset.json"),
                    new ModelService("MODEL-001-B", "线路走廊模型", "3DTILES",
                            "/models/build-2026-001/line/tileset.json"))),
            new ProjectInfo("BUILD-2026-002", "厦门翔安变电站扩建工程", "PLANNING", "翔安区", List.of(
                    new ModelService("MODEL-002-A", "扩建区模型", "3DTILES",
                            "/models/build-2026-002/tileset.json"))),
            new ProjectInfo("BUILD-2025-018", "同安配网改造工程", "COMPLETED", "同安区", List.of(
                    new ModelService("MODEL-018-A", "配网改造模型", "BIM",
                            "/models/build-2025-018/model.json"))));

    @Tool(description = """
            scene2 场景四唯一接口，对应 /epgis3d/swgis/build/getBuildServiceList。
            按工程名称或ID查询工程信息和模型服务列表；唯一匹配时返回SHOW_CONSTRUCTION_PROJECT action，
            并指定第一个模型为默认加载模型。
            """)
    public ProjectListResult queryConstructionProjects(
            @ToolParam(description = "工程名称或工程ID") String projectNameOrId) {
        if (projectNameOrId == null || projectNameOrId.isBlank()) {
            throw new IllegalArgumentException("工程名称或ID不能为空");
        }
        String requested = projectNameOrId.trim();
        String normalized = requested.toLowerCase(Locale.ROOT);
        List<ProjectInfo> projects = PROJECTS.stream()
                .filter(item -> item.projectId().equalsIgnoreCase(normalized)
                        || item.projectName().toLowerCase(Locale.ROOT).contains(normalized))
                .toList();
        String action = projects.size() == 1 ? "SHOW_CONSTRUCTION_PROJECT" : null;
        ProjectActionParams params = null;
        if (projects.size() == 1) {
            ProjectInfo selected = projects.getFirst();
            ModelService defaultModel = selected.models().isEmpty() ? null : selected.models().getFirst();
            params = new ProjectActionParams(selected, defaultModel, true);
        }
        String message = switch (projects.size()) {
            case 0 -> "没有找到匹配工程。";
            case 1 -> "已找到唯一工程，默认加载第一个模型。";
            default -> "匹配到多个工程，请让用户确认后使用工程ID再次调用。";
        };
        return new ProjectListResult(PROJECT_LIST_ENDPOINT,
                Map.of("keyword", requested, "pageNum", 1, "pageSize", 20),
                projects, action, params, message);
    }

    public record ModelService(String modelId, String modelName, String modelType, String serviceUrl) { }
    public record ProjectInfo(String projectId, String projectName, String status,
            String region, List<ModelService> models) { }
    public record ProjectActionParams(ProjectInfo project, ModelService defaultModel,
            boolean autoLoadDefaultModel) { }
    public record ProjectListResult(String endpoint, Map<String, Object> request, List<ProjectInfo> projects,
            String action, ProjectActionParams actionParams, String message) { }
}
