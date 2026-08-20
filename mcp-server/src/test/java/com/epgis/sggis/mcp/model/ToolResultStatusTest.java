package com.epgis.sggis.mcp.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ToolResultStatusTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void exposesCodeAndMessage() {
        assertThat(ToolResultStatus.COMPLETED.code()).isEqualTo("COMPLETED");
        assertThat(ToolResultStatus.COMPLETED.msg()).isEqualTo("执行完成");
    }

    @Test
    void exposesStableCodesAndMessagesForAllStatuses() {
        assertThat(ToolResultStatus.values()).allSatisfy(status -> {
            assertThat(status.code()).isEqualTo(status.name());
            assertThat(status.msg()).isNotBlank();
        });
        assertThat(ToolResultStatus.INVALID_ARGUMENT.msg()).isEqualTo("请求参数不合法");
        assertThat(ToolResultStatus.DEPENDENCY_ERROR.msg()).isEqualTo("依赖服务调用失败");
        assertThat(ToolResultStatus.TIMEOUT.msg()).isEqualTo("依赖服务调用超时");
    }

    @Test
    void serializesOnlyTheCodeAsAString() throws Exception {
        assertThat(objectMapper.writeValueAsString(ToolResultStatus.COMPLETED))
                .isEqualTo("\"COMPLETED\"");
    }
}
