# Spring AI 2.0 + Qwen + MCP + RAG 示例

这是一个 Java 21 / Maven 多模块项目。网页 Agent 通过阿里云百炼的 OpenAI 兼容接口调用 Qwen，通过 Streamable HTTP 调用独立的 GIS MCP Server，并使用 PostgreSQL + PGVector 实现知识库检索增强生成（RAG）。

## 项目结构

```text
spring-ai-qwen-mcp-demo
├── docker/            # PGVector Compose 配置，宿主机端口 5555
├── mcp-server/        # 计算和 GIS 场景 MCP Server，端口 8081，端点 /mcp
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
| `AGENT_SKILLS_DIRECTORY` | 否 | `skills` | Agent Skills 根目录（相对于应用工作目录） |
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

## Agent Skills

Agent Skills 位于 `skills/<skill-name>/SKILL.md`。每个文件必须使用 UTF-8 编码，并以 YAML front matter 开头；目录名与 `name` 必须一致，且仅可使用小写字母、数字和连字符：

```markdown
---
name: electrical-gis-analysis
description: 分析电网 GIS 数据、设备空间关系或地理区域时使用。
---

# 电网 GIS 分析

这里写入技能的完整操作指令。
```

每次对话都会重新扫描技能目录。模型先看到技能的名称和描述；任务匹配时会调用 `loadSkill` 读取对应的完整 `SKILL.md`，因此无需重启服务即可添加、修改或删除技能。无效的 front matter、目录名不匹配和符号链接会被忽略；`loadSkill` 只能读取当前发现的技能，不能读取目录外文件。可通过 `AGENT_SKILLS_DIRECTORY` 指定其他技能根目录。

每个 Skill 目录还可包含 `references/`、`examples/` 和 `templates/` 等辅助文本。`loadSkillResource(skillName, path)` 可按需读取已发现 Skill 目录内的 `.md`、`.txt`、`.json`、`.yaml` 或 `.yml` 文件，单文件最大 100 KB。该工具拒绝符号链接、路径遍历、目录外路径和其他文件类型；脚本不会自动执行。

项目内置了 `mathematical-calculation`、`equipment-location`、`hosted-layer-viewing`、`geometry-region-drawing`、`construction-project-browsing` 和 `electrical-gis-analysis` 六项业务技能。核心系统提示词仅定义身份、知识库优先和地图结果安全约束；各业务工具的调用参数及流程由对应 Skill 管理。

## MCP 工具和地图场景

MCP Server 当前提供五个聚合工具：

1. `calculate`：执行精确的两数四则运算。
2. `locateEquipment`：按设备名称、OID 或设备ID查询并返回 `LOCATE_EQUIPMENT` 地图动作。
3. `viewHostedLayer`：按应用名称和图层名称查询并返回 `LOAD_HOSTED_LAYER` 地图动作。
4. `drawGeometryRegion`：查询组织边界或生成设备缓冲区，返回 `DRAW_REGION` 地图动作。
5. `browseConstructionProject`：查询基建工程及模型服务，返回 `SHOW_CONSTRUCTION_PROJECT` 地图动作。

地图工具统一返回 `status`、`action`、`actionParams`、`candidates` 和 `message`。`status` 为
`NEED_CONFIRMATION` 时，Agent 必须先让用户从 `candidates` 中确认目标；只有 `COMPLETED` 结果才应交给前端执行地图动作。

可以在网页中尝试：

```text
定位中闽线
打开配网运检应用中的设备缺陷图层
绘制厦门市供电公司的管理范围
绘制中闽线周边500米缓冲区
查看厦门同安输变电工程
```

以上数据和业务接口调用记录均为演示数据，不代表真实电网和工程数据。

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

同步响应同时包含模型文字和本轮所有 MCP 工具调用的结构化结果：

```json
{
  "conversationId": "7f3ad908-937f-4d5f-b4d1-fd5aef7d44a1",
  "content": "已找到目标设备。",
  "toolResults": [
    {
      "toolName": "locateEquipment",
      "arguments": { "keyword": "中闽线" },
      "result": {
        "status": "COMPLETED",
        "action": "LOCATE_EQUIPMENT",
        "actionParams": {
          "equipment": { "oid": "1516289", "name": "中闽线" },
          "geometry": { "type": "LineString", "coordinates": [] },
          "highlight": true
        },
        "candidates": []
      }
    }
  ]
}
```

没有调用 MCP 工具时，`toolResults` 为空数组。工具返回合法 JSON 时，`arguments` 和 `result` 保持对象、数组、数字和布尔值等原始 JSON 类型；非 JSON 返回值按字符串传递。

SSE 调用：

```bash
curl -N -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"conversationId":"7f3ad908-937f-4d5f-b4d1-fd5aef7d44a1","message":"计算 12.5 除以 4"}'
```

为规避 Spring AI 2.0.0 合并部分 Qwen 工具调用流式分片时的兼容问题，SSE 接口内部使用同步模型调用，并将完整回答作为一个 `token` 事件返回，因此页面会一次性显示答案。

SSE 按以下顺序返回事件：每次 MCP 调用对应一个 `tool` 事件，其 `data` 就是上例中的单条工具结果；随后通过 `token` 返回 `{"content":"..."}`，最后通过 `done` 表示结束。错误仍通过 `error` 事件返回。

### 前端调用示例

前端应为同一段连续对话复用同一个 UUID；新建会话时再生成新的 UUID：

```javascript
function getConversationId() {
  let id = localStorage.getItem('conversationId');
  if (!id) {
    id = crypto.randomUUID();
    localStorage.setItem('conversationId', id);
  }
  return id;
}
```

普通 JSON 调用：

```javascript
async function chat(message) {
  const response = await fetch('/api/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      conversationId: getConversationId(),
      message
    })
  });

  const body = await response.json();
  if (!response.ok) {
    throw new Error(body.message || '对话请求失败');
  }

  // 显示模型生成的文字。
  document.querySelector('#answer').textContent = body.content;

  // 一次 Agent 调用可能执行多个 MCP 工具。
  body.toolResults.forEach(handleToolResult);
  return body;
}
```

地图工具应先检查 `status`，再根据稳定的 `action` 字段分发给不同业务组件，不要依赖可能带 MCP 前缀的工具名：

```javascript
function handleToolResult(tool) {
  const result = tool.result;
  if (!result) return;

  if (result.status === 'NEED_CONFIRMATION') {
    renderCandidates(result.candidates || []);
    return;
  }
  if (result.status !== 'COMPLETED') {
    showMessage(result.message || '地图操作未完成');
    return;
  }

  const handler = {
    LOCATE_EQUIPMENT: locateEquipment,
    LOAD_HOSTED_LAYER: loadHostedLayer,
    DRAW_REGION: drawRegion,
    SHOW_CONSTRUCTION_PROJECT: showConstructionProject
  }[result.action];

  if (handler) {
    handler(result.actionParams);
    return;
  }

  // 未单独适配的工具先按 JSON 展示，便于调试和向后兼容。
  document.querySelector('#tool-json').textContent =
    JSON.stringify(tool, null, 2);
}
```

SSE 接口是 `POST` 请求，浏览器原生 `EventSource` 不能携带该请求体，因此使用 `fetch` 读取响应流：

```javascript
async function streamChat(message, handlers = {}) {
  const response = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'text/event-stream'
    },
    body: JSON.stringify({
      conversationId: getConversationId(),
      message
    })
  });

  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new Error(body.message || '流式对话请求失败');
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    const { value, done } = await reader.read();
    buffer += decoder.decode(value || new Uint8Array(), { stream: !done });

    const frames = buffer.split(/\r?\n\r?\n/);
    buffer = frames.pop() || '';
    frames.forEach(frame => handleSseFrame(frame, handlers));

    if (done) {
      if (buffer.trim()) handleSseFrame(buffer, handlers);
      break;
    }
  }
}

