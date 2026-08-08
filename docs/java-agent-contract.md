# Java ↔ Agent 服务契约（v1）

> 版本：v1.0
> 日期：2026-08-08
> 状态：基线（与 2026-08-08 源码一致）
> 适用范围：`dkd-parent/lingXi-manage` 与 `lingXi-agent` 之间的全部 HTTP/SSE 交互

本文是阶段 0 的交付物：以一份独立、可审核的文档固定 Java 与 Python Agent
之间的请求、响应、SSE 事件和错误码契约。实现必须与本文一致；任何语义变更
按「先扩展、后切换、再收缩」流程升级版本，不允许隐式改契约。

## 1. 标识符约定

| 标识符 | 格式 | 生成位置 | 说明 |
|---|---|---|---|
| `request_id` | 宽松 `[A-Za-z0-9._-]{1,128}`；Java 生成值为 `req-[a-f0-9]{32}` | 最外层 HTTP 入口 | Java `RequestIdFilter` 透传合法 `X-Request-Id`，否则生成；写入 MDC 与响应头 |
| `X-Request-Id` 头 | 同 `request_id` | Java → Agent 透传 | Agent `request_logging_middleware` 校验并采用，回写响应头 |
| `agent_request_id` | `req-[a-f0-9]{32}` | Java（工具令牌签发） | 与工具令牌绑定；Java 复用 HTTP 入口 `request_id`（当格式匹配时），否则新生成 |
| `thread_id` | `[A-Za-z0-9][A-Za-z0-9._:@/-]{0,127}` | Java 映射 | Agent Checkpoint 会话隔离；请求体接受别名 `session_id` |
| `user_id` | 1–128 字符 | Java（登录态） | 用户隔离，与 `thread_id` 不可混用 |
| `event_id` | 事件生产方 | 事件生产方 | 回调与消息消费去重 |

同一链路约定：浏览器请求 → Java 生成/透传 `request_id` →
（`X-Request-Id` 头 + `agent_request_id` 字段）→ Agent 日志与错误响应 →
回写浏览器响应头。一次用户请求在 Java 日志、Agent 日志、错误信封中应可检索到同一标识。

## 2. 认证与服务间头

所有非健康检查接口必须携带：

| 请求头 | 必填 | 说明 |
|---|---|---|
| `X-Agent-Service-Key` | 是 | 与 Java `AGENT_SERVICE_API_KEY` 一致；Agent 端缺少时返回 401 |
| `X-Request-Id` | 否 | 透传链路标识；非法值由 Agent 重新生成 |
| `Content-Type` | 是 | `application/json; charset=UTF-8`（SSE 为 `text/event-stream`） |

健康检查：`GET /health`、`GET /livez`、`GET /readyz`。

## 3. 统一响应信封

### 3.1 成功

```json
{
  "success": true,
  "message": "ok",
  "data": { }
}
```

### 3.2 失败（HTTP 非 2xx 或 `success=false`）

```json
{
  "success": false,
  "error": {
    "code": "STABLE_ERROR_CODE",
    "message": "面向用户的安全文本"
  },
  "request_id": "req-0123456789abcdef0123456789abcdef"
}
```

规则：

- 错误消息必须是稳定、面向用户的安全文本，不包含堆栈、供应商原始响应或凭据；
- Java `AgentResponseUtil.normalizeError` 兼容 `error: {code, message}` 与
  扁平 `error_code`/`error` 两种形态，并截断超长消息；
- Java 兜底错误码仅在 Agent 返回无法解析的响应时出现。

