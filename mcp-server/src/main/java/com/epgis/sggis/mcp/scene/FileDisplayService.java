package com.epgis.sggis.mcp.scene;

import com.epgis.sggis.mcp.model.ToolResultStatus;
import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/** 上传文件已由业务系统处理后，为页面返回对应的展示指令。 */
@Service
public class FileDisplayService {

    public static final String SHP_ACTION = "SHOW_SHP_LAYER";
    public static final String GLB_ACTION = "SHOW_GLB_MODEL";

    @Tool(description = """
            展示已上传并发布的 SHP 压缩包数据，例如鸟害区分布数据。
            当用户要求展示其补充的 SHP、Shape 或鸟害区分布数据时调用此工具。
            返回 SHOW_SHP_LAYER action，网页端据此加载并展示该图层。
            """)
    public FileDisplayResult showShapeData(
            @ToolParam(description = "待展示的已发布 SHP 数据名称，例如鸟害区分布数据") String dataName) {
        return completed(SHP_ACTION, "SHP", dataName);
    }

    @Tool(description = """
            预览已转换为 GLB 的 GIM 设备模型。
            当用户要求展示其上传的 GIM 模型或查看 GIM 模型预览效果时调用此工具。
            返回 SHOW_GLB_MODEL action，网页端据此加载 GLB 模型预览。
            """)
    public FileDisplayResult previewGimModel(
            @ToolParam(description = "待预览的已转换 GIM 模型名称，例如广州西门子能源变压器有限公司换流变压器设备") String modelName) {
        return completed(GLB_ACTION, "GLB", modelName);
    }

    private FileDisplayResult completed(String action, String resourceType, String resourceName) {
        if (resourceName == null || resourceName.isBlank()) {
            throw new IllegalArgumentException("展示资源名称不能为空");
        }
        FileDisplayActionParams actionParams = new FileDisplayActionParams(resourceName.trim(), resourceType, true);
        return new FileDisplayResult(ToolResultStatus.COMPLETED, action, actionParams, List.of(),
                ToolResultStatus.COMPLETED.msg());
    }

    public record FileDisplayActionParams(String resourceName, String resourceType, boolean autoLoad) { }

    public record FileDisplayResult(ToolResultStatus status, String action, FileDisplayActionParams actionParams,
            List<Object> candidates, String message) { }
}
