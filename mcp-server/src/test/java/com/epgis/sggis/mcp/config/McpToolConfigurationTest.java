package com.epgis.sggis.mcp.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.epgis.sggis.mcp.calculator.CalculatorService;
import com.epgis.sggis.mcp.scene.ConstructionProjectService;
import com.epgis.sggis.mcp.scene.DeviceLocationService;
import com.epgis.sggis.mcp.scene.FileDisplayService;
import com.epgis.sggis.mcp.scene.GeometryRegionService;
import com.epgis.sggis.mcp.scene.HostedLayerService;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;

class McpToolConfigurationTest {

    @Test
    void registersCalculatorAndMapSceneAndFileDisplayTools() {
        ToolCallbackProvider provider = new McpToolConfiguration()
                .mcpTools(new CalculatorService(), new DeviceLocationService(), new HostedLayerService(),
                        new GeometryRegionService(), new ConstructionProjectService(), new FileDisplayService());

        var toolNames = Arrays.stream(provider.getToolCallbacks())
                .map(callback -> callback.getToolDefinition().name())
                .toList();

        assertThat(toolNames)
                .containsExactlyInAnyOrder(
                        "calculate",
                        "locateEquipment",
                        "viewHostedLayer",
                        "drawGeometryRegion",
                        "browseConstructionProject",
                        "showShapeData",
                        "previewGimModel");
    }
}
