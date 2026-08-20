package com.epgis.sggis.mcp.scene;

import com.epgis.sggis.mcp.model.ToolResultStatus;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/** 场景四：查询基建工程及工程模型服务。 */
@Service
public class ConstructionProjectService {

    public static final String PROJECT_LIST_ENDPOINT = "/epgis3d/swgis/build/getBuildServiceList";
    public static final String ACTION = "SHOW_CONSTRUCTION_PROJECT";

    private static final List<ConstructionProject> PROJECTS = List.of(
            project("BUILD-2026-001", "厦门同安输变电工程", "CONSTRUCTION", "同安区",
                    model("MODEL-001-A", "变电站主体模型", "3DTILES", "/models/build-2026-001/station/tileset.json"),
                    model("MODEL-001-B", "线路走廊模型", "3DTILES", "/models/build-2026-001/line/tileset.json")),
            project("BUILD-2026-002", "厦门翔安变电站扩建工程", "PLANNING", "翔安区",
                    model("MODEL-002-A", "扩建区模型", "3DTILES", "/models/build-2026-002/tileset.json")),
            project("BUILD-2025-018", "同安配网改造工程", "COMPLETED", "同安区",
                    model("MODEL-018-A", "配网改造模型", "BIM", "/models/build-2025-018/model.json")));

    @Tool(description = """
            场景四基建工程浏览：按工程名称或工程ID查询工程信息和模型服务URL清单，
            返回 SHOW_CONSTRUCTION_PROJECT action。唯一匹配时默认加载模型列表中的第一个模型；多结果时先让用户确认。
            """)
    public ConstructionProjectResult browseConstructionProject(
            @ToolParam(description = "工程名称或工程ID，例如厦门同安输变电工程") String projectNameOrId) {
        if (projectNameOrId == null || projectNameOrId.isBlank()) {
            throw new IllegalArgumentException("工程名称或ID不能为空");
        }
        String requested = projectNameOrId.trim();
        String normalized = requested.toLowerCase(Locale.ROOT);
        List<ConstructionProject> matches = PROJECTS.stream()
                .filter(item -> item.projectId().equalsIgnoreCase(normalized)
                        || item.projectName().toLowerCase(Locale.ROOT).contains(normalized))
                .toList();
        List<EndpointCall> calls = List.of(new EndpointCall("POST", PROJECT_LIST_ENDPOINT,
                Map.of("keyword", requested, "pageNum", 1, "pageSize", 20)));
        if (matches.isEmpty()) {
            return new ConstructionProjectResult(ToolResultStatus.NOT_FOUND, null, null, List.of(), calls,
                    ToolResultStatus.NOT_FOUND.msg());
        }
        if (matches.size() > 1) {
            return new ConstructionProjectResult(ToolResultStatus.NEED_CONFIRMATION, null, null,
                    matches.stream().map(ProjectCandidate::from).toList(), calls,
                    ToolResultStatus.NEED_CONFIRMATION.msg());
        }
        ConstructionProject selected = matches.getFirst();
        ProjectCandidate project = ProjectCandidate.from(selected);
        ModelService defaultModel = selected.models().isEmpty() ? null : selected.models().getFirst();
        ProjectActionParams params = new ProjectActionParams(project, selected.models(), defaultModel, true);
        return new ConstructionProjectResult(ToolResultStatus.COMPLETED, ACTION, params, List.of(), calls,
                ToolResultStatus.COMPLETED.msg());
    }

    private static ConstructionProject project(String id, String name, String status, String region,
            ModelService... models) {
        return new ConstructionProject(id, name, status, region, List.of(models));
    }

    private static ModelService model(String id, String name, String type, String serviceUrl) {
        return new ModelService(id, name, type, serviceUrl);
    }

    private record ConstructionProject(String projectId, String projectName, String status,
            String region, List<ModelService> models) { }

    public record ProjectCandidate(String projectId, String projectName, String status, String region) {
        static ProjectCandidate from(ConstructionProject project) {
            return new ProjectCandidate(project.projectId(), project.projectName(), project.status(),
                    project.region());
        }
    }

    public record ModelService(String modelId, String modelName, String modelType, String serviceUrl) { }
    public record EndpointCall(String method, String endpoint, Map<String, Object> parameters) { }
    public record ProjectActionParams(ProjectCandidate project, List<ModelService> models,
            ModelService defaultModel, boolean autoLoadDefaultModel) { }
    public record ConstructionProjectResult(ToolResultStatus status, String action, ProjectActionParams actionParams,
            List<ProjectCandidate> candidates, List<EndpointCall> endpointCalls, String message) { }
}
