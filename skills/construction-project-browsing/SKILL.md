---
name: construction-project-browsing
description: 查询或浏览基建工程、工程模型及默认模型服务时使用。
---

# 基建工程浏览

1. 调用 `browseConstructionProject`。
2. `projectNameOrId` 传工程名称或工程 ID，不要凭空推断。
3. `COMPLETED` 时保留工具返回的 `SHOW_CONSTRUCTION_PROJECT` action 和 actionParams，由网页端加载工程和默认模型。
4. `NEED_CONFIRMATION` 时列出 candidates 并等待用户确认；`NOT_FOUND` 时请用户提供更准确的工程名称或 ID。
