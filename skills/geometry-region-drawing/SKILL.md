---
name: geometry-region-drawing
description: 绘制组织机构管理范围、设备周边缓冲区或其他 GIS 几何区域时使用。
---

# 几何区域绘制

1. 调用 `drawGeometryRegion`。
2. 查看组织机构范围时，`target` 传组织名称，`bufferDistanceMeters` 必须传 `0`。
3. 绘制设备缓冲区时，`target` 传设备名称或 OID，`bufferDistanceMeters` 原样传用户指定的米数；未提供距离时先询问。
4. `COMPLETED` 时保留工具返回的 `DRAW_REGION` action 和 actionParams，由网页端绘制 GeoJSON 区域。
5. `NEED_CONFIRMATION` 时列出 candidates 并等待用户确认；`NOT_FOUND` 时请用户提供更准确的目标。
