package com.epgis.sggis.mcp.scene;

import static org.assertj.core.api.Assertions.assertThat;

import com.epgis.sggis.mcp.model.ToolResultStatus;
import org.junit.jupiter.api.Test;

class HostedLayerServiceTest {

    @Test
    void returnsThreeDimensionalLayerAction() {
        var result = new HostedLayerService().viewHostedLayer("配网运检应用", "设备缺陷图层");

        assertThat(result.status()).isEqualTo(ToolResultStatus.COMPLETED);
        assertThat(result.action()).isEqualTo(HostedLayerService.ACTION);
        assertThat(result.actionParams().layer().layerId()).isEqualTo(92);
        assertThat(result.actionParams().sceneMode()).isEqualTo("3D");
        assertThat(result.endpointCalls()).extracting(HostedLayerService.EndpointCall::endpoint)
                .containsExactly(
                        HostedLayerService.APPLICATION_LIST_ENDPOINT,
                        HostedLayerService.LAYER_LIST_ENDPOINT);
    }

    @Test
    void returnsNotFoundWithoutFrontendAction() {
        var result = new HostedLayerService().viewHostedLayer("不存在的应用", "不存在的图层");

        assertThat(result.status()).isEqualTo(ToolResultStatus.NOT_FOUND);
        assertThat(result.action()).isNull();
    }
}