function handleSseFrame(frame, handlers) {
  let eventName = 'message';
  const dataLines = [];

  frame.split(/\r?\n/).forEach(line => {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim();
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart());
    }
  });

  if (!dataLines.length) return;
  const data = JSON.parse(dataLines.join('\n'));

  if (eventName === 'tool') handlers.onTool?.(data);
  if (eventName === 'token') handlers.onToken?.(data.content || '');
  if (eventName === 'done') handlers.onDone?.();
  if (eventName === 'error') throw new Error(data.content || '流式对话失败');
}
```

调用示例：

```javascript
const answer = document.querySelector('#answer');
answer.textContent = '';

await streamChat('查询设备 436289 的上级电源', {
  onTool: handleToolResult,
  onToken: content => { answer.textContent += content; },
  onDone: () => { console.log('本次回答完成'); }
});
```

以上示例使用相对路径，适用于前端和服务端同源部署。如果前端部署在其他域名，需要将地址改为完整的服务端 URL，并在服务端配置允许该前端域名跨域访问。

## 构建与测试

```bash
mvn clean test
```

测试使用替身对象，不依赖真实 API Key、Qwen、PGVector 或运行中的 MCP Server。会话仅保存在内存中，每个会话最多保留最近 20 条消息，应用重启后清空；知识数据保存在 Docker 的 `pgdata` 数据卷中。
