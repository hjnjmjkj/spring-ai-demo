package com.epgis.sggis.mcp.power;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PowerTraceServiceTest {

    @Test
    void executesSearchTraceAndDetailsInOrder() {
        PowerTraceService service = new PowerTraceService();

        var search = service.searchPowerEquipment("益群服装加工厂#1变");
        assertThat(search.endpoint()).isEqualTo(PowerTraceService.SEARCH_ENDPOINT);
        assertThat(search.candidates()).singleElement()
                .satisfies(candidate -> assertThat(candidate.oid()).isEqualTo("436289"));

        var trace = service.tracePowerSource(search.searchId(), "436289", "1");
        assertThat(trace.endpoint()).isEqualTo(PowerTraceService.TRACE_ENDPOINT);
        assertThat(trace.paths()).singleElement().satisfies(path ->
                assertThat(path.devices()).extracting(PowerTraceService.EquipmentRef::oid)
                        .containsExactly("16841", "16585931", "15678432", "436289"));

        var details = service.queryPowerEquipmentDetails(trace.traceId());
        assertThat(details.endpointCalls())
                .allSatisfy(call -> assertThat(call.endpoint()).isEqualTo(PowerTraceService.DETAILS_ENDPOINT));
        assertThat(details.paths().getFirst().devices())
                .extracting(PowerTraceService.EquipmentDetail::name)
                .containsExactly(
                        "110kV同安变电站",
                        "同安变10kVⅠ段母线",
                        "同安变10kV益群线901开关",
                        "厦门市同安益群服装加工厂#1变");
        assertThat(details.summary()).contains("110kV同安变电站").contains("实际开关状态");
    }

    @Test
    void returnsCandidatesForAmbiguousName() {
        var result = new PowerTraceService().searchPowerEquipment("一号配变");

        assertThat(result.candidates()).extracting(PowerTraceService.EquipmentCandidate::name)
                .containsExactly("东区一号配变", "西区一号配变");
        assertThat(result.nextAction()).contains("用户确认");
    }

    @Test
    void preventsSkippingSearchStep() {
        assertThatThrownBy(() -> new PowerTraceService().tracePowerSource("invalid", "436289", "1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("先调用 searchPowerEquipment");
    }

    @Test
    void preventsSkippingTraceStep() {
        assertThatThrownBy(() -> new PowerTraceService().queryPowerEquipmentDetails("invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("依次调用");
    }
}
