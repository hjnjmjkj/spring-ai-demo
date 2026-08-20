package com.epgis.sggis.mcp.scene;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/** 场景三：绘制组织机构边界或设备缓冲区域。 */
@Service
public class GeometryRegionService {

    public static final String ORGANIZATION_CATALOG_ENDPOINT = "/metadataservice/organization/catalog";
    public static final String ORGANIZATION_BOUNDARY_ENDPOINT = "/metadataservice/organization/jurisdiction";
    public static final String EQUIPMENT_SEARCH_ENDPOINT = "/datasearchservice/equipment/search";
    public static final String EQUIPMENT_DETAIL_ENDPOINT = "/queryservice/psrquery/object/{typeid}/{psrid}";
    public static final String BUFFER_ENDPOINT = "/mapservice/spatial/buffer";
    public static final String ACTION = "DRAW_REGION";

    private static final List<Organization> ORGANIZATIONS = List.of(
            new Organization("ORG-XM", "厦门市供电公司", polygon(117.88, 24.42, 118.32, 24.83)),
            new Organization("ORG-TA", "同安区供电中心", polygon(118.02, 24.61, 118.25, 24.82)));
    private static final List<Equipment> EQUIPMENT = List.of(
            new Equipment("16841", "301000", "110kV同安变电站",
                    new GeoJsonGeometry("Point", List.of(118.1510, 24.7310))),
            new Equipment("1516289", "102000", "中闽线",
                    new GeoJsonGeometry("LineString", List.of(
                            List.of(118.1280, 24.7200), List.of(118.1390, 24.7260),
                            List.of(118.1510, 24.7310)))));

