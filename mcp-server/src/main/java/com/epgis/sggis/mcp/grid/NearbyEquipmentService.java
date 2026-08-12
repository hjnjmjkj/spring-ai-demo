package com.epgis.sggis.mcp.grid;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/** 场景三：地址地理编码后，查询指定半径内的电网设备。 */
@Service
public class NearbyEquipmentService {

    public static final String GEOCODE_ENDPOINT = "/mapservice/geocode/geo";
    public static final String BUFFER_ENDPOINT = "/mapservice/spatial/equip/buffer";

    private static final String TRANSFORMER_TYPE_ID = "302000";
    private static final String STATION_TYPE_ID = "301000";
    private static final List<SpatialEquipment> SPATIAL_EQUIPMENT = spatialData();

    /** 保存第一步地理编码结果，第二步只能通过 geocodeId 读取坐标。 */
    private final Map<String, GeocodeSession> geocodes = new ConcurrentHashMap<>();

    @Tool(description = """
            场景三第一步：把结构化地址转换为坐标，模拟调用 GET /mapservice/geocode/geo。
            用户查询某地址周边X公里内的变电站或变压器时必须先调用本工具，并把返回的 geocodeId 用于 queryNearbyPowerEquipment。
            """)
    public GeocodeResult geocodeAddress(
            @ToolParam(description = "结构化地址，例如厦门市思明区莲前西路859号") String address,
            @ToolParam(description = "指定城市，例如厦门；地址已包含城市时也应填写对应城市") String city) {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("地址不能为空");
        }
        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException("城市不能为空");
        }
        GeoPoint point = resolvePoint(address.trim());
        String geocodeId = UUID.randomUUID().toString();
        String formattedAddress = normalizeAddress(address.trim(), city.trim());
        geocodes.put(geocodeId, new GeocodeSession(formattedAddress, city.trim(), point));
        return new GeocodeResult(geocodeId, GEOCODE_ENDPOINT, formattedAddress, city.trim(), point,
                "使用 geocodeId 调用 queryNearbyPowerEquipment，不得自行修改坐标。");
    }

    @Tool(description = """
            场景三第二步：查询地址坐标周边指定半径内的变电站、配电变压器或两者，模拟调用
            POST /mapservice/spatial/equip/buffer。必须使用 geocodeAddress 返回的 geocodeId，不能跳过第一步或猜测坐标。
            category 取 STATION、TRANSFORMER 或 ALL；半径单位为公里。
            """)
    public NearbyEquipmentResult queryNearbyPowerEquipment(
            @ToolParam(description = "第一步 geocodeAddress 返回的 geocodeId") String geocodeId,
            @ToolParam(description = "查询半径，单位公里，大于0且不超过50") double radiusKm,
            @ToolParam(description = "设备类别：STATION、TRANSFORMER 或 ALL") EquipmentCategory category) {
        GeocodeSession geocode = geocodes.get(geocodeId);
        if (geocode == null) {
            throw new IllegalArgumentException("geocodeId 无效，请先调用 geocodeAddress");
        }
        if (radiusKm <= 0 || radiusKm > 50) {
            throw new IllegalArgumentException("查询半径必须大于0且不超过50公里");
        }
        if (category == null) {
            throw new IllegalArgumentException("设备类别不能为空");
        }

        List<String> typeIds = switch (category) {
            case STATION -> List.of(STATION_TYPE_ID);
            case TRANSFORMER -> List.of(TRANSFORMER_TYPE_ID);
            case ALL -> List.of(STATION_TYPE_ID, TRANSFORMER_TYPE_ID);
        };
        double radiusMeters = radiusKm * 1000;
        List<NearbyEquipment> equipment = SPATIAL_EQUIPMENT.stream()
                .filter(item -> typeIds.contains(item.typeId()))
                .map(item -> new NearbyEquipment(item.oid(), item.typeId(), item.name(), item.equipmentType(),
                        item.voltageLevel(), item.location(), distanceMeters(geocode.point(), item.location())))
                .filter(item -> item.distanceMeters() <= radiusMeters)
                .sorted(java.util.Comparator.comparingDouble(NearbyEquipment::distanceMeters))
                .toList();
        List<BufferEndpointCall> endpointCalls = typeIds.stream()
                .map(typeId -> new BufferEndpointCall(BUFFER_ENDPOINT, typeId, geocode.point(), radiusMeters))
                .toList();
        return new NearbyEquipmentResult(geocodeId, geocode.formattedAddress(), geocode.point(), radiusKm,
                category, endpointCalls, equipment,
                "在“" + geocode.formattedAddress() + "”周边 " + radiusKm + " 公里内找到 "
                        + equipment.size() + " 个符合条件的电网设备。");
    }

    private static String normalizeAddress(String address, String city) {
        return address.contains(city) ? address : city + address;
    }

    private static GeoPoint resolvePoint(String address) {
        if (address.contains("莲前西路") && address.contains("859")) {
            return new GeoPoint(118.15287285602, 24.474092016072);
        }
        if (address.contains("同安")) {
            return new GeoPoint(118.1520, 24.7290);
        }
        if (address.contains("会展")) {
            return new GeoPoint(118.1830, 24.4690);
        }
        return new GeoPoint(118.0894, 24.4798);
    }

    private static double distanceMeters(GeoPoint from, GeoPoint to) {
        double earthRadius = 6_371_000;
        double lat1 = Math.toRadians(from.latitude());
        double lat2 = Math.toRadians(to.latitude());
        double deltaLat = Math.toRadians(to.latitude() - from.latitude());
        double deltaLon = Math.toRadians(to.longitude() - from.longitude());
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        return Math.round(earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)) * 10.0) / 10.0;
    }

    private static List<SpatialEquipment> spatialData() {
        List<SpatialEquipment> data = new ArrayList<>();
        data.add(new SpatialEquipment("310001", STATION_TYPE_ID, "110kV莲前变电站", "变电站", "110kV",
                new GeoPoint(118.1490, 24.4760)));
        data.add(new SpatialEquipment("310002", STATION_TYPE_ID, "110kV会展变电站", "变电站", "110kV",
                new GeoPoint(118.1810, 24.4690)));
        data.add(new SpatialEquipment("310003", STATION_TYPE_ID, "110kV同安变电站", "变电站", "110kV",
                new GeoPoint(118.1510, 24.7310)));
        data.add(new SpatialEquipment("620001", TRANSFORMER_TYPE_ID, "莲前西路#1公变", "配电变压器", "10kV",
                new GeoPoint(118.1540, 24.4730)));
        data.add(new SpatialEquipment("620002", TRANSFORMER_TYPE_ID, "莲前街道办专变", "配电变压器", "10kV",
                new GeoPoint(118.1475, 24.4715)));
        data.add(new SpatialEquipment("620003", TRANSFORMER_TYPE_ID, "会展中心#2专变", "配电变压器", "10kV",
                new GeoPoint(118.1800, 24.4680)));
        data.add(new SpatialEquipment("620004", TRANSFORMER_TYPE_ID, "同安区政府公变", "配电变压器", "10kV",
                new GeoPoint(118.1530, 24.7285)));
        return List.copyOf(data);
    }

    public enum EquipmentCategory { STATION, TRANSFORMER, ALL }

    public record GeoPoint(double longitude, double latitude) { }

    private record GeocodeSession(String formattedAddress, String city, GeoPoint point) { }

    public record GeocodeResult(String geocodeId, String endpoint, String formattedAddress, String city,
            GeoPoint location, String nextAction) { }

    private record SpatialEquipment(String oid, String typeId, String name, String equipmentType,
            String voltageLevel, GeoPoint location) { }

    public record NearbyEquipment(String oid, String typeId, String name, String equipmentType,
            String voltageLevel, GeoPoint location, double distanceMeters) { }

    public record BufferEndpointCall(String endpoint, String typeId, GeoPoint geometry, double distanceMeters) { }

    public record NearbyEquipmentResult(String geocodeId, String formattedAddress, GeoPoint center,
            double radiusKm, EquipmentCategory category, List<BufferEndpointCall> endpointCalls,
            List<NearbyEquipment> equipment, String message) { }
}
