---
name: hosted-layer-viewing
description: 查看指定应用下的托管图层或在三维场景中加载图层时使用。
---

# 托管图层查看

1. 调用 `viewHostedLayer`。
2. `applicationName` 传应用名称，`layerName` 传图层名称；信息缺失时先请用户补充。
3. `COMPLETED` 时保留工具返回的 `LOAD_HOSTED_LAYER` action 和 actionParams，由网页端加载图层。
4. `NEED_CONFIRMATION` 时列出 candidates 并等待用户确认；`NOT_FOUND` 时说明未找到图层。