    @Tool(description = """
            场景三几何区域绘制。查看组织机构范围时，target 传组织名称且 bufferDistanceMeters 传0；
            查看线路或变电站的缓冲范围时，target 传设备名称或OID，bufferDistanceMeters 传大于0的米数。
            返回 DRAW_REGION action 和 GeoJSON 面数据供前端绘制。
            """)
    public GeometryRegionResult drawGeometryRegion(
            @ToolParam(description = "组织机构名称、设备名称或设备OID") String target,
            @ToolParam(description = "缓冲距离，单位米；查看组织机构范围时传0") double bufferDistanceMeters) {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("目标名称不能为空");
        }
        if (bufferDistanceMeters < 0 || bufferDistanceMeters > 100_000) {
            throw new IllegalArgumentException("缓冲距离必须在0到100000米之间");
        }
        return bufferDistanceMeters == 0
                ? organizationBoundary(target.trim())
                : equipmentBuffer(target.trim(), bufferDistanceMeters);
    }

    private GeometryRegionResult organizationBoundary(String target) {
        List<EndpointCall> calls = List.of(
                new EndpointCall("GET", ORGANIZATION_CATALOG_ENDPOINT, Map.of("keyword", target)));
        List<Organization> matches = ORGANIZATIONS.stream()
                .filter(item -> containsIgnoreCase(item.name(), target) || item.iscId().equalsIgnoreCase(target))
                .toList();
        if (matches.isEmpty()) {
            return notFound(calls, "没有找到组织机构“" + target + "”。");
        }
        if (matches.size() > 1) {
            return new GeometryRegionResult("NEED_CONFIRMATION", null, null,
                    matches.stream().map(item -> new TargetCandidate(item.iscId(), "ORGANIZATION", item.name()))
                            .toList(),
                    calls, "匹配到多个组织机构，请让用户确认。");
        }
        Organization selected = matches.getFirst();
        List<EndpointCall> completedCalls = new ArrayList<>(calls);
        completedCalls.add(new EndpointCall("POST", ORGANIZATION_BOUNDARY_ENDPOINT,
                Map.of("iscIds", List.of(selected.iscId()), "cstype", "lonlat", "isDilution", false)));
        GeometryActionParams params = new GeometryActionParams("ORGANIZATION_BOUNDARY", selected.name(),
                selected.boundary(), 0, "#2F80ED", "rgba(47,128,237,0.25)");
        return new GeometryRegionResult("COMPLETED", ACTION, params, List.of(), completedCalls,
                "已获取“" + selected.name() + "”的经营管理区边界。");
    }

    private GeometryRegionResult equipmentBuffer(String target, double distanceMeters) {
        EndpointCall searchCall = new EndpointCall("POST", EQUIPMENT_SEARCH_ENDPOINT,
                Map.of("keyword", target, "keywordType", 4, "pageNum", 1, "pageSize", 20));
        List<Equipment> matches = EQUIPMENT.stream()
                .filter(item -> containsIgnoreCase(item.name(), target) || item.oid().equalsIgnoreCase(target))
                .toList();
        if (matches.isEmpty()) {
            return notFound(List.of(searchCall), "没有找到设备“" + target + "”。");
        }
        if (matches.size() > 1) {
            return new GeometryRegionResult("NEED_CONFIRMATION", null, null,
                    matches.stream().map(item -> new TargetCandidate(item.oid(), item.typeId(), item.name()))
                            .toList(),
                    List.of(searchCall), "匹配到多个设备，请让用户确认后使用OID再次调用。");
        }
        Equipment selected = matches.getFirst();
        String detailEndpoint = EQUIPMENT_DETAIL_ENDPOINT
                .replace("{typeid}", selected.typeId()).replace("{psrid}", selected.oid());
        List<EndpointCall> calls = List.of(
                searchCall,
                new EndpointCall("GET", detailEndpoint,
                        Map.of("cstype", "lonlat", "isGeojsonFormat", true)),
                new EndpointCall("POST", BUFFER_ENDPOINT,
                        Map.of("geometry", List.of(selected.geometry()), "distance", distanceMeters)));
        GeoJsonGeometry buffered = createBoundingBuffer(selected.geometry(), distanceMeters);
        GeometryActionParams params = new GeometryActionParams("EQUIPMENT_BUFFER", selected.name(), buffered,
                distanceMeters, "#F2994A", "rgba(242,153,74,0.25)");
        return new GeometryRegionResult("COMPLETED", ACTION, params, List.of(), calls,
                "已生成设备“" + selected.name() + "”的" + formatDistance(distanceMeters) + "米缓冲区。");
    }

    private static GeometryRegionResult notFound(List<EndpointCall> calls, String message) {
        return new GeometryRegionResult("NOT_FOUND", null, null, List.of(), calls, message);
    }

    private static String formatDistance(double distance) {
        return distance == Math.rint(distance) ? Long.toString((long) distance) : Double.toString(distance);
    }

    private static boolean containsIgnoreCase(String value, String keyword) {
        return value.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private static GeoJsonGeometry createBoundingBuffer(GeoJsonGeometry geometry, double distanceMeters) {
        List<List<Double>> points = new ArrayList<>();
        if ("Point".equals(geometry.type())) {
            @SuppressWarnings("unchecked")
            List<Double> point = (List<Double>) geometry.coordinates();
            points.add(point);
        }
        else {
            @SuppressWarnings("unchecked")
            List<List<Double>> line = (List<List<Double>>) geometry.coordinates();
            points.addAll(line);
        }
        double minX = points.stream().mapToDouble(point -> point.get(0)).min().orElseThrow();
        double maxX = points.stream().mapToDouble(point -> point.get(0)).max().orElseThrow();
        double minY = points.stream().mapToDouble(point -> point.get(1)).min().orElseThrow();
        double maxY = points.stream().mapToDouble(point -> point.get(1)).max().orElseThrow();
        double delta = distanceMeters / 111_000d;
        return polygon(minX - delta, minY - delta, maxX + delta, maxY + delta);
    }

    private static GeoJsonGeometry polygon(double minX, double minY, double maxX, double maxY) {
        return new GeoJsonGeometry("Polygon", List.of(List.of(
                List.of(minX, minY), List.of(maxX, minY), List.of(maxX, maxY),
                List.of(minX, maxY), List.of(minX, minY))));
    }

    private record Organization(String iscId, String name, GeoJsonGeometry boundary) { }
    private record Equipment(String oid, String typeId, String name, GeoJsonGeometry geometry) { }

    public record GeoJsonGeometry(String type, Object coordinates) { }
    public record TargetCandidate(String id, String typeId, String name) { }
    public record EndpointCall(String method, String endpoint, Map<String, Object> parameters) { }
    public record GeometryActionParams(String mode, String name, GeoJsonGeometry geometry,
            double bufferDistanceMeters, String strokeColor, String fillColor) { }
    public record GeometryRegionResult(String status, String action, GeometryActionParams actionParams,
            List<TargetCandidate> candidates, List<EndpointCall> endpointCalls, String message) { }
}
