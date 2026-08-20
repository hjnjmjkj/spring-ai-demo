package com.epgis.sggis.mcp.scene2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

class Scene2ToolsTest {

    @Test
    void exposesTenInterfaceLevelToolsWhenBuiltSeparately() {
        var provider = MethodToolCallbackProvider.builder()
                .toolObjects(new DeviceLocationTools(), new HostedLayerTools(),
                        new GeometryRegionTools(), new ConstructionProjectTools())
                .build();

        assertThat(Arrays.stream(provider.getToolCallbacks())
                .map(callback -> callback.getToolDefinition().name()))
                .containsExactlyInAnyOrder(
                        "searchEquipmentForLocation",
                        "queryEquipmentDetailForLocation",
                        "queryApplicationsForHostedLayer",
                        "queryHostedLayersForApplication",
                        "queryOrganizationCatalogForRegion",
                        "queryOrganizationBoundaryForRegion",
                        "searchEquipmentForBuffer",
                        "queryEquipmentDetailForBuffer",
                        "analyzeGeometryBufferForRegion",
                        "queryConstructionProjects");
    }

    @Test
    void deviceLocationIsSplitIntoSearchAndDetailTools() {
        DeviceLocationTools tools = new DeviceLocationTools();

        var search = tools.searchEquipmentForLocation("16841");
        var candidate = search.candidates().getFirst();
        var detail = tools.queryEquipmentDetailForLocation(candidate.typeId(), candidate.oid());

        assertThat(search.endpoint()).isEqualTo(DeviceLocationTools.SEARCH_ENDPOINT);
        assertThat(detail.endpoint()).isEqualTo("/queryservice/psrquery/object/301000/16841");
        assertThat(detail.action()).isEqualTo("LOCATE_EQUIPMENT");
    }

    @Test
    void hostedLayerIsSplitIntoApplicationAndLayerTools() {
        HostedLayerTools tools = new HostedLayerTools();

        var applications = tools.queryApplicationsForHostedLayer("配网运检应用");
        var app = applications.applications().getFirst();
        var layers = tools.queryHostedLayersForApplication(app.appKey(), app.appName(), "设备缺陷图层");

        assertThat(applications.endpoint()).isEqualTo(HostedLayerTools.APPLICATION_LIST_ENDPOINT);
        assertThat(layers.endpoint()).isEqualTo(HostedLayerTools.LAYER_LIST_ENDPOINT);
        assertThat(layers.actionParams().layer().layerId()).isEqualTo(92);
    }

    @Test
    void geometryRegionSeparatesOrganizationAndBufferInterfaceChains() {
        GeometryRegionTools tools = new GeometryRegionTools();

        var catalog = tools.queryOrganizationCatalogForRegion("同安区供电中心");
        var boundary = tools.queryOrganizationBoundaryForRegion(catalog.organizations().getFirst().iscId());
        var equipment = tools.searchEquipmentForBuffer("中闽线").candidates().getFirst();
        var detail = tools.queryEquipmentDetailForBuffer(equipment.typeId(), equipment.oid());
        var buffer = tools.analyzeGeometryBufferForRegion(detail.geometry(), 50);

        assertThat(boundary.action()).isEqualTo("DRAW_REGION");
        assertThat(boundary.actionParams().mode()).isEqualTo("ORGANIZATION_BOUNDARY");
        assertThat(buffer.action()).isEqualTo("DRAW_REGION");
        assertThat(buffer.actionParams().mode()).isEqualTo("EQUIPMENT_BUFFER");
    }

    @Test
    void constructionProjectRemainsOneToolBecauseItHasOneInterface() {
        var result = new ConstructionProjectTools().queryConstructionProjects("BUILD-2026-001");

        assertThat(result.endpoint()).isEqualTo(ConstructionProjectTools.PROJECT_LIST_ENDPOINT);
        assertThat(result.action()).isEqualTo("SHOW_CONSTRUCTION_PROJECT");
        assertThat(result.actionParams().defaultModel().modelId()).isEqualTo("MODEL-001-A");
    }
}
