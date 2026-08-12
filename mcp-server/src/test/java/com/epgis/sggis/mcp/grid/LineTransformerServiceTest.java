package com.epgis.sggis.mcp.grid;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LineTransformerServiceTest {

    @Test
    void returnsAllDistributionTransformersUnderLine() {
        var result = new LineTransformerService().queryLineDistributionTransformers("中闽线");

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.endpoint()).isEqualTo(LineTransformerService.LINE_CONTAINS_ENDPOINT);
        assertThat(result.line().oid()).isEqualTo("1516289");
        assertThat(result.transformers())
                .extracting(LineTransformerService.DistributionTransformer::name)
                .containsExactly(
                        "厦门市同安益群服装加工厂#1变",
                        "中闽线#1公变",
                        "中闽线#2公变");
    }

    @Test
    void asksForConfirmationWhenLineNameIsAmbiguous() {
        var result = new LineTransformerService().queryLineDistributionTransformers("春山线");

        assertThat(result.status()).isEqualTo("NEED_CONFIRMATION");
        assertThat(result.candidates()).extracting(LineTransformerService.LineCandidate::oid)
                .containsExactly("1612457", "1612999");
    }
}
