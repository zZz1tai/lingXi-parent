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
│   ├── checkpoints.py         # InMemory / AsyncPostgresSaver 生命周期
│   └── stores.py              # InMemory / AsyncPostgresStore 生命周期
├── chains/
│   ├── chapter_analysis.py    # 章节骨架、逐场景生成、局部修复及全局校验
│   ├── promt.py               # 章节规划、场景生成与修复提示词
│   └── business_chat.py       # 看板分析与快捷问题 LCEL 链
├── api/v1/
│   ├── chat.py                # 普通对话、结构化看板分析、快捷问题
│   ├── chapter.py             # 章节分析契约
│   └── video.py               # 图片生成、视频提交与查询
├── schemas/                   # Pydantic 请求/响应契约
└── services/
    ├── chapter_analysis.py    # Story Bible 业务契约与 Prompt 最终化
    ├── memory.py              # 受控长期偏好召回、写入与清空
    └── video_capabilities.py  # Wanx/Qwen 模型能力单一来源
```

## 内部 API

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/chat/invoke` | 普通对话；`mode=context_analysis` 时由 Python 分析结构化业务数据 |
| POST | `/api/v1/chat/stream` | 对话或看板分析 SSE |
| POST | `/api/v1/chat/smart-questions` | 根据结构化对话历史返回严格 3 条快捷问题 |
| DELETE | `/api/v1/chat/thread` | 按 `{user_id, thread_id}` 永久删除会话 checkpoint |
| POST | `/api/v1/chat/memory/list` | 查看当前用户规范化长期回答偏好 |
| PUT | `/api/v1/chat/memory/preference` | 修改一项长期回答偏好 |
| DELETE | `/api/v1/chat/memory` | 幂等清空当前用户全部长期回答偏好 |
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

## 长期回答偏好

长期记忆默认完全关闭。启用后仅保存 `answer_length`、
`answer_structure`、`number_format` 三类枚举偏好；确定性提取只接受用户
“以后回答简短一点”“以后先说结论”等明确表达。Store 不保存聊天原文、
角色权限、手机号、验证码、库存、设备状态或其他模型推断事实。

生产配置示例：

```dotenv
AGENT_STORE_BACKEND=postgres
# 留空时复用 AGENT_POSTGRES_DSN
AGENT_STORE_POSTGRES_DSN=postgresql://agent_user:password@postgres:5432/lingxi_agent
AGENT_MEMORY_ENABLED=true
AGENT_MEMORY_NAMESPACE_SECRET=replace-with-an-independent-random-secret-of-32-plus-bytes
AGENT_MEMORY_MAX_RECALL=5
AGENT_MEMORY_WRITE_CONFIDENCE=0.9
```

用户 ID 先使用独立密钥做 HMAC-SHA256，再作为 Store 命名空间；密钥和 DSN
均使用 `SecretStr`，不会进入 Prompt、checkpoint、响应或日志。修改偏好使用
确定键覆盖，清空操作幂等。开发测试可使用 `AGENT_STORE_BACKEND=memory`，
多实例生产部署必须使用 PostgreSQL Store。

## 本地通用工具

普通 Agent 始终注册以下无外部副作用的通用工具，不需要额外 API Key：

- `get_current_datetime`：查询 Asia/Shanghai 或指定 IANA 时区的精确日期、时间和星期；
- `calculate`：执行有长度、复杂度和数值范围限制的安全四则及幂运算，不执行代码；
- `convert_units`：换算长度、质量、体积、时间、面积、速度和温度，不提供实时汇率；
- `date_calculator`：日期加减、两个日期的间隔与包含首尾日期的天数计算。

以上工具都通过 `ToolRuntime` 产生安全的 `started` / `completed` 进度事件，
不访问业务数据库，也不读取用户身份或凭据。

`WEATHER_ENABLED=true` 时还会注册 `get_weather`。它优先访问固定的 Open-Meteo
HTTPS 地理编码与天气主机，提供当前天气和 1～7 天预报；响应大小受
`WEATHER_MAX_RESPONSE_BYTES` 限制，用户不能控制目标 URL。若当前网络无法访问
Open-Meteo 且已配置 Tavily，则自动降级为有来源链接的当日公开天气搜索结果。

