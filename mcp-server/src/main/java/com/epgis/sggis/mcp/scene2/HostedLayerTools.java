package com.epgis.sggis.mcp.scene2;

import com.epgis.sggis.mcp.model.ToolResultStatus;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/** 场景二的接口级 Tool 版本；当前未注册到 MCP。 */
@Service
public class HostedLayerTools {

    public static final String APPLICATION_LIST_ENDPOINT = "/authorityservice/baseservice/getapps";
    public static final String LAYER_LIST_ENDPOINT = "/metadataservice/datatype/list";

    private static final List<ApplicationInfo> APPLICATIONS = List.of(
            new ApplicationInfo("4bb6185c64ec451db9a6fe46c84d7273", "配网运检应用"),
            new ApplicationInfo("8f8502d7347c4bc1a5ad30f669b66f56", "基建管理应用"));
    private static final List<HostedLayer> LAYERS = List.of(
            new HostedLayer(92, "4bb6185c64ec451db9a6fe46c84d7273", "配网运检应用", "设备缺陷图层", 0),
            new HostedLayer(108, "4bb6185c64ec451db9a6fe46c84d7273", "配网运检应用", "抢修资源图层", 0),
            new HostedLayer(205, "8f8502d7347c4bc1a5ad30f669b66f56", "基建管理应用", "在建工程图层", 0),
            new HostedLayer(206, "8f8502d7347c4bc1a5ad30f669b66f56", "基建管理应用", "工程红线图层", 0));

    @Tool(description = """
            scene2 场景二第一步，对应 /authorityservice/baseservice/getapps。
            按应用名称查询应用；确认appKey后调用queryHostedLayersForApplication查询图层。
            """)
    public ApplicationListResult queryApplicationsForHostedLayer(
            @ToolParam(description = "应用名称关键字") String applicationName) {
        requireText(applicationName, "应用名称不能为空");
        String requested = applicationName.trim();
        List<ApplicationInfo> applications = APPLICATIONS.stream()
                .filter(item -> containsIgnoreCase(item.appName(), requested)
                        || item.appKey().equalsIgnoreCase(requested))
                .toList();
        ToolResultStatus status = candidateStatus(applications.size());
        return new ApplicationListResult(status, APPLICATION_LIST_ENDPOINT,
                Map.of("appName", requested), applications,
                status.msg(),
                applications.size() == 1
                        ? "使用appKey、appName和图层名称调用queryHostedLayersForApplication。"
                        : "没有唯一应用时，请先补充或确认应用。");
    }

    @Tool(description = """
            scene2 场景二第二步，对应 POST /metadataservice/datatype/list。
            查询指定应用下的托管图层；唯一匹配时返回 LOAD_HOSTED_LAYER action。
            """)
    public HostedLayerListResult queryHostedLayersForApplication(
            @ToolParam(description = "应用列表返回的appKey") String appKey,
            @ToolParam(description = "应用列表返回的appName") String appName,
            @ToolParam(description = "用户要查看的托管图层名称") String layerName) {
        requireText(appKey, "appKey不能为空");
        requireText(appName, "appName不能为空");
        requireText(layerName, "图层名称不能为空");
        List<LayerInfo> layers = LAYERS.stream()
                .filter(item -> item.appKey().equals(appKey.trim()))
                .filter(item -> containsIgnoreCase(item.appName(), appName.trim()))
                .filter(item -> containsIgnoreCase(item.layerAlias(), layerName.trim()))
                .map(LayerInfo::from)
                .toList();
        String action = layers.size() == 1 ? "LOAD_HOSTED_LAYER" : null;
        HostedLayerActionParams params = layers.size() == 1
                ? new HostedLayerActionParams(layers.getFirst(), "3D", true) : null;
        ToolResultStatus status = switch (layers.size()) {
            case 0 -> ToolResultStatus.NOT_FOUND;
            case 1 -> ToolResultStatus.COMPLETED;
            default -> ToolResultStatus.NEED_CONFIRMATION;
        };
        return new HostedLayerListResult(status, LAYER_LIST_ENDPOINT,
                Map.of("appName", appName.trim(), "layerAlias", layerName.trim(),
                        "queryType", 3, "page", 1, "size", 50),
                layers, action, params, status.msg());
    }

    private static ToolResultStatus candidateStatus(int candidateCount) {
        return switch (candidateCount) {
            case 0 -> ToolResultStatus.NOT_FOUND;
            case 1 -> ToolResultStatus.READY_FOR_NEXT_STEP;
            default -> ToolResultStatus.NEED_CONFIRMATION;
        };
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static boolean containsIgnoreCase(String value, String keyword) {
        return value.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private record HostedLayer(int layerId, String appKey, String appName, String layerAlias, int state) { }

    public record ApplicationInfo(String appKey, String appName) { }
    public record ApplicationListResult(ToolResultStatus status, String endpoint, Map<String, Object> request,
            List<ApplicationInfo> applications, String message, String nextAction) { }
    public record LayerInfo(int layerId, String appKey, String appName, String layerAlias, int state) {
        static LayerInfo from(HostedLayer item) {
            return new LayerInfo(item.layerId(), item.appKey(), item.appName(), item.layerAlias(), item.state());
        }
    }
    public record HostedLayerActionParams(LayerInfo layer, String sceneMode, boolean visible) { }
    public record HostedLayerListResult(ToolResultStatus status, String endpoint,
            Map<String, Object> request, List<LayerInfo> layers,
            String action, HostedLayerActionParams actionParams, String message) { }
}
