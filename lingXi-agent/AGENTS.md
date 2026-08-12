# AGENTS.md — LingXi Agent（Python AI 执行层）

## 项目概览

灵犀系统统一的 Python AI 执行层：基于 FastAPI + LangChain v1 / LangGraph v1，
负责所有模型调用、Prompt 构造、结构化输出解析、模型能力规则、短期记忆与知识检索。
Java 后端负责权限、业务数据、事务与人工确认，**不直连模型**；本服务**不读写业务数据库**。

## 技术栈

- Python 3.12 + FastAPI + uvicorn
- LangChain 1.x / LangGraph 1.x（仅 v1 API，禁止旧版兼容 API）
- Pydantic v2 + pydantic-settings
- 模型供应商：OpenAI 兼容（对话）、Qwen Image（图片）、HappyHorse（视频）
- 可选：Tavily（搜索）、Open-Meteo（天气）、PostgreSQL checkpoint / Store、Langfuse 观测

## 运行与测试（全部离线可跑）

```powershell
cd lingXi-agent
.\.venv\Scripts\python.exe -m pip install -r requirements.txt   # 依赖安装
.\.venv\Scripts\python.exe -m app.run                           # 本地启动（端口 5000）
.\.venv\Scripts\python.exe -m unittest discover -s tests -v     # 单元测试
.\.venv\Scripts\python.exe -m pytest -q
.\.venv\Scripts\python.exe -m pip check
```

测试只使用本地 Fake/Runnable，不调用真实模型。改完代码必须跑 `pip check`。

## 目录定位

| 功能 | 路径 | 说明 |
|------|------|------|
| FastAPI 入口 | `app/main.py` | 路由注册、全局异常处理、生命周期 |
| 配置管理 | `app/config/settings.py` | Pydantic Settings 从环境 / `.env` 加载，敏感项用 `SecretStr` |
| 对话 Agent | `app/agents/builder.py` | `create_agent`、ToolStrategy / ProviderStrategy |
| 小说 Agent | `app/agents/novel_builder.py` / `novel_prompts.py` | 小说工作流独立构建 |
| Agent 中间件 | `app/agents/middleware.py` | 动态 Prompt、模型路由、工具错误与摘要预算 |
| Agent 状态 | `app/agents/state.py` | v1 AgentState 与不可变请求 Context |
| 短期记忆 | `app/agents/checkpoints.py` | InMemory / AsyncPostgresSaver 生命周期 |
| 长期偏好 | `app/agents/stores.py` | InMemory / AsyncPostgresStore 生命周期 |
| 章节分析链 | `app/chains/chapter_analysis.py` | 骨架规划、逐场景生成、局部修复与全局校验 |
| 业务对话链 | `app/chains/business_chat.py` | 看板分析与快捷问题 LCEL 链 |
| 提示词 | `app/chains/promt.py` | 章节规划、场景生成与修复提示词 |
| API 路由 | `app/api/v1/` | `chat.py` / `chapter.py` / `video.py` 等 |
| 依赖注入 | `app/api/dependencies.py` | LLM/Agent 单例管理 |
| 请求/响应契约 | `app/schemas/` | Pydantic 请求响应模型 |
| 章节契约 | `app/services/chapter_analysis.py` | Story Bible 业务契约与 Prompt 最终化 |
| 模型能力 | `app/services/video_capabilities.py` | Wanx/Qwen 模型能力单一来源 |
| 记忆服务 | `app/services/memory.py` | 受控长期偏好召回、写入与清空 |
| 认证 | `app/security/` | X-Agent-Service-Key 校验 |
| 观测 | `app/observability/` | Langfuse 上报（默认关闭） |
| 工具 | `app/agents/tools/` | 通用工具、搜索、天气、知识检索、业务工具 |

## API 接口

