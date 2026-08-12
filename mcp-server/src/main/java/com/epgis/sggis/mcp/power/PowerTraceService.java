package com.epgis.sggis.mcp.power;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/** 三步电源追溯场景的内存模拟服务。 */
@Service
public class PowerTraceService {

    public static final String SEARCH_ENDPOINT = "/datasearchservice/equipment/search";
    public static final String TRACE_ENDPOINT = "/powergridtoposervice/advancedanalysis/powersource";
    public static final String DETAILS_ENDPOINT = "/queryservice/psrquery/objects";

    private static final Map<String, EquipmentDetail> EQUIPMENT = equipmentData();
    private static final List<String> SEARCHABLE_OIDS = List.of(
            "436289", "447930", "6793892", "885696", "510001", "510002");
    private static final Map<String, List<EquipmentRef>> POWER_PATHS = powerPathData();

    private final Map<String, SearchSession> searches = new ConcurrentHashMap<>();
    private final Map<String, TraceSession> traces = new ConcurrentHashMap<>();

    @Tool(description = """
            电源追溯第一步：按设备名称、OID 或设备ID模拟调用 /datasearchservice/equipment/search 搜索电网设备。
            用户询问某设备的上级电源时必须先调用本工具。若返回多个候选，先请用户确认设备，不能直接调用下一步。
            """)
    public EquipmentSearchResult searchPowerEquipment(
            @ToolParam(description = "用户提供的设备名称、OID 或设备ID，不得为空") String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("设备搜索关键字不能为空");
        }
        String normalized = keyword.trim().toLowerCase(Locale.ROOT);
        List<EquipmentCandidate> candidates = SEARCHABLE_OIDS.stream()
                .map(EQUIPMENT::get)
                .filter(detail -> matches(detail, normalized))
                .map(EquipmentCandidate::from)
                .toList();
        String searchId = UUID.randomUUID().toString();
        searches.put(searchId, new SearchSession(candidates));
        String nextAction = switch (candidates.size()) {
            case 0 -> "没有匹配设备，请让用户提供更准确的设备名称、OID 或设备ID。";
            case 1 -> "设备唯一，使用 searchId 和该设备 oid 调用 tracePowerSource。";
            default -> "存在多个候选，必须先让用户确认具体设备；确认后使用 searchId 和选定 oid 调用 tracePowerSource。";
        };
        return new EquipmentSearchResult(searchId, SEARCH_ENDPOINT, keyword.trim(), candidates, nextAction);
    }

    @Tool(description = """
            电源追溯第二步：模拟调用 /powergridtoposervice/advancedanalysis/powersource。
            必须使用 searchPowerEquipment 返回的 searchId，并且 deviceOid 必须是该次搜索的候选设备。
            返回顺序严格为电源点到目标设备。成功后必须继续调用 queryPowerEquipmentDetails 获取可读设备名称。
            """)
    public PowerTraceResult tracePowerSource(
            @ToolParam(description = "第一步 searchPowerEquipment 返回的 searchId") String searchId,
            @ToolParam(description = "用户确认的候选设备 oid") String deviceOid,
            @ToolParam(description = "开关运行状态：1 表示实际状态，2 表示计划状态") String runStatus) {
        SearchSession search = searches.get(searchId);
        if (search == null) {
            throw new IllegalArgumentException("searchId 无效，请先调用 searchPowerEquipment");
        }
        if (!"1".equals(runStatus) && !"2".equals(runStatus)) {
            throw new IllegalArgumentException("runStatus 只能是 1 或 2");
        }
        EquipmentCandidate selected = search.candidates().stream()
                .filter(candidate -> candidate.oid().equals(deviceOid))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("deviceOid 不属于本次搜索结果"));
        List<EquipmentRef> refs = POWER_PATHS.get(deviceOid);
        if (refs == null) {
            throw new IllegalArgumentException("模拟数据中没有该设备的电源路径");
        }
        String traceId = UUID.randomUUID().toString();
        List<PowerPath> paths = List.of(new PowerPath("path0", refs));
        traces.put(traceId, new TraceSession(selected, runStatus, paths));
        return new PowerTraceResult(traceId, TRACE_ENDPOINT, selected, runStatus, paths,
                "使用 traceId 调用 queryPowerEquipmentDetails，批量查询路径设备详情并生成最终答案。");
    }

    @Tool(description = """
            电源追溯第三步：模拟按 typeid 分组连续调用 /queryservice/psrquery/objects，查询溯源路径中所有设备的详细属性。
            必须使用 tracePowerSource 返回的 traceId。工具返回后，按电源点到目标设备的顺序向用户说明上级电源和完整供电路径。
            """)
    public EquipmentDetailsResult queryPowerEquipmentDetails(
            @ToolParam(description = "第二步 tracePowerSource 返回的 traceId") String traceId) {
        TraceSession trace = traces.get(traceId);
        if (trace == null) {
            throw new IllegalArgumentException("traceId 无效，请依次调用设备搜索和电源追溯工具");
        }
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        List<PowerPathDetails> detailedPaths = new ArrayList<>();
        for (PowerPath path : trace.paths()) {
            List<EquipmentDetail> details = new ArrayList<>();
            for (EquipmentRef ref : path.devices()) {
                grouped.computeIfAbsent(ref.typeId(), ignored -> new ArrayList<>()).add(ref.oid());
                EquipmentDetail detail = EQUIPMENT.get(ref.oid());
                if (detail == null) {
                    throw new IllegalStateException("模拟详情数据缺失: " + ref.oid());
                }
                details.add(detail.withRole(ref.role()));
            }
            detailedPaths.add(new PowerPathDetails(path.name(), details));
        }
        List<EndpointCall> endpointCalls = grouped.entrySet().stream()
                .map(entry -> new EndpointCall(DETAILS_ENDPOINT, entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
        EquipmentDetail source = detailedPaths.getFirst().devices().getFirst();
        return new EquipmentDetailsResult(traceId, endpointCalls, detailedPaths,
                "目标设备“" + trace.selected().name() + "”的上级电源为“" + source.name()
                        + "”，结果基于" + ("1".equals(trace.runStatus()) ? "实际" : "计划") + "开关状态。");
    }

    private static boolean matches(EquipmentDetail detail, String keyword) {
        return detail.name().toLowerCase(Locale.ROOT).contains(keyword)
                || detail.oid().equalsIgnoreCase(keyword)
                || detail.deviceId().equalsIgnoreCase(keyword);
    }

    private static Map<String, EquipmentDetail> equipmentData() {
        Map<String, EquipmentDetail> data = new LinkedHashMap<>();
        add(data, "16841", "301000", "110kV同安变电站", "变电站", "110kV", "STATION-16841");
        add(data, "16585931", "360000", "同安变10kVⅠ段母线", "母线", "10kV", "BUS-16585931");
        add(data, "15678432", "360000", "同安变10kV益群线901开关", "开关", "10kV", "SW-15678432");
        add(data, "436289", "302000", "厦门市同安益群服装加工厂#1变", "配电变压器", "10kV", "455ceeba-27b8-4b91-8ba2-05b67de0c768");
        add(data, "17620", "301000", "110kV大同变电站", "变电站", "110kV", "STATION-17620");
        add(data, "16600001", "360000", "大同变10kVⅡ段母线", "母线", "10kV", "BUS-16600001");
        add(data, "16600002", "360000", "大同变10kV茶业线903开关", "开关", "10kV", "SW-16600002");
        add(data, "447930", "302000", "厦门金壶春茶业有限公司", "配电变压器", "10kV", "692EE56C1C895170E0430A8EA81051700000");
        add(data, "1516289", "102000", "中闽线", "架空线路", "10kV", "LINE-1516289");
        add(data, "6793892", "102000", "中闽线#20", "杆塔", "10kV", "be304a7d-9df0-4550-ac4e-78595658e88e");
        add(data, "17001", "301000", "110kV春山变电站", "变电站", "110kV", "STATION-17001");
        add(data, "1612457", "102000", "春山线", "架空线路", "10kV", "LINE-1612457");
        add(data, "885696", "102000", "春山线#07", "杆塔", "10kV", "8B134ADAA0B34869AD7460F8AFC002521205181758");
        add(data, "510001", "302000", "东区一号配变", "配电变压器", "10kV", "TRANSFORMER-510001");
        add(data, "510002", "302000", "西区一号配变", "配电变压器", "10kV", "TRANSFORMER-510002");
        return Map.copyOf(data);
    }

    private static void add(Map<String, EquipmentDetail> data, String oid, String typeId, String name,
            String equipmentType, String voltageLevel, String deviceId) {
        data.put(oid, new EquipmentDetail(oid, typeId, name, equipmentType, voltageLevel, deviceId, null));
    }

    private static Map<String, List<EquipmentRef>> powerPathData() {
        return Map.of(
                "436289", refs("16841", "16585931", "15678432", "436289"),
                "447930", refs("17620", "16600001", "16600002", "447930"),
                "6793892", refs("16841", "16585931", "1516289", "6793892"),
                "885696", refs("17001", "1612457", "885696"),
                "510001", refs("16841", "16585931", "15678432", "510001"),
                "510002", refs("17620", "16600001", "16600002", "510002"));
    }

    private static List<EquipmentRef> refs(String... oids) {
        List<EquipmentRef> refs = new ArrayList<>();
        for (int index = 0; index < oids.length; index++) {
            EquipmentDetail detail = EQUIPMENT.get(oids[index]);
            String role = index == 0 ? "POWER_SOURCE" : index == oids.length - 1 ? "TARGET" : "UPSTREAM";
            refs.add(new EquipmentRef(detail.typeId(), detail.oid(), role));
        }
        return List.copyOf(refs);
    }

    private record SearchSession(List<EquipmentCandidate> candidates) { }
    private record TraceSession(EquipmentCandidate selected, String runStatus, List<PowerPath> paths) { }

    public record EquipmentCandidate(
            String oid, String typeId, String name, String equipmentType, String voltageLevel, String deviceId) {
        static EquipmentCandidate from(EquipmentDetail detail) {
            return new EquipmentCandidate(detail.oid(), detail.typeId(), detail.name(), detail.equipmentType(),
                    detail.voltageLevel(), detail.deviceId());
        }
    }

    public record EquipmentSearchResult(
            String searchId, String endpoint, String keyword, List<EquipmentCandidate> candidates, String nextAction) { }
    public record EquipmentRef(String typeId, String oid, String role) { }
    public record PowerPath(String name, List<EquipmentRef> devices) { }
    public record PowerTraceResult(String traceId, String endpoint, EquipmentCandidate selectedDevice,
            String runStatus, List<PowerPath> paths, String nextAction) { }
    public record EquipmentDetail(String oid, String typeId, String name, String equipmentType,
            String voltageLevel, String deviceId, String role) {
        EquipmentDetail withRole(String newRole) {
            return new EquipmentDetail(oid, typeId, name, equipmentType, voltageLevel, deviceId, newRole);
        }
    }
    public record EndpointCall(String endpoint, String typeId, List<String> psrids) { }
    public record PowerPathDetails(String name, List<EquipmentDetail> devices) { }
    public record EquipmentDetailsResult(String traceId, List<EndpointCall> endpointCalls,
            List<PowerPathDetails> paths, String summary) { }
}
