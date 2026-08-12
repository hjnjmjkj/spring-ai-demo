package com.epgis.sggis.mcp.grid;

import java.util.List;
import java.util.Locale;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/** 场景二：查询指定线路下的全部配电变压器。 */
@Service
public class LineTransformerService {

    public static final String LINE_CONTAINS_ENDPOINT = "/queryservice/psrquery/line/{psrid}/contains";
    private static final String TRANSFORMER_TYPE_ID = "302000";
    private static final List<PowerLine> LINES = lineData();

    @Tool(description = """
            场景二专用工具：查询指定线路下的所有配电变压器，模拟调用
            GET /queryservice/psrquery/line/{psrid}/contains?typeids=302000。
            参数可使用线路名称或线路资源OID。若匹配多条线路，向用户列出 candidates 并等待确认，然后用确定的OID再次调用。
            """)
    public LineTransformerResult queryLineDistributionTransformers(
            @ToolParam(description = "线路名称或线路资源OID，例如中闽线或1516289") String lineNameOrOid) {
        if (lineNameOrOid == null || lineNameOrOid.isBlank()) {
            throw new IllegalArgumentException("线路名称或OID不能为空");
        }
        String keyword = lineNameOrOid.trim().toLowerCase(Locale.ROOT);
        List<PowerLine> matches = LINES.stream()
                .filter(line -> line.oid().equalsIgnoreCase(keyword)
                        || line.name().toLowerCase(Locale.ROOT).contains(keyword))
                .toList();
        if (matches.isEmpty()) {
            return new LineTransformerResult("NOT_FOUND", LINE_CONTAINS_ENDPOINT, lineNameOrOid.trim(),
                    null, List.of(), List.of(), "没有找到匹配线路，请提供更准确的线路名称或OID。");
        }
        if (matches.size() > 1) {
            List<LineCandidate> candidates = matches.stream().map(LineCandidate::from).toList();
            return new LineTransformerResult("NEED_CONFIRMATION", LINE_CONTAINS_ENDPOINT, lineNameOrOid.trim(),
                    null, candidates, List.of(), "匹配到多条线路，请让用户确认后使用线路OID再次调用。");
        }
        PowerLine line = matches.getFirst();
        return new LineTransformerResult("COMPLETED", LINE_CONTAINS_ENDPOINT, lineNameOrOid.trim(),
                LineCandidate.from(line), List.of(), line.transformers(),
                "线路“" + line.name() + "”下共有 " + line.transformers().size() + " 台配电变压器。");
    }

    private static List<PowerLine> lineData() {
        return List.of(
                new PowerLine("1516289", "中闽线", List.of(
                        transformer("436289", "厦门市同安益群服装加工厂#1变", "10kV", "630kVA"),
                        transformer("601001", "中闽线#1公变", "10kV", "400kVA"),
                        transformer("601002", "中闽线#2公变", "10kV", "500kVA"))),
                new PowerLine("1612457", "春山线", List.of(
                        transformer("602001", "春山村#1公变", "10kV", "400kVA"),
                        transformer("602002", "春山工业园专变", "10kV", "800kVA"))),
                new PowerLine("1612999", "春山线备用支线", List.of(
                        transformer("602101", "春山备用#1公变", "10kV", "315kVA"))));
    }

    private static DistributionTransformer transformer(String oid, String name, String voltage, String capacity) {
        return new DistributionTransformer(oid, TRANSFORMER_TYPE_ID, name, voltage, capacity);
    }

    public record DistributionTransformer(String oid, String typeId, String name, String voltageLevel,
            String ratedCapacity) { }

    private record PowerLine(String oid, String name, List<DistributionTransformer> transformers) { }

    public record LineCandidate(String oid, String name) {
        static LineCandidate from(PowerLine line) {
            return new LineCandidate(line.oid(), line.name());
        }
    }

    public record LineTransformerResult(String status, String endpoint, String requestedLine, LineCandidate line,
            List<LineCandidate> candidates, List<DistributionTransformer> transformers, String message) { }
}
