package com.epgis.sggis.mcp.scene;

import com.epgis.sggis.mcp.model.ToolResultStatus;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/** 场景二：按应用和图层名称查找托管图层。 */
@Service
public class HostedLayerService {

    public static final String APPLICATION_LIST_ENDPOINT = "/authorityservice/baseservice/getapps";
    public static final String LAYER_LIST_ENDPOINT = "/metadataservice/datatype/list";
    public static final String ACTION = "LOAD_HOSTED_LAYER";

    private static final List<HostedLayer> LAYERS = List.of(
            layer(92, "4bb6185c64ec451db9a6fe46c84d7273", "配网运检应用", "设备缺陷图层", 0),
            layer(108, "4bb6185c64ec451db9a6fe46c84d7273", "配网运检应用", "抢修资源图层", 0),
            layer(205, "8f8502d7347c4bc1a5ad30f669b66f56", "基建管理应用", "在建工程图层", 0),
            layer(206, "8f8502d7347c4bc1a5ad30f669b66f56", "基建管理应用", "工程红线图层", 0));

    @Tool(description = """
            场景二托管图层查看：根据应用名称和托管图层名称查询图层ID，返回 LOAD_HOSTED_LAYER action，
            供前端在三维场景加载图层。若名称不唯一，必须列出候选并等待用户确认。
            """)
    public HostedLayerResult viewHostedLayer(
            @ToolParam(description = "应用名称，例如配网运检应用") String applicationName,
            @ToolParam(description = "托管图层名称，例如设备缺陷图层") String layerName) {
        requireText(applicationName, "应用名称不能为空");
        requireText(layerName, "图层名称不能为空");
        String requestedApp = applicationName.trim();
        String requestedLayer = layerName.trim();
        List<EndpointCall> calls = List.of(
                new EndpointCall("GET", APPLICATION_LIST_ENDPOINT, Map.of("appName", requestedApp)),
                new EndpointCall("POST", LAYER_LIST_ENDPOINT,
                        Map.of("appName", requestedApp, "layerAlias", requestedLayer,
                                "queryType", 3, "page", 1, "size", 50)));
        List<HostedLayer> matches = LAYERS.stream()
                .filter(item -> containsIgnoreCase(item.appName(), requestedApp))
                .filter(item -> containsIgnoreCase(item.layerAlias(), requestedLayer))
                .toList();
        if (matches.isEmpty()) {
            return new HostedLayerResult(ToolResultStatus.NOT_FOUND, null, null, List.of(), calls,
                    ToolResultStatus.NOT_FOUND.msg());
        }
        if (matches.size() > 1) {
            return new HostedLayerResult(ToolResultStatus.NEED_CONFIRMATION, null, null,
                    matches.stream().map(LayerCandidate::from).toList(), calls,
                    ToolResultStatus.NEED_CONFIRMATION.msg());
        }
        HostedLayer selected = matches.getFirst();
        LayerCandidate layer = LayerCandidate.from(selected);
        return new HostedLayerResult(ToolResultStatus.COMPLETED, ACTION,
                new HostedLayerActionParams(layer, "3D", true), List.of(), calls,
                ToolResultStatus.COMPLETED.msg());
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static boolean containsIgnoreCase(String value, String keyword) {
        return value.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private static HostedLayer layer(int layerId, String appKey, String appName, String alias, int state) {
        return new HostedLayer(layerId, appKey, appName, alias, state);
    }

    private record HostedLayer(int layerId, String appKey, String appName, String layerAlias, int state) { }

    public record LayerCandidate(int layerId, String appKey, String appName, String layerAlias, int state) {
        static LayerCandidate from(HostedLayer layer) {
            return new LayerCandidate(layer.layerId(), layer.appKey(), layer.appName(), layer.layerAlias(),
                    layer.state());
        }
    }

    public record EndpointCall(String method, String endpoint, Map<String, Object> parameters) { }
    public record HostedLayerActionParams(LayerCandidate layer, String sceneMode, boolean visible) { }
    public record HostedLayerResult(ToolResultStatus status, String action, HostedLayerActionParams actionParams,
            List<LayerCandidate> candidates, List<EndpointCall> endpointCalls, String message) { }
}
