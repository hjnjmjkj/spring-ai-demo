package com.epgis.sggis.mcp.config;

import com.epgis.sggis.mcp.calculator.CalculatorService;
import com.epgis.sggis.mcp.grid.LineTransformerService;
import com.epgis.sggis.mcp.grid.NearbyEquipmentService;
import com.epgis.sggis.mcp.power.PowerTraceService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class McpToolConfiguration {

    @Bean
    ToolCallbackProvider mcpTools(CalculatorService calculatorService, PowerTraceService powerTraceService,
            LineTransformerService lineTransformerService, NearbyEquipmentService nearbyEquipmentService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(calculatorService, powerTraceService, lineTransformerService, nearbyEquipmentService)
                .build();
    }
}
