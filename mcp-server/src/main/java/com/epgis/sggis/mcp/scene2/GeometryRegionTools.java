package com.epgis.sggis.mcp.scene2;

import com.epgis.sggis.mcp.model.ToolResultStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/** 场景三的接口级 Tool 版本；当前未注册到 MCP。 */
@Service
public class GeometryRegionTools {

    public static final String ORGANIZATION_CATALOG_ENDPOINT = "/metadataservice/organization/catalog";
    public static final String ORGANIZATION_BOUNDARY_ENDPOINT = "/metadataservice/organization/jurisdiction";
    public static final String EQUIPMENT_SEARCH_ENDPOINT = "/datasearchservice/equipment/search";
    public static final String EQUIPMENT_DETAIL_ENDPOINT = "/queryservice/psrquery/object/{typeid}/{psrid}";
    public static final String BUFFER_ENDPOINT = "/mapservice/spatial/buffer";

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
            scene2 场景三组织范围第一步，对应新增的组织机构目录接口。
            按组织名称查询iscId，确认后调用queryOrganizationBoundaryForRegion。
            """)
    public OrganizationCatalogResult queryOrganizationCatalogForRegion(
            @ToolParam(description = "组织机构名称") String organizationName) {
        requireText(organizationName, "组织机构名称不能为空");
        String requested = organizationName.trim();
        List<OrganizationInfo> organizations = ORGANIZATIONS.stream()
                .filter(item -> containsIgnoreCase(item.name(), requested)
                        || item.iscId().equalsIgnoreCase(requested))
                .map(item -> new OrganizationInfo(item.iscId(), item.name()))
                .toList();
        ToolResultStatus status = candidateStatus(organizations.size());
        return new OrganizationCatalogResult(status,
                ORGANIZATION_CATALOG_ENDPOINT, Map.of("keyword", requested),
                organizations, status.msg(), organizations.size() == 1
                        ? "使用iscId调用queryOrganizationBoundaryForRegion。"
                        : "没有唯一组织机构时，请先补充或确认组织。");
    }

    @Tool(description = """
            scene2 场景三组织范围第二步，对应 POST /metadataservice/organization/jurisdiction。
            按已确认的iscId查询组织边界，并返回 DRAW_REGION action。
            """)
    public OrganizationBoundaryResult queryOrganizationBoundaryForRegion(
            @ToolParam(description = "组织目录返回的GIS2.0组织机构iscId") String iscId) {
        requireText(iscId, "iscId不能为空");
        Organization selected = ORGANIZATIONS.stream()
                .filter(item -> item.iscId().equalsIgnoreCase(iscId.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("iscId未匹配到组织机构，请先查询组织目录"));
        RegionActionParams params = new RegionActionParams("ORGANIZATION_BOUNDARY", selected.name(),
                selected.boundary(), 0, "#2F80ED", "rgba(47,128,237,0.25)");
        return new OrganizationBoundaryResult(ToolResultStatus.COMPLETED, ORGANIZATION_BOUNDARY_ENDPOINT,
                Map.of("iscIds", List.of(selected.iscId()), "cstype", "lonlat", "isDilution", false),
                selected.boundary(), "DRAW_REGION", params, ToolResultStatus.COMPLETED.msg());
    }

    @Tool(description = """
            scene2 场景三设备缓冲第一步，对应 POST /datasearchservice/equipment/search。
            搜索线路或变电站；确认唯一设备后调用queryEquipmentDetailForBuffer。
            """)
    public EquipmentSearchResult searchEquipmentForBuffer(
            @ToolParam(description = "线路、变电站名称或OID") String keyword) {
        requireText(keyword, "设备关键字不能为空");
        String requested = keyword.trim();
        List<EquipmentInfo> candidates = EQUIPMENT.stream()
                .filter(item -> containsIgnoreCase(item.name(), requested)
                        || item.oid().equalsIgnoreCase(requested))
                .map(item -> new EquipmentInfo(item.oid(), item.typeId(), item.name()))
                .toList();
        ToolResultStatus status = candidateStatus(candidates.size());
        return new EquipmentSearchResult(status, EQUIPMENT_SEARCH_ENDPOINT,
                Map.of("keyword", requested, "keywordType", 4, "pageNum", 1, "pageSize", 20),
                candidates, status.msg(), candidates.size() == 1
                        ? "使用typeId和oid调用queryEquipmentDetailForBuffer。"
                        : "没有唯一设备时，请先补充或确认设备。");
    }

    @Tool(description = """
            scene2 场景三设备缓冲第二步，对应 GET /queryservice/psrquery/object/{typeid}/{psrid}。
            查询已确认设备的GeoJSON几何；然后调用analyzeGeometryBufferForRegion生成缓冲区。
            """)
    public EquipmentGeometryResult queryEquipmentDetailForBuffer(
            @ToolParam(description = "设备类型编号typeId") String typeId,
            @ToolParam(description = "设备资源编号oid") String psrId) {
        requireText(typeId, "typeId不能为空");
        requireText(psrId, "psrId不能为空");
        Equipment selected = EQUIPMENT.stream()
                .filter(item -> item.typeId().equals(typeId.trim()) && item.oid().equals(psrId.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("typeId和psrId未匹配到设备，请先搜索设备"));
        String endpoint = EQUIPMENT_DETAIL_ENDPOINT.replace("{typeid}", selected.typeId())
                .replace("{psrid}", selected.oid());
        return new EquipmentGeometryResult(ToolResultStatus.READY_FOR_NEXT_STEP, endpoint,
                Map.of("cstype", "lonlat", "isGeojsonFormat", true),
                new EquipmentInfo(selected.oid(), selected.typeId(), selected.name()), selected.geometry(),
                ToolResultStatus.READY_FOR_NEXT_STEP.msg(),
                "把geometry和用户给出的米数传给analyzeGeometryBufferForRegion。");
    }

    @Tool(description = """
            scene2 场景三设备缓冲第三步，对应 POST /mapservice/spatial/buffer。
            根据上一工具返回的GeoJSON几何和用户给出的米数生成缓冲区，并返回 DRAW_REGION action。
            """)
    public BufferAnalysisResult analyzeGeometryBufferForRegion(
            @ToolParam(description = "queryEquipmentDetailForBuffer返回的GeoJSON geometry")
            GeoJsonGeometry geometry,
            @ToolParam(description = "缓冲距离，单位米，大于0且不超过100000") double distanceMeters) {
        if (geometry == null || geometry.type() == null || geometry.coordinates() == null) {
            throw new IllegalArgumentException("geometry不能为空");
        }
        if (distanceMeters <= 0 || distanceMeters > 100_000) {
            throw new IllegalArgumentException("缓冲距离必须大于0且不超过100000米");
        }
        GeoJsonGeometry buffered = createBoundingBuffer(geometry, distanceMeters);
        RegionActionParams params = new RegionActionParams("EQUIPMENT_BUFFER", "设备缓冲区", buffered,
                distanceMeters, "#F2994A", "rgba(242,153,74,0.25)");
        return new BufferAnalysisResult(ToolResultStatus.COMPLETED, BUFFER_ENDPOINT,
                Map.of("geometry", List.of(geometry), "distance", distanceMeters), buffered,
                "DRAW_REGION", params, ToolResultStatus.COMPLETED.msg());
    }

    private static ToolResultStatus candidateStatus(int candidateCount) {
        return switch (candidateCount) {
            case 0 -> ToolResultStatus.NOT_FOUND;
            case 1 -> ToolResultStatus.READY_FOR_NEXT_STEP;
            default -> ToolResultStatus.NEED_CONFIRMATION;
        };
    }

    private static GeoJsonGeometry createBoundingBuffer(GeoJsonGeometry geometry, double distanceMeters) {
        List<List<Double>> points = new ArrayList<>();
        if ("Point".equals(geometry.type())) {
            points.add(numberPair(geometry.coordinates()));
        }
        else if ("LineString".equals(geometry.type())) {
            if (!(geometry.coordinates() instanceof List<?> values)) {
                throw new IllegalArgumentException("LineString coordinates格式错误");
            }
            values.forEach(value -> points.add(numberPair(value)));
        }
        else {
            throw new IllegalArgumentException("缓冲分析仅支持Point和LineString");
        }
        double minX = points.stream().mapToDouble(point -> point.get(0)).min().orElseThrow();
        double maxX = points.stream().mapToDouble(point -> point.get(0)).max().orElseThrow();
        double minY = points.stream().mapToDouble(point -> point.get(1)).min().orElseThrow();
        double maxY = points.stream().mapToDouble(point -> point.get(1)).max().orElseThrow();
        double delta = distanceMeters / 111_000d;
        return polygon(minX - delta, minY - delta, maxX + delta, maxY + delta);
    }

    private static List<Double> numberPair(Object value) {
        if (!(value instanceof List<?> pair) || pair.size() < 2
                || !(pair.get(0) instanceof Number x) || !(pair.get(1) instanceof Number y)) {
            throw new IllegalArgumentException("coordinates坐标格式错误");
        }
        return List.of(x.doubleValue(), y.doubleValue());
    }

    private static GeoJsonGeometry polygon(double minX, double minY, double maxX, double maxY) {
        return new GeoJsonGeometry("Polygon", List.of(List.of(
                List.of(minX, minY), List.of(maxX, minY), List.of(maxX, maxY),
                List.of(minX, maxY), List.of(minX, minY))));
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static boolean containsIgnoreCase(String value, String keyword) {
        return value.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private record Organization(String iscId, String name, GeoJsonGeometry boundary) { }
    private record Equipment(String oid, String typeId, String name, GeoJsonGeometry geometry) { }

    public record GeoJsonGeometry(String type, Object coordinates) { }
    public record OrganizationInfo(String iscId, String name) { }
    public record OrganizationCatalogResult(ToolResultStatus status, String endpoint,
            Map<String, Object> request,
            List<OrganizationInfo> organizations, String message, String nextAction) { }
    public record RegionActionParams(String mode, String name, GeoJsonGeometry geometry,
            double bufferDistanceMeters, String strokeColor, String fillColor) { }
    public record OrganizationBoundaryResult(ToolResultStatus status, String endpoint,
            Map<String, Object> request,
            GeoJsonGeometry boundary, String action, RegionActionParams actionParams, String message) { }
    public record EquipmentInfo(String oid, String typeId, String name) { }
    public record EquipmentSearchResult(ToolResultStatus status, String endpoint, Map<String, Object> request,
            List<EquipmentInfo> candidates, String message, String nextAction) { }
    public record EquipmentGeometryResult(ToolResultStatus status, String endpoint, Map<String, Object> request,
            EquipmentInfo equipment, GeoJsonGeometry geometry, String message, String nextAction) { }
    public record BufferAnalysisResult(ToolResultStatus status, String endpoint, Map<String, Object> request,
            GeoJsonGeometry geometry, String action, RegionActionParams actionParams, String message) { }
}
