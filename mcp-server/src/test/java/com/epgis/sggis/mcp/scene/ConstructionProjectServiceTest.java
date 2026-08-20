package com.epgis.sggis.mcp.scene;

import static org.assertj.core.api.Assertions.assertThat;

import com.epgis.sggis.mcp.model.ToolResultStatus;
import org.junit.jupiter.api.Test;

class ConstructionProjectServiceTest {

    @Test
    void returnsProjectModelsAndSelectsFirstModelByDefault() {
        var result = new ConstructionProjectService().browseConstructionProject("BUILD-2026-001");

        assertThat(result.status()).isEqualTo(ToolResultStatus.COMPLETED);
        assertThat(result.action()).isEqualTo(ConstructionProjectService.ACTION);
        assertThat(result.actionParams().models()).hasSize(2);
        assertThat(result.actionParams().defaultModel().modelId()).isEqualTo("MODEL-001-A");
        assertThat(result.actionParams().autoLoadDefaultModel()).isTrue();
    }

    @Test
    void asksForConfirmationWhenProjectNameIsAmbiguous() {
        var result = new ConstructionProjectService().browseConstructionProject("同安");

        assertThat(result.status()).isEqualTo(ToolResultStatus.NEED_CONFIRMATION);
        assertThat(result.action()).isNull();
        assertThat(result.candidates()).hasSize(2);
    }
}
