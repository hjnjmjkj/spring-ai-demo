package com.epgis.sggis.mcp.scene;

import com.epgis.sggis.mcp.model.ToolResultStatus;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/** 场景一：搜索设备并返回前端地图定位动作。 */
@Service
public class DeviceLocationService {

    public static final String SEARCH_ENDPOINT = "/datasearchservice/equipment/search";
    public static final String DETAIL_ENDPOINT = "/queryservice/psrquery/object/{typeid}/{psrid}";
    public static final String ACTION = "LOCATE_EQUIPMENT";

    private static final List<Equipment> EQUIPMENT = List.of(
            equipment("1516289", "102000", "LINE-1516289", "中闽线", "LINE", "10kV",
                    geometry("LineString", List.of(
                            List.of(118.1280, 24.7200), List.of(118.1390, 24.7260),
                            List.of(118.1510, 24.7310)))),
            equipment("1612457", "102000", "LINE-1612457", "春山线", "LINE", "10kV",
                    geometry("LineString", List.of(
                            List.of(118.0900, 24.6500), List.of(118.1030, 24.6590)))),
            equipment("1612999", "102000", "LINE-1612999", "春山线备用支线", "LINE", "10kV",
                    geometry("LineString", List.of(
                            List.of(118.1020, 24.6590), List.of(118.1110, 24.6650)))),
            equipment("16841", "301000", "STATION-16841", "110kV同安变电站", "POINT", "110kV",
                    geometry("Point", List.of(118.1510, 24.7310))),
            equipment("310001", "301000", "STATION-310001", "110kV莲前变电站", "POINT", "110kV",
                    geometry("Point", List.of(118.1490, 24.4760))));

    @Tool(description = """
            场景一设备定位：按线路、变电站等设备名称、OID 或设备ID搜索设备，并查询唯一设备的坐标和属性。
            返回 LOCATE_EQUIPMENT action 供前端高亮点或线设备。匹配多个设备时必须先让用户确认候选项。
            """)
    public DeviceLocationResult locateEquipment(
            @ToolParam(description = "设备名称、OID 或设备ID，例如中闽线、16841或STATION-16841") String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("设备关键字不能为空");
        }
        String requested = keyword.trim();
        String normalized = requested.toLowerCase(Locale.ROOT);
        List<Equipment> matches = EQUIPMENT.stream()
                .filter(item -> item.name().toLowerCase(Locale.ROOT).contains(normalized)
                        || item.oid().equalsIgnoreCase(normalized)
                        || item.deviceId().equalsIgnoreCase(normalized))
                .toList();
        EndpointCall searchCall = new EndpointCall("POST", SEARCH_ENDPOINT,
                Map.of("keyword", requested, "keywordType", 4, "pageNum", 1, "pageSize", 20));
        if (matches.isEmpty()) {
            return new DeviceLocationResult(ToolResultStatus.NOT_FOUND, null, null, List.of(),
                    List.of(searchCall), ToolResultStatus.NOT_FOUND.msg());
        }
        if (matches.size() > 1) {
            return new DeviceLocationResult(ToolResultStatus.NEED_CONFIRMATION, null, null,
                    matches.stream().map(EquipmentCandidate::from).toList(), List.of(searchCall),
                    ToolResultStatus.NEED_CONFIRMATION.msg());
        }
        Equipment selected = matches.getFirst();
        EndpointCall detailCall = new EndpointCall("GET",
                DETAIL_ENDPOINT.replace("{typeid}", selected.typeId()).replace("{psrid}", selected.oid()),
                Map.of("iscodetsf", false, "isterminal", false, "versionid", "0", "cstype", "lonlat",
                        "isGeojsonFormat", true));
        LocateActionParams actionParams = new LocateActionParams(
                EquipmentCandidate.from(selected), selected.voltageLevel(), selected.geometry(),
                "POINT".equals(selected.geometryKind()) ? "substation" : "power-line", true);
        return new DeviceLocationResult(ToolResultStatus.COMPLETED, ACTION, actionParams, List.of(),
                List.of(searchCall, detailCall), ToolResultStatus.COMPLETED.msg());
    }

    private static Equipment equipment(String oid, String typeId, String deviceId, String name,
            String geometryKind, String voltageLevel, GeoJsonGeometry geometry) {
        return new Equipment(oid, typeId, deviceId, name, geometryKind, voltageLevel, geometry);
    }

    private static GeoJsonGeometry geometry(String type, Object coordinates) {
        return new GeoJsonGeometry(type, coordinates);
    }

    private record Equipment(String oid, String typeId, String deviceId, String name,
            String geometryKind, String voltageLevel, GeoJsonGeometry geometry) { }

    public record EquipmentCandidate(String oid, String typeId, String deviceId, String name, String geometryKind) {
        static EquipmentCandidate from(Equipment equipment) {
            return new EquipmentCandidate(equipment.oid(), equipment.typeId(), equipment.deviceId(),
                    equipment.name(), equipment.geometryKind());
        }
    }

    public record GeoJsonGeometry(String type, Object coordinates) { }
    public record EndpointCall(String method, String endpoint, Map<String, Object> parameters) { }
    public record LocateActionParams(EquipmentCandidate equipment, String voltageLevel,
            GeoJsonGeometry geometry, String icon, boolean highlight) { }
    public record DeviceLocationResult(ToolResultStatus status, String action, LocateActionParams actionParams,
            List<EquipmentCandidate> candidates, List<EndpointCall> endpointCalls, String message) { }
}
