package com.epgis.sggis.mcp.grid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NearbyEquipmentServiceTest {

    @Test
    void geocodesThenFindsNearbyStationsAndTransformers() {
        NearbyEquipmentService service = new NearbyEquipmentService();
        var geocode = service.geocodeAddress("厦门市思明区莲前西路859号", "厦门");

        assertThat(geocode.endpoint()).isEqualTo(NearbyEquipmentService.GEOCODE_ENDPOINT);
        assertThat(geocode.location().longitude()).isEqualTo(118.15287285602);

        var result = service.queryNearbyPowerEquipment(
                geocode.geocodeId(), 1.0, NearbyEquipmentService.EquipmentCategory.ALL);

        assertThat(result.endpointCalls()).hasSize(2)
                .allSatisfy(call -> assertThat(call.endpoint()).isEqualTo(NearbyEquipmentService.BUFFER_ENDPOINT));
        assertThat(result.equipment()).extracting(NearbyEquipmentService.NearbyEquipment::name)
                .containsExactly("莲前西路#1公变", "110kV莲前变电站", "莲前街道办专变");
        assertThat(result.equipment()).extracting(NearbyEquipmentService.NearbyEquipment::distanceMeters)
                .isSorted();
    }

    @Test
    void filtersNearbyEquipmentByCategory() {
        NearbyEquipmentService service = new NearbyEquipmentService();
        var geocode = service.geocodeAddress("莲前西路859号", "厦门");

        var result = service.queryNearbyPowerEquipment(
                geocode.geocodeId(), 5.0, NearbyEquipmentService.EquipmentCategory.STATION);

        assertThat(result.endpointCalls()).singleElement()
                .satisfies(call -> assertThat(call.typeId()).isEqualTo("301000"));
        assertThat(result.equipment()).allSatisfy(item -> assertThat(item.equipmentType()).isEqualTo("变电站"));
    }

    @Test
    void preventsSkippingGeocodeStep() {
        assertThatThrownBy(() -> new NearbyEquipmentService().queryNearbyPowerEquipment(
                "invalid", 2.0, NearbyEquipmentService.EquipmentCategory.ALL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("先调用 geocodeAddress");
    }

    @Test
    void validatesRadius() {
        NearbyEquipmentService service = new NearbyEquipmentService();
        var geocode = service.geocodeAddress("莲前西路859号", "厦门");

        assertThatThrownBy(() -> service.queryNearbyPowerEquipment(
                geocode.geocodeId(), 0, NearbyEquipmentService.EquipmentCategory.ALL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("大于0");
    }
}
