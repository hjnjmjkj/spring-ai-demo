---
name: equipment-location
description: 定位线路、变电站或其他电网设备，并在地图中高亮时使用。
---

# 设备定位

1. 调用 `locateEquipment`。
2. `keyword` 传入用户提供的设备名称、OID 或设备 ID，不要擅自补全或改写。
3. `COMPLETED` 时保留工具返回的 `LOCATE_EQUIPMENT` action 和 actionParams，由网页端定位并高亮设备。
4. `NEED_CONFIRMATION` 时列出 candidates 并等待用户确认；`NOT_FOUND` 时请用户提供更准确的名称或 ID。