## 4. HTTP API 清单（v1）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/chat/invoke` | 同步对话；`mode=context_analysis` 时分析结构化业务数据 |
| POST | `/api/v1/chat/stream` | 对话 SSE（聚合文本） |
| POST | `/api/v1/chat/stream/v2` | 对话 SSE（结构化白名单事件） |
| POST | `/api/v1/chat/smart-questions` | 由结构化历史生成严格 3 条快捷问题 |
| POST | `/api/v1/chat/resume` | 受控动作批准/拒绝后恢复 LangGraph checkpoint |
| DELETE | `/api/v1/chat/thread` | 按 `{user_id, thread_id}` 删除 checkpoint |
| POST | `/api/v1/chat/memory/list` | 查看当前用户长期回答偏好 |
| PUT | `/api/v1/chat/memory/preference` | 修改一项长期回答偏好 |
| DELETE | `/api/v1/chat/memory` | 幂等清空当前用户长期偏好 |
| POST | `/api/v1/extract` | ToolStrategy / ProviderStrategy 结构化提取 |
| POST | `/api/v1/video/analyze-chapter` | 章节骨架规划、逐场景生成与全局校验 |
| POST | `/api/v1/video/analyze-chapter/stream` | 章节分析 NDJSON 进度流 |
| POST | `/api/v1/video/generate-image` | Qwen Image 生成（参考图保序） |
| POST | `/api/v1/video/submit-video` | HappyHorse 视频提交与参数归一化 |
| POST | `/api/v1/video/query-video` | 异步视频任务查询 |

小说链路：

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/novel/synopsis/generate` | 按书名直接调用 LLM 生成梗概（不进 Agent） |
| POST | `/api/v1/novel/synopsis/stream` | 同上，SSE token 流 |
| POST | `/api/v1/novel/write/stream` | 小说创作智能体结构化事件流 |
| DELETE | `/api/v1/novel/thread` | 删除小说作品会话 checkpoint |

路径前缀 `/api/v1` 即 API 版本。新增兼容字段不升版本；删除字段、
改变语义或新增必需字段时升级版本号。

## 5. 错误码

### 5.1 Agent 产生（稳定）

| 错误码 | HTTP | 含义 | 处置 |
|---|---|---|---|
| `VALIDATION_ERROR` | 422 | 请求体校验失败（Pydantic） | 客户端修正请求 |
| `CONFIG_ERROR` | 500 | Agent 配置无效 | 运维修复配置 |
| `SEARCH_ERROR` | 502 | 搜索供应商失败 | 重试或降级 |
| `TOOL_EXECUTION_ERROR` / `TOOL_*` | 502 | 工具执行失败（`TOOL_` 前缀） | 按工具策略重试 |
| `AGENT_TIMEOUT` | 504 | 超过最大迭代次数 | 标记失败，人工重试 |
| `MODEL_UNAVAILABLE` | 503 | 模型不可达 | 熔断并告警 |
| `INTERNAL_ERROR` | 500 | 未预期异常 | 告警，不向客户端泄露详情 |

### 5.2 Java 兜底（仅在 Agent 响应不可解析时出现）

| 错误码 | 场景 |
|---|---|
| `AGENT_CHAT_FAILED` | 同步对话失败 |
| `AGENT_STREAM_HTTP_ERROR` | 流式请求 HTTP 层失败 |
| `AGENT_STREAM_HTTP_ERROR`（小说流同码） | 小说/梗概流失败 |
| `AGENT_SYNOPSIS_FAILED` | 梗概生成失败 |
| `AGENT_SYNOPSIS_STREAM_ERROR` | 梗概流失败 |
| `AGENT_SMART_QUESTIONS_FAILED` | 快捷问题失败 |
| `AGENT_THREAD_DELETE_FAILED` | checkpoint 删除失败 |
| `AGENT_MEMORY_LIST_FAILED` / `AGENT_MEMORY_UPDATE_FAILED` / `AGENT_MEMORY_CLEAR_FAILED` | 长期记忆接口失败 |
| `IMAGE_OCR_FAILED` | 图片 OCR 失败 |
| `AGENT_HTTP_ERROR` | 非 2xx 且无法解析信封 |

规则：新增错误码必须同时更新 Java 侧契约测试；错误码名称只使用
`[A-Z0-9_]`，不得超过 64 字符。

## 6. SSE 事件契约

### 6.1 传输

- 媒体类型 `text/event-stream`，UTF-8；
- 每个事件一行 `data: <json>`，事件间空行分隔；
- 流终止标记：事件 `{"type":"done"}` 后追加 `data: [DONE]`；
- 正常终止或异常终止必须发出 `done` / `error` 之一；Java 侧缺少终止标记视为失败；
- 心跳事件用于保活，不持久化。

### 6.2 白名单事件（Java 只转发以下类型）

| 事件 | 含义 | 是否持久化 | Java 转发字段 |
|---|---|---|---|
| `token` | 可展示文本增量 | 聚合后写助手消息 | `type`、`content` |
| `tool_start` | 工具开始 | 仅审计摘要 | `type`、`tool`、`data.status=started` |
| `tool_progress` | 工具进度 | 仅审计摘要 | `type`、`tool`、`data.status`、`data.result_count` |
| `tool_end` | 工具完成 | 仅审计摘要 | `type`、`tool`、`data.status`、`data.result_count` |
| `citation` | 知识引用 | 仅审计摘要 | `type`、`data.{title,section,version,source_id,score}` |
| `clarification` | 澄清请求 | 建议持久化 | `type`、`content` |
| `memory_saved` | 偏好已保存 | 保存事件不复制正文 | `type`、`data.{preference,value}` |
| `approval_required` | 受控写操作待确认 | 必须持久化动作提案 | `type`、`data.{action_id,action_type,status,description,impact,expires_at,target.inner_code}` |
| `action_completed` / `action_rejected` | 动作结果 | 持久化结果 | `type`、`data.{action_id,status,result.task_id,result.task_code}` |
| `heartbeat` | 保活 | 不持久化 | `type` |
| `done` | 正常终止 | 推进消息为 SUCCEEDED | `type`、`content`（可选） |
| `error` | 异常终止 | 推进消息为 FAILED | `type`、`content` |

Java 白名单实现：`AgentClient.isStructuredEvent` 与 `sendStructuredEvent`
重建事件，禁止透传工具原始参数、内部节点和任意扩展字段。

新增事件必须保持旧客户端可忽略；删除或改变语义时升级 API 版本。

## 7. 请求体边界（Agent 校验）

| 限制 | 值 |
|---|---|
| 单条消息 | ≤ 32 000 字符 |
| `context_data` | ≤ 256 KB（UTF-8 编码后） |
| 附件 | ≤ 5 个；图片 ≤ 10 MB；文档提取文本 ≤ 60 000 字符 |
| 权限码 | ≤ 256 个且去重 |
| 提取文本 | ≤ 32 000 字符 |
| 作品上下文 | ≤ 256 KB；设定卡 ≤ 60 条 |
| 请求体总量 | ≤ 2 MB（`MAX_REQUEST_BODY_BYTES`） |

## 8. 契约测试与离线验证

契约测试由 Java 侧 `AgentClientContractTest` 承担：使用本地 `HttpServer`
模拟 Agent，覆盖同步、流式、SSE 终止标记、错误信封归一化、工具令牌生命周期。
运行不访问真实模型。

离线验证命令（CI 与本地一致）：

```text
# Java（包含契约测试）
mvn -pl lingXi-admin -am test

