# LingXi Agent（LangChain v1）

灵犀系统统一的 Python AI 执行层，基于 LangChain v1 / LangGraph v1。所有模型调用、Prompt 构造、结构化输出解析、短期记忆和模型能力规则都在这里实现；Java 后端负责用户权限、业务数据、任务状态、数据库事务、资产关系、人工确认和 HTTP 数据搬运。

## 职责边界

| 能力 | Python `lingXi-agent` | Java `lingXi-manage` |
|---|---|---|
| 文本/图片/视频模型调用 | 负责 | 不直连模型 |
| Prompt 模板与动态 Prompt | 负责 | 不拼接 Prompt |
| LLM JSON 解析与修复 | 负责 | 搬运已验证结果 |
| Wanx 时长、Prompt 长度、参考图数量等模型规则 | 负责 | 不保存模型能力分支 |
| 项目权限、人工确认、任务状态、事务 | 不负责 | 负责 |
| 人物/场景/分镜/资产关系及 OSS 转存 | 不负责 | 负责 |

模型名、密钥、Provider Base URL 和 Java→Agent 地址统一由
`dkd-parent/lingXi-admin/src/main/resources/application.yml` 配置，Java 按请求搬运给 Python；代码中不保留同一套模型默认值。

## AI 视频链路

1. Java 搬运章节原文、已有项目人物和当前视频模型到 Python。
2. `chapter_analysis` LCEL 链先生成可校验的章节骨架并分配源单元，再按场景生成分镜；每场最多 12 个源单元，失败时只局部修复该场景，最后由服务端组装 Story Bible 并做全局校验。
3. Python 最终化人物三视图、场景图、关键帧和视频 Prompt，并校验对白、人物引用顺序、单镜人数和模型时长。
4. Java 事务化写入章节、人物、场景、分镜、Prompt 草稿和资产关系；章节分析完成后不会自动生成图片。
5. 用户查看/修改图片 Prompt 后手动生成。Java 搬运资产数据和参考图 URL，Python 调用 Qwen Image。
6. 关键帧图片完成并经用户批准后，Java 才允许创建/修改视频草稿并手动提交。Python 按 Wanx 模型归一化参数并返回任务 ID，Java 负责持久化和轮询状态。

## LangChain v1 结构

```text
app/
├── agents/
│   ├── builder.py             # create_agent、ToolStrategy / ProviderStrategy
│   ├── middleware.py          # 动态 Prompt、模型路由、工具错误与摘要预算
│   ├── state.py               # AgentState 与不可变请求 Context
│   └── checkpoints.py         # InMemory / AsyncPostgresSaver 生命周期
├── chains/
│   ├── chapter_analysis.py    # 章节骨架、逐场景生成、局部修复及全局校验
│   └── business_chat.py       # 看板分析与快捷问题 LCEL 链
├── api/v1/
│   ├── chat.py                # 普通对话、结构化看板分析、快捷问题
│   ├── chapter.py             # 章节分析契约
│   └── video.py               # 图片生成、视频提交与查询
├── schemas/                   # Pydantic 请求/响应契约
└── services/
    ├── chapter_analysis.py    # Story Bible 业务契约与 Prompt 最终化
    └── video_capabilities.py  # Wanx/Qwen 模型能力单一来源
```

## 内部 API

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/chat/invoke` | 普通对话；`mode=context_analysis` 时由 Python 分析结构化业务数据 |
| POST | `/api/v1/chat/stream` | 对话或看板分析 SSE |
| POST | `/api/v1/chat/smart-questions` | 根据结构化对话历史返回严格 3 条快捷问题 |
| DELETE | `/api/v1/chat/thread` | 按 `{user_id, thread_id}` 永久删除会话 checkpoint |
| POST | `/api/v1/extract` | 显式 ToolStrategy / ProviderStrategy 结构化提取 |
| POST | `/api/v1/video/analyze-chapter` | 章节骨架规划、逐场景生成、局部修复及全局校验 |
| POST | `/api/v1/video/analyze-chapter/stream` | 同上，并以 NDJSON 持续返回阶段进度和最终结果 |
| POST | `/api/v1/video/generate-image` | Qwen Image 生成；参考图完整保序 |
| POST | `/api/v1/video/submit-video` | HappyHorse 多参考图视频提交与参数归一化 |
| POST | `/api/v1/video/query-video` | 异步视频供应商任务查询 |

`submission_uncertain=true` 表示供应商可能已经受理但没有返回任务 ID。Java 必须进入人工核对状态，不能自动重复提交。

除 `/health`、`/livez`、`/readyz` 外，全部接口都必须携带
`X-Agent-Service-Key`。它必须与 Java 服务的 `AGENT_SERVICE_API_KEY`
一致；Java `AgentConfig` 会在未设置 `agent.service-api-key` 时显式读取该
环境变量。请求中的供应商 `base_url` 只能使用 `OUTBOUND_ALLOWED_HOSTS`
明确列出的 HTTPS 目标；受信任供应商的业务空间子域名可按
`*.cn-beijing.maas.aliyuncs.com` 形式显式配置。

对话请求应同时传递 `user_id` 和 `thread_id`（兼容别名 `session_id`）。
前者用于用户隔离，后者才是会话标识；服务端会生成无歧义的 checkpoint
命名空间，禁止不同用户或会话共享短期记忆。SSE 使用 LangChain v1 的
`messages`、`updates`、`custom` 多模式流，并发送心跳、`done` 和最终
`[DONE]` 标记；`AGENT_STREAM_MAX_SECONDS` 与
`AGENT_STREAM_MAX_TEXT_CHARS` 分别限制总时长和累计文本输出。

## 短期记忆

本地与测试默认使用进程内存：

```dotenv
AGENT_CHECKPOINTER_BACKEND=memory
```

生产环境使用 PostgreSQL 持久化 checkpoint：

```dotenv
AGENT_CHECKPOINTER_BACKEND=postgres
AGENT_POSTGRES_DSN=postgresql://agent_user:password@postgres:5432/lingxi_agent
```

这两个配置统一由 Pydantic Settings 从进程环境或项目 `.env` 加载；DSN
使用 `SecretStr` 保存，不会出现在配置 repr 或日志中。进程启动时会初始化
`AsyncPostgresSaver` 所需表，并在退出时关闭连接。

## 本地运行

```powershell
cd lingXi-agent
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
Copy-Item .env.example .env
# 修改 .env，至少设置 OPENAI_API_KEY 与 AGENT_SERVICE_API_KEY
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 5000
```

Swagger 默认关闭；仅在受控开发环境设置 `DOCS_ENABLED=true` 后访问
`http://localhost:5000/docs`。

## 离线验证

测试使用本地 Fake/Runnable，不会请求真实文本、图片或视频模型：

```powershell
.\.venv\Scripts\python.exe -m unittest discover -s tests -v
.\.venv\Scripts\python.exe -m pytest -q
.\.venv\Scripts\python.exe -m pip check
```

Java 侧编译：

```powershell
mvn -pl dkd-parent/lingXi-manage -am -DskipTests compile
```
