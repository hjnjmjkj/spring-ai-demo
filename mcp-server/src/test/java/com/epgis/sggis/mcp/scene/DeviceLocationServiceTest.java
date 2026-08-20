package com.epgis.sggis.mcp.scene;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeviceLocationServiceTest {

    @Test
    void returnsLocateActionForUniqueEquipment() {
        var result = new DeviceLocationService().locateEquipment("16841");

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.action()).isEqualTo(DeviceLocationService.ACTION);
        assertThat(result.actionParams().equipment().name()).isEqualTo("110kV同安变电站");
        assertThat(result.actionParams().geometry().type()).isEqualTo("Point");
        assertThat(result.endpointCalls()).extracting(DeviceLocationService.EndpointCall::endpoint)
                .containsExactly(
                        DeviceLocationService.SEARCH_ENDPOINT,
                        "/queryservice/psrquery/object/301000/16841");
    }

    @Test
    void doesNotReturnActionUntilAmbiguousEquipmentIsConfirmed() {
        var result = new DeviceLocationService().locateEquipment("春山线");

        assertThat(result.status()).isEqualTo("NEED_CONFIRMATION");
        assertThat(result.action()).isNull();
        assertThat(result.actionParams()).isNull();
        assertThat(result.candidates()).hasSize(2);
    }
}
