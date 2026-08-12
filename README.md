# Spring AI 2.0 + Qwen + MCP + RAG 示例

这是一个 Java 21 / Maven 多模块项目。网页 Agent 通过阿里云百炼的 OpenAI 兼容接口调用 Qwen，通过 Streamable HTTP 调用独立的计算器 MCP Server，并使用 PostgreSQL + PGVector 实现知识库检索增强生成（RAG）。

## 项目结构

```text
spring-ai-qwen-mcp-demo
├── docker/            # PGVector Compose 配置，宿主机端口 5555
├── mcp-server/        # 计算器 MCP Server，端口 8081，端点 /mcp
└── qwen-agent-web/    # Qwen Agent、RAG、REST/SSE API 和聊天网页，端口 8080
```

## 环境要求

- JDK 21
- Maven 3.9+
- Docker（用于 PGVector）
- 阿里云百炼 API Key

确认 Maven 使用 Java 21：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
mvn -version
```

## 配置

| 环境变量 | 必填 | 默认值 | 用途 |
| --- | --- | --- | --- |
| `DASHSCOPE_API_KEY` | 是 | 无 | 百炼 API Key，只在服务端读取 |
| `QWEN_BASE_URL` | 否 | `https://dashscope.aliyuncs.com/compatible-mode/v1` | OpenAI 兼容地址 |
| `QWEN_MODEL` | 否 | `qwen3.7-plus` | 支持 Function Calling 的 Qwen 模型 |
| `QWEN_EMBEDDING_MODEL` | 否 | `text-embedding-v4` | 知识入库和检索使用的向量模型 |
| `QWEN_EMBEDDING_DIMENSIONS` | 否 | `1024` | 向量维数；修改后需清空或迁移旧表 |
| `MCP_SERVER_URL` | 否 | `http://localhost:8081` | MCP Server 基础地址 |
| `PGVECTOR_HOST` | 否 | `localhost` | PostgreSQL 主机 |
| `PGVECTOR_PORT` | 否 | `5555` | PostgreSQL 宿主机端口 |
| `POSTGRES_DB` | 否 | `pgvector` | 数据库名 |
| `POSTGRES_USER` | 否 | `pgvector` | 数据库用户 |
| `POSTGRES_PASSWORD` | 否 | `pgvector` | 数据库密码 |

不要把真实 Key 写入 `application.yml`、`.env` 或提交到版本库。若 Key 曾出现在源码中，请在百炼控制台立即轮换。

PowerShell 示例：

```powershell
$env:DASHSCOPE_API_KEY = 'sk-your-key'
$env:QWEN_MODEL = 'qwen3.7-plus'
```

## 启动

先启动 PGVector：

```bash
docker compose -f docker/docker-compose.yml up -d
docker compose -f docker/docker-compose.yml ps
```

再启动 MCP Server：

```bash
mvn -pl mcp-server spring-boot:run
```

最后在另一个终端设置 `DASHSCOPE_API_KEY` 并启动 Agent：

```bash
mvn -pl qwen-agent-web spring-boot:run
```

浏览器访问 [http://localhost:8080](http://localhost:8080)。点击“添加知识”粘贴资料，保存后即可直接针对该资料提问；计算类问题仍会调用 MCP 的 `calculate` 工具。

## 电源追溯对话场景

MCP Server 提供三个必须连续调用的模拟工具：

1. `searchPowerEquipment`：模拟 `/datasearchservice/equipment/search`，按名称、OID 或设备ID检索设备。
2. `tracePowerSource`：模拟 `/powergridtoposervice/advancedanalysis/powersource`，返回从电源点到目标设备的有序路径。
3. `queryPowerEquipmentDetails`：按 `typeId` 分组模拟调用 `/queryservice/psrquery/objects`，补齐路径设备名称和属性。

前两步分别返回 `searchId` 和 `traceId`，后续工具必须携带这些标识，因此不能跳步。名称匹配到多个设备时，Agent 会先列出候选并等待用户确认。

可以在网页中尝试：

```text
查询厦门市同安益群服装加工厂#1变的上级电源
查询中闽线#20的电源来自哪里
查询一号配变的上级电源
```

第三条会命中“东区一号配变”和“西区一号配变”，用于演示多轮确认后继续调用。模拟数据只用于演示接口编排，不代表真实电网拓扑。

另外两个 GIS 场景也已经提供：

```text
查询中闽线下的所有配电变压器
查询厦门市思明区莲前西路859号周边1公里内的变电站和变压器
```

- 线路配变场景调用 `queryLineDistributionTransformers`，模拟 `/queryservice/psrquery/line/{psrid}/contains?typeids=302000`。
- 地址周边场景先调用 `geocodeAddress` 模拟 `/mapservice/geocode/geo`，再携带 `geocodeId` 调用 `queryNearbyPowerEquipment` 模拟 `/mapservice/spatial/equip/buffer`。
- 周边查询支持 `STATION`、`TRANSFORMER` 和 `ALL` 三种设备类别，结果按距离从近到远排列；半径范围为大于0且不超过50公里。

停止数据库：

```bash
docker compose -f docker/docker-compose.yml down
```

以上命令保留 `pgdata` 数据卷。只有明确需要删除全部知识数据时，才使用 `down -v`。

## RAG API

添加知识：

```bash
curl -X POST http://localhost:8080/api/knowledge/documents \
  -H "Content-Type: application/json" \
  -d '{"content":"产品 A 的保修期为三年。","metadata":{"source":"产品手册"}}'
```

测试检索：

```bash
curl -X POST http://localhost:8080/api/knowledge/search \
  -H "Content-Type: application/json" \
  -d '{"query":"产品 A 保修多久？","topK":5,"similarityThreshold":0.65}'
```

删除知识：

```bash
curl -X DELETE http://localhost:8080/api/knowledge/documents/{documentId}
```

知识文本最大 20000 字符。当前示例按一次提交生成一个向量文档，适合短资料；大型文件应在业务层先按段落切分后批量入库。

## 聊天 API

同步调用：

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"conversationId":"7f3ad908-937f-4d5f-b4d1-fd5aef7d44a1","message":"产品 A 保修多久？"}'
```

SSE 调用：

```bash
curl -N -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"conversationId":"7f3ad908-937f-4d5f-b4d1-fd5aef7d44a1","message":"计算 12.5 除以 4"}'
```

为规避 Spring AI 2.0.0 合并部分 Qwen 工具调用流式分片时的兼容问题，SSE 接口内部使用同步模型调用，并将完整回答作为一个 `token` 事件返回，因此页面会一次性显示答案。

## 构建与测试

```bash
mvn clean test
```

测试使用替身对象，不依赖真实 API Key、Qwen、PGVector 或运行中的 MCP Server。会话仅保存在内存中，每个会话最多保留最近 20 条消息，应用重启后清空；知识数据保存在 Docker 的 `pgdata` 数据卷中。
