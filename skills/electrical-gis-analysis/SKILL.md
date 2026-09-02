---
name: electrical-gis-analysis
description: 综合分析电网 GIS 数据、设备空间关系或地理区域，并需要组合多个地图工具时使用。
---

# 电网 GIS 分析

可按需读取 `references/analysis-checklist.md`，了解分析前需要确认的业务信息。

1. 先确认用户需要分析的设备、区域、时间范围和输出指标。
2. 需要定位设备时，使用 `locateEquipment`；需要组织范围或设备缓冲区时，使用 `drawGeometryRegion`。
3. 工具返回 `NEED_CONFIRMATION` 时，展示候选项并等待用户确认，不得自行选择。
4. 仅依据工具结果和知识库上下文给出结论；缺少数据时明确说明限制。
5. 计算器
