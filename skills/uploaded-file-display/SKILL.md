---
name: uploaded-file-display
description: 展示用户已上传并处理完成的 SHP 空间数据或 GIM 设备模型时使用。
---

# 已上传文件展示

1. 用户要求展示已上传、已发布的 SHP/Shape 数据（包括鸟害区分布数据）时，调用 `showShapeData`，`dataName` 传数据名称。
2. 用户要求预览已上传的 GIM 模型时，调用 `previewGimModel`，`modelName` 传设备或模型名称。
3. 工具返回 `COMPLETED` 时，不要改写 `action` 或 `actionParams`；告知用户页面已加载展示指令。
4. 本技能不负责上传、发布、解压 SHP ZIP 或将 GIM 转换为 GLB。
