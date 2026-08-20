package com.epgis.sggis.mcp.scene2;

import com.epgis.sggis.mcp.model.ToolResultStatus;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/** 场景一的接口级 Tool 版本；当前未注册到 MCP。 */
@Service
public class DeviceLocationTools {

    public static final String SEARCH_ENDPOINT = "/datasearchservice/equipment/search";
    public static final String DETAIL_ENDPOINT = "/queryservice/psrquery/object/{typeid}/{psrid}";

    private static final List<Equipment> EQUIPMENT = List.of(
            new Equipment("1516289", "102000", "LINE-1516289", "中闽线", "LINE", "10kV",
                    new GeoJsonGeometry("LineString", List.of(
                            List.of(118.1280, 24.7200), List.of(118.1390, 24.7260),
                            List.of(118.1510, 24.7310)))),
            new Equipment("1612457", "102000", "LINE-1612457", "春山线", "LINE", "10kV",
                    new GeoJsonGeometry("LineString", List.of(
                            List.of(118.0900, 24.6500), List.of(118.1030, 24.6590)))),
            new Equipment("1612999", "102000", "LINE-1612999", "春山线备用支线", "LINE", "10kV",
                    new GeoJsonGeometry("LineString", List.of(
                            List.of(118.1020, 24.6590), List.of(118.1110, 24.6650)))),
            new Equipment("16841", "301000", "STATION-16841", "110kV同安变电站", "POINT", "110kV",
                    new GeoJsonGeometry("Point", List.of(118.1510, 24.7310))));

    @Tool(description = """
            scene2 场景一第一步，对应 POST /datasearchservice/equipment/search。
            按设备名称、OID或设备ID搜索定位候选；唯一候选后继续调用 queryEquipmentDetailForLocation。
            """)
    public EquipmentSearchResult searchEquipmentForLocation(
            @ToolParam(description = "设备名称、OID或设备ID") String keyword) {
        requireText(keyword, "设备关键字不能为空");
        String requested = keyword.trim();
        String normalized = requested.toLowerCase(Locale.ROOT);
        List<EquipmentCandidate> candidates = EQUIPMENT.stream()
                .filter(item -> item.name().toLowerCase(Locale.ROOT).contains(normalized)
                        || item.oid().equalsIgnoreCase(normalized)
                        || item.deviceId().equalsIgnoreCase(normalized))
                .map(EquipmentCandidate::from)
                .toList();
        String nextAction = switch (candidates.size()) {
            case 0 -> "没有匹配设备，请补充更准确的名称、OID或设备ID。";
            case 1 -> "使用候选项的typeId和oid调用queryEquipmentDetailForLocation。";
            default -> "存在多个候选，先让用户确认，再调用queryEquipmentDetailForLocation。";
        };
        ToolResultStatus status = candidateStatus(candidates.size());
        return new EquipmentSearchResult(status, SEARCH_ENDPOINT,
                Map.of("keyword", requested, "keywordType", 4, "pageNum", 1, "pageSize", 20),
                candidates, status.msg(), nextAction);
    }

    @Tool(description = """
            scene2 场景一第二步，对应 GET /queryservice/psrquery/object/{typeid}/{psrid}。
            查询用户已确认设备的属性和几何，返回 LOCATE_EQUIPMENT action 供前端高亮。
            """)
    public EquipmentDetailResult queryEquipmentDetailForLocation(
            @ToolParam(description = "搜索结果中的设备类型编号typeId") String typeId,
            @ToolParam(description = "搜索结果中的设备资源编号oid") String psrId) {
        requireText(typeId, "typeId不能为空");
        requireText(psrId, "psrId不能为空");
        Equipment selected = EQUIPMENT.stream()
                .filter(item -> item.typeId().equals(typeId.trim()) && item.oid().equals(psrId.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("typeId和psrId未匹配到设备，请先搜索并确认候选"));
        String endpoint = DETAIL_ENDPOINT.replace("{typeid}", selected.typeId())
                .replace("{psrid}", selected.oid());
        EquipmentDetail detail = new EquipmentDetail(selected.oid(), selected.typeId(), selected.deviceId(),
                selected.name(), selected.geometryKind(), selected.voltageLevel(), selected.geometry());
        LocateActionParams params = new LocateActionParams(detail,
                "POINT".equals(selected.geometryKind()) ? "substation" : "power-line", true);
        return new EquipmentDetailResult(ToolResultStatus.COMPLETED, endpoint,
                Map.of("iscodetsf", false, "isterminal", false, "versionid", "0", "cstype", "lonlat",
                        "isGeojsonFormat", true),
                detail, "LOCATE_EQUIPMENT", params, ToolResultStatus.COMPLETED.msg());
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

    private record Equipment(String oid, String typeId, String deviceId, String name,
            String geometryKind, String voltageLevel, GeoJsonGeometry geometry) { }

    public record GeoJsonGeometry(String type, Object coordinates) { }
    public record EquipmentCandidate(String oid, String typeId, String deviceId, String name,
            String geometryKind) {
        static EquipmentCandidate from(Equipment item) {
            return new EquipmentCandidate(item.oid(), item.typeId(), item.deviceId(), item.name(),
                    item.geometryKind());
        }
    }
    public record EquipmentSearchResult(ToolResultStatus status, String endpoint, Map<String, Object> request,
            List<EquipmentCandidate> candidates, String message, String nextAction) { }
    public record EquipmentDetail(String oid, String typeId, String deviceId, String name,
            String geometryKind, String voltageLevel, GeoJsonGeometry geometry) { }
    public record LocateActionParams(EquipmentDetail equipment, String icon, boolean highlight) { }
    public record EquipmentDetailResult(ToolResultStatus status, String endpoint, Map<String, Object> request,
            EquipmentDetail equipment, String action, LocateActionParams actionParams, String message) { }
}
