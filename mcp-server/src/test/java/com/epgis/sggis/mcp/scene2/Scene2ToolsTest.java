package com.epgis.sggis.mcp.scene2;

import static org.assertj.core.api.Assertions.assertThat;

import com.epgis.sggis.mcp.model.ToolResultStatus;
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

        assertThat(search.status()).isEqualTo(ToolResultStatus.READY_FOR_NEXT_STEP);
        assertThat(search.message()).isEqualTo(ToolResultStatus.READY_FOR_NEXT_STEP.msg());
        assertThat(search.endpoint()).isEqualTo(DeviceLocationTools.SEARCH_ENDPOINT);
        assertThat(detail.status()).isEqualTo(ToolResultStatus.COMPLETED);
        assertThat(detail.message()).isEqualTo(ToolResultStatus.COMPLETED.msg());
        assertThat(detail.endpoint()).isEqualTo("/queryservice/psrquery/object/301000/16841");
        assertThat(detail.action()).isEqualTo("LOCATE_EQUIPMENT");
    }

    @Test
    void hostedLayerIsSplitIntoApplicationAndLayerTools() {
        HostedLayerTools tools = new HostedLayerTools();

        var applications = tools.queryApplicationsForHostedLayer("配网运检应用");
        var app = applications.applications().getFirst();
        var layers = tools.queryHostedLayersForApplication(app.appKey(), app.appName(), "设备缺陷图层");

        assertThat(applications.status()).isEqualTo(ToolResultStatus.READY_FOR_NEXT_STEP);
        assertThat(applications.endpoint()).isEqualTo(HostedLayerTools.APPLICATION_LIST_ENDPOINT);
        assertThat(layers.status()).isEqualTo(ToolResultStatus.COMPLETED);
        assertThat(layers.endpoint()).isEqualTo(HostedLayerTools.LAYER_LIST_ENDPOINT);
        assertThat(layers.actionParams().layer().layerId()).isEqualTo(92);
    }

    @Test
    void geometryRegionSeparatesOrganizationAndBufferInterfaceChains() {
        GeometryRegionTools tools = new GeometryRegionTools();

        var catalog = tools.queryOrganizationCatalogForRegion("同安区供电中心");
        var boundary = tools.queryOrganizationBoundaryForRegion(catalog.organizations().getFirst().iscId());
        var equipmentSearch = tools.searchEquipmentForBuffer("中闽线");
        var equipment = equipmentSearch.candidates().getFirst();
        var detail = tools.queryEquipmentDetailForBuffer(equipment.typeId(), equipment.oid());
        var buffer = tools.analyzeGeometryBufferForRegion(detail.geometry(), 50);

        assertThat(catalog.status()).isEqualTo(ToolResultStatus.READY_FOR_NEXT_STEP);
        assertThat(boundary.status()).isEqualTo(ToolResultStatus.COMPLETED);
        assertThat(boundary.action()).isEqualTo("DRAW_REGION");
        assertThat(boundary.actionParams().mode()).isEqualTo("ORGANIZATION_BOUNDARY");
        assertThat(equipmentSearch.status()).isEqualTo(ToolResultStatus.READY_FOR_NEXT_STEP);
        assertThat(detail.status()).isEqualTo(ToolResultStatus.READY_FOR_NEXT_STEP);
        assertThat(buffer.status()).isEqualTo(ToolResultStatus.COMPLETED);
        assertThat(buffer.action()).isEqualTo("DRAW_REGION");
        assertThat(buffer.actionParams().mode()).isEqualTo("EQUIPMENT_BUFFER");
    }

    @Test
    void constructionProjectRemainsOneToolBecauseItHasOneInterface() {
        var result = new ConstructionProjectTools().queryConstructionProjects("BUILD-2026-001");

        assertThat(result.status()).isEqualTo(ToolResultStatus.COMPLETED);
        assertThat(result.endpoint()).isEqualTo(ConstructionProjectTools.PROJECT_LIST_ENDPOINT);
        assertThat(result.action()).isEqualTo("SHOW_CONSTRUCTION_PROJECT");
        assertThat(result.actionParams().defaultModel().modelId()).isEqualTo("MODEL-001-A");
    }

    @Test
    void reportsNotFoundAndNeedConfirmationForCandidateQueries() {
        DeviceLocationTools tools = new DeviceLocationTools();

        assertThat(tools.searchEquipmentForLocation("不存在的设备").status())
                .isEqualTo(ToolResultStatus.NOT_FOUND);
        assertThat(tools.searchEquipmentForLocation("春山线").status())
                .isEqualTo(ToolResultStatus.NEED_CONFIRMATION);
    }
}
