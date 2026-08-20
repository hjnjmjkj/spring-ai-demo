package com.epgis.sggis.mcp.model;

import com.fasterxml.jackson.annotation.JsonValue;

/** MCP 业务工具的统一执行状态。 */
public enum ToolResultStatus {
    COMPLETED("COMPLETED", "执行完成"),
    READY_FOR_NEXT_STEP("READY_FOR_NEXT_STEP", "已就绪，可继续下一步"),
    NEED_CONFIRMATION("NEED_CONFIRMATION", "存在多个候选，请确认"),
    NOT_FOUND("NOT_FOUND", "未找到匹配结果"),
    INVALID_ARGUMENT("INVALID_ARGUMENT", "请求参数不合法"),
    DEPENDENCY_ERROR("DEPENDENCY_ERROR", "依赖服务调用失败"),
    TIMEOUT("TIMEOUT", "依赖服务调用超时");

    private final String code;
    private final String msg;

    ToolResultStatus(String code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    @JsonValue
    public String code() {
        return code;
    }

    public String msg() {
        return msg;
    }
}
