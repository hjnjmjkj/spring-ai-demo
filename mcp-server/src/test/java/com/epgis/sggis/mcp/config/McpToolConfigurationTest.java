package com.epgis.sggis.mcp.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.epgis.sggis.mcp.calculator.CalculatorService;
import com.epgis.sggis.mcp.grid.LineTransformerService;
import com.epgis.sggis.mcp.grid.NearbyEquipmentService;
import com.epgis.sggis.mcp.power.PowerTraceService;
import com.epgis.sggis.mcp.scene.ConstructionProjectService;
import com.epgis.sggis.mcp.scene.DeviceLocationService;
import com.epgis.sggis.mcp.scene.GeometryRegionService;
import com.epgis.sggis.mcp.scene.HostedLayerService;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;

class McpToolConfigurationTest {

    @Test
    void registersCalculatorAndPowerTraceTools() {
        ToolCallbackProvider provider = new McpToolConfiguration()
                .mcpTools(new CalculatorService(), new PowerTraceService(),
                        new LineTransformerService(), new NearbyEquipmentService(),
                        new DeviceLocationService(), new HostedLayerService(),
                        new GeometryRegionService(), new ConstructionProjectService());

        var toolNames = Arrays.stream(provider.getToolCallbacks())
                .map(callback -> callback.getToolDefinition().name())
                .toList();

        assertThat(toolNames)
                .containsExactlyInAnyOrder(
                        "calculate",
                        "searchPowerEquipment",
                        "tracePowerSource",
                        "queryPowerEquipmentDetails",
                        "queryLineDistributionTransformers",
                        "geocodeAddress",
                        "queryNearbyPowerEquipment",
                        "locateEquipment",
                        "viewHostedLayer",
                        "drawGeometryRegion",
                        "browseConstructionProject");
        assertThat(toolNames).doesNotContain(
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
}