除 `/health`、`/livez`、`/readyz` 外全部需要 `X-Agent-Service-Key`：

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/chat/invoke` | 同步对话；`mode=context_analysis` 时分析结构化业务数据 |
| POST | `/api/v1/chat/stream` | 对话 / 看板分析 SSE |
| POST | `/api/v1/chat/smart-questions` | 返回严格 3 条快捷问题 |
| DELETE | `/api/v1/chat/thread` | 按 `{user_id, thread_id}` 永久删除会话 checkpoint |
| POST | `/api/v1/chat/memory/list` / `PUT .../preference` / `DELETE /api/v1/chat/memory` | 长期回答偏好管理 |
| POST | `/api/v1/extract` | ToolStrategy / ProviderStrategy 结构化提取 |
| POST | `/api/v1/video/analyze-chapter` 及其 `/stream` | 章节分析（NDJSON 进度） |
| POST | `/api/v1/video/generate-image` | Qwen Image 生成，参考图完整保序 |
| POST | `/api/v1/video/submit-video` | HappyHorse 多参考图视频提交 |
| POST | `/api/v1/video/query-video` | 异步视频任务查询 |
| POST | `/api/v1/novel/write/stream` | 小说创作 SSE（Agent 图 + 作品会话 checkpoint） |
| POST | `/api/v1/novel/synopsis/generate` | 直接生成 200~400 字故事梗概，不走 Agent 图 |
| POST | `/api/v1/novel/synopsis/stream` | 梗概逐 token SSE，不进 Agent 图、无会话记忆 |
| POST | `/api/v1/novel/outline/generate` | 三层大纲（全书→卷→章）+ 断链检查，供人工确认 |
| DELETE | `/api/v1/novel/thread` | 按 `{user_id, thread_id}` 删除小说作品会话 checkpoint |

## 关键约定

- 对话请求必须同时传 `user_id` 与 `thread_id`（兼容别名 `session_id`）；用户身份放不可变
  Context，会话身份放 `thread_id`，两者不得混用，禁止跨用户/会话共享 checkpoint。
- SSE 使用 LangChain v1 的 `messages` / `updates` / `custom` 多模式流，发送心跳、`done`、
  最终 `[DONE]`；注意 `AGENT_STREAM_MAX_SECONDS` 与 `AGENT_STREAM_MAX_TEXT_CHARS` 限制。
- 请求中的供应商 `base_url` 只能命中 `OUTBOUND_ALLOWED_HOSTS` 白名单（HTTPS）；
  业务空间子域名按 `*.cn-beijing.maas.aliyuncs.com` 显式配置，不要关闭白名单。
- 服务密钥 `AGENT_SERVICE_API_KEY` 必须与 Java `agent.service-api-key` 一致；
  密钥与 DSN 用 `SecretStr`，不得出现在 Prompt、响应、日志或 repr 中。
- `submission_uncertain=true` 表示供应商可能已受理但未返回任务 ID：Java 必须人工核对，
  不要自动重试提交。
- 配置与代码分离：模型名、密钥、Base URL 由 Java 按请求搬运，代码中不保留模型默认值。
- 统一 JSON 错误信封 `{success, error: {code, message}}`，日志含 `request_id`。
- 不使用 `langgraph.prebuilt.create_react_agent` 等旧版兼容 API。
- `.env`、`.env.example` 内容禁止进入 diff；新增配置项必须同步更新 `.env.example`。

## 职责边界（对照 Java）

| 能力 | Python `lingXi-agent` | Java `lingXi-manage` |
|------|----------------------|----------------------|
| 文本/图片/视频模型调用 | 负责 | 不直连模型 |
| Prompt 模板与动态 Prompt | 负责 | 不拼接 Prompt |
| LLM JSON 解析与修复 | 负责 | 搬运已验证结果 |
| 模型能力规则（时长、参考图数量等） | 负责 | 不保存模型能力分支 |
| 项目权限、人工确认、任务状态、事务 | 不负责 | 负责 |
| 人物/场景/分镜/资产关系及 OSS 转存 | 不负责 | 负责 |
