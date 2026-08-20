package com.epgis.sggis.mcp.scene;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConstructionProjectServiceTest {

    @Test
    void returnsProjectModelsAndSelectsFirstModelByDefault() {
        var result = new ConstructionProjectService().browseConstructionProject("BUILD-2026-001");

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.action()).isEqualTo(ConstructionProjectService.ACTION);
        assertThat(result.actionParams().models()).hasSize(2);
        assertThat(result.actionParams().defaultModel().modelId()).isEqualTo("MODEL-001-A");
        assertThat(result.actionParams().autoLoadDefaultModel()).isTrue();
    }

    @Test
    void asksForConfirmationWhenProjectNameIsAmbiguous() {
        var result = new ConstructionProjectService().browseConstructionProject("同安");

        assertThat(result.status()).isEqualTo("NEED_CONFIRMATION");
        assertThat(result.action()).isNull();
        assertThat(result.candidates()).hasSize(2);
    }
}
