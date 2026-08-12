package com.epgis.sggis.mcp.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.epgis.sggis.mcp.calculator.CalculatorService;
import com.epgis.sggis.mcp.grid.LineTransformerService;
import com.epgis.sggis.mcp.grid.NearbyEquipmentService;
import com.epgis.sggis.mcp.power.PowerTraceService;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;

class McpToolConfigurationTest {

    @Test
    void registersCalculatorAndPowerTraceTools() {
        ToolCallbackProvider provider = new McpToolConfiguration()
                .mcpTools(new CalculatorService(), new PowerTraceService(),
                        new LineTransformerService(), new NearbyEquipmentService());

        assertThat(Arrays.stream(provider.getToolCallbacks())
                .map(callback -> callback.getToolDefinition().name()))
                .containsExactlyInAnyOrder(
                        "calculate",
                        "searchPowerEquipment",
                        "tracePowerSource",
                        "queryPowerEquipmentDetails",
                        "queryLineDistributionTransformers",
                        "geocodeAddress",
                        "queryNearbyPowerEquipment");
    }
}
