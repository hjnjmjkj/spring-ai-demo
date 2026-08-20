package com.epgis.sggis.mcp.config;

import com.epgis.sggis.mcp.calculator.CalculatorService;
import com.epgis.sggis.mcp.scene.ConstructionProjectService;
import com.epgis.sggis.mcp.scene.DeviceLocationService;
import com.epgis.sggis.mcp.scene.GeometryRegionService;
import com.epgis.sggis.mcp.scene.HostedLayerService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class McpToolConfiguration {

    @Bean
    ToolCallbackProvider mcpTools(CalculatorService calculatorService,
            DeviceLocationService deviceLocationService, HostedLayerService hostedLayerService,
            GeometryRegionService geometryRegionService, ConstructionProjectService constructionProjectService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(calculatorService, deviceLocationService, hostedLayerService, geometryRegionService, constructionProjectService)
                .build();
    }
}