# Agent
python -m pip check
python -m pytest -q
python -m compileall app
```

发布门禁：

1. Java↔Agent 契约测试必须通过；
2. 任何请求体字段、SSE 事件、错误码变更必须同步修改本文档版本；
3. 版本升级遵循「先扩展、后切换、再收缩」发布顺序。

## 9. 变更流程

1. 提交契约变更说明（本文档 + ADR），标注新增/删除/语义变化；
2. 先发布 Agent（兼容旧、新契约）；
3. 发布 Java 使用新契约；
4. 发布前端启用新交互；
5. 观察至少一个稳定周期后移除旧字段。

## 10. 相关实现

- [Java Agent 客户端](../dkd-parent/lingXi-manage/src/main/java/com/lingXi/ai/client/AgentClient.java)
- [Java 响应归一化](../dkd-parent/lingXi-manage/src/main/java/com/lingXi/ai/client/AgentResponseUtil.java)
- [Java 契约测试](../dkd-parent/lingXi-manage/src/test/java/com/lingXi/ai/client/AgentClientContractTest.java)
- [Java request_id 过滤器](../dkd-parent/lingXi-framework/src/main/java/com/dkd/framework/web/filter/RequestIdFilter.java)
- [Agent 请求契约](../lingXi-agent/app/schemas/request.py)
- [Agent 响应契约](../lingXi-agent/app/schemas/response.py)
- [Agent 错误处理](../lingXi-agent/app/utils/exceptions.py)
- [Agent 日志与 request_id](../lingXi-agent/app/utils/logger.py)