Tavily 默认使用直连，避免桌面系统代理规则导致搜索连接失败。生产环境需要
显式代理时设置 `TAVILY_HTTPS_PROXY`；只有确认进程代理可靠时才设置
`TAVILY_TRUST_ENV=true`。

## 内部知识检索

内部知识工具默认关闭。第一阶段提供权限优先的 JSONL 后端，并通过统一
`KnowledgeRetriever` 接口与 Agent 解耦，后续可替换为 pgvector 或
OpenSearch，而不改变 `search_knowledge` 工具契约：

```dotenv
KNOWLEDGE_BACKEND=jsonl
KNOWLEDGE_INDEX_PATH=D:/secure/lingxi-knowledge/index.jsonl
KNOWLEDGE_TOP_K=8
KNOWLEDGE_RERANK_TOP_N=5
```

索引在进程启动时严格校验；启用后若文件缺失、格式错误、来源标识重复或
超过大小上限，服务将拒绝启动。检索在打分前按当前登录角色、文档有效期、
当前版本和产品型号过滤。索引格式与发布要求见
[`knowledge/README.md`](knowledge/README.md)。

## Java 只读业务工具

业务工具默认关闭。启用后，普通 Agent 可按需调用 `query_sales_summary`、
`query_task_statistics`、`query_abnormal_devices` 和 `lookup_device`：

```dotenv
AGENT_TOOLS_ENABLED=true
AGENT_TOOL_BASE_URL=http://localhost:8080
AGENT_TOOL_ALLOWED_HOSTS=localhost,127.0.0.1
AGENT_TOOL_ALLOW_INSECURE_HTTP=true
AGENT_TOOL_TIMEOUT_SECONDS=20
```

生产环境应使用 HTTPS 内部地址并设置
`AGENT_TOOL_ALLOW_INSECURE_HTTP=false`。Gateway 目标固定且必须命中精确
白名单，不跟随重定向；响应总大小和模型可见文本均有限制。工具身份只从
不可变 `AgentContext` 读取，短期令牌不会进入模型消息、工具参数、日志或
checkpoint。

Java 令牌默认有效 5 分钟、每轮最多调用 5 次，并绑定用户、会话、请求、
权限和区域。当前令牌 Store 是单 JVM 内存实现，只适合单实例首期部署；
多实例生产部署前必须迁移到 Redis 等共享 Store。

## 人工确认受控写操作

阶段 5 只开放“创建一张待处理维修工单”。模型只能调用
`propose_maintenance_task` 生成提案；`execute_maintenance_task` 不注册给
模型，只会在 Java 登录端记录批准后由 LangGraph `Command(resume=...)`
恢复的工具内部调用。拒绝、过期或执行失败都不会自动重试。

启用前必须先执行 `dkd-parent/sql/ai_agent_action_migration.sql`，并确保 Java
与 Python 使用相同的服务认证密钥。生产环境必须使用 PostgreSQL 持久
checkpoint；两个服务的开关都默认关闭，并需同时显式开启：

```dotenv
AGENT_TOOLS_ENABLED=true
AGENT_WRITE_ACTIONS_ENABLED=true
AGENT_CHECKPOINTER_BACKEND=postgres
AGENT_POSTGRES_DSN=postgresql://agent_user:password@postgres:5432/lingxi_agent
```

Java 同样设置 `AGENT_WRITE_ACTIONS_ENABLED=true`。批准时只允许修改工单描述，
不能更换设备；执行前 Java 会再次检查当前用户、权限、会话、区域、设备状态
和未完成工单，并以数据库唯一键保证重放不会重复创建。

## 本地运行

```powershell
cd lingXi-agent
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
Copy-Item .env.example .env
# 修改 .env，设置 AGENT_SERVICE_API_KEY；模型 API Key 由 Java 模型配置页面逐请求传入
 .\.venv\Scripts\python.exe -m app.run
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
