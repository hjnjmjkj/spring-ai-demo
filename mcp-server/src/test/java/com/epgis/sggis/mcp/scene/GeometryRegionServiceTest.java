package com.epgis.sggis.mcp.scene;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GeometryRegionServiceTest {

    @Test
    void drawsOrganizationBoundaryWhenDistanceIsZero() {
        var result = new GeometryRegionService().drawGeometryRegion("同安区供电中心", 0);

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.action()).isEqualTo(GeometryRegionService.ACTION);
        assertThat(result.actionParams().mode()).isEqualTo("ORGANIZATION_BOUNDARY");
        assertThat(result.actionParams().geometry().type()).isEqualTo("Polygon");
        assertThat(result.endpointCalls()).extracting(GeometryRegionService.EndpointCall::endpoint)
                .containsExactly(
                        GeometryRegionService.ORGANIZATION_CATALOG_ENDPOINT,
                        GeometryRegionService.ORGANIZATION_BOUNDARY_ENDPOINT);
    }

    @Test
    void searchesEquipmentAndDrawsBuffer() {
        var result = new GeometryRegionService().drawGeometryRegion("中闽线", 50);

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.actionParams().mode()).isEqualTo("EQUIPMENT_BUFFER");
        assertThat(result.actionParams().bufferDistanceMeters()).isEqualTo(50);
        assertThat(result.endpointCalls()).extracting(GeometryRegionService.EndpointCall::endpoint)
                .containsExactly(
                        GeometryRegionService.EQUIPMENT_SEARCH_ENDPOINT,
                        "/queryservice/psrquery/object/102000/1516289",
                        GeometryRegionService.BUFFER_ENDPOINT);
    }

    @Test
    void rejectsNegativeBufferDistance() {
        assertThatThrownBy(() -> new GeometryRegionService().drawGeometryRegion("中闽线", -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0到100000米");
    }
}
