# LangChain Search Agent

基于 LangChain 新版 Agent 架构（`create_react_agent`）的联网搜索智能体后端服务。对外提供 RESTful API，支持同步问答、SSE 流式输出、结构化信息提取等能力。

## 技术栈

| 组件 | 技术选型 |
|------|---------|
| 语言 | Python 3.12+ |
| Web 框架 | FastAPI (异步) |
| 智能体框架 | LangChain + LangGraph (`create_react_agent`) |
| 搜索工具 | Tavily Search (可替换) |
| 数据校验 | Pydantic v2 |
| 配置管理 | pydantic-settings |
| 流式输出 | SSE (Server-Sent Events) |

## 目录结构

```
.
├── app/
│   ├── __init__.py
│   ├── main.py                 # FastAPI 入口，路由注册，全局异常处理，生命周期管理
│   ├── config/
│   │   ├── __init__.py
│   │   └── settings.py         # 配置管理，环境变量加载
│   ├── agents/
│   │   ├── __init__.py
│   │   ├── builder.py          # Agent 构建逻辑，create_react_agent 封装
│   │   ├── prompts.py          # 系统提示词模板，@dynamic_prompt 装饰器
│   │   └── tools/
│   │       ├── __init__.py
│   │       └── web_search.py   # 联网搜索工具封装 (Tavily)
│   ├── api/
│   │   ├── __init__.py
│   │   ├── v1/
│   │   │   ├── __init__.py
│   │   │   ├── chat.py         # 对话接口 (invoke + stream)
│   │   │   └── extract.py      # 结构化提取接口
│   │   └── dependencies.py     # 依赖注入 (LLM/Agent 实例, 请求 ID)
│   ├── schemas/
│   │   ├── __init__.py
│   │   ├── request.py          # 请求参数模型
│   │   └── response.py         # 响应参数模型
│   └── utils/
│       ├── __init__.py
│       ├── logger.py           # 统一日志工具
│       └── exceptions.py       # 自定义异常与全局错误处理
├── .env.example                # 环境变量示例
├── .coze                       # 沙箱构建/运行配置
├── requirements.txt            # Python 依赖清单
└── README.md                   # 本文件
```

## 快速开始

### 1. 环境配置

```bash
# 复制环境变量模板
cp .env.example .env

# 编辑 .env，填入你的 API Key
# - OPENAI_API_KEY: OpenAI 或兼容 API 的密钥
# - TAVILY_API_KEY: Tavily 搜索 API 密钥 (https://tavily.com)
```

### 2. 安装依赖

```bash
pip install -r requirements.txt
```

### 3. 启动服务

```bash
# 开发模式 (带热重载)
uvicorn app.main:app --host 0.0.0.0 --port 5000 --reload

# 生产模式
uvicorn app.main:app --host 0.0.0.0 --port 5000
```

### 4. 访问文档

- Swagger UI: `http://localhost:5000/docs`
- ReDoc: `http://localhost:5000/redoc`

## API 接口

### 健康检查

```bash
curl http://localhost:5000/health
```

### 同步对话

```bash
curl -X POST http://localhost:5000/api/v1/chat/invoke \
  -H "Content-Type: application/json" \
  -d '{
    "message": "2024年诺贝尔物理学奖获得者是谁？",
    "style": "professional",
    "max_iterations": 5
  }'
```

### 流式对话 (SSE)

```bash
curl -X POST http://localhost:5000/api/v1/chat/stream \
  -H "Content-Type: application/json" \
  -N \
  -d '{
    "message": "最新的AI发展趋势有哪些？",
    "style": "casual"
  }'
```

SSE 事件类型：
- `token` — LLM 逐 token 输出
- `tool_start` — 工具调用开始（含工具名和输入参数）
- `tool_end` — 工具调用结束（含输出结果）
- `done` — 执行完成（含完整回答）
- `error` — 执行出错

### 结构化信息提取

**使用 ToolStrategy（Agent 工具调用策略）：**

```bash
curl -X POST http://localhost:5000/api/v1/extract \
  -H "Content-Type: application/json" \
  -d '{
    "text": "苹果公司（Apple Inc.）是一家美国跨国科技公司，总部位于加利福尼亚州库比蒂诺。现任CEO是蒂姆·库克（Tim Cook），他于2011年接替史蒂夫·乔布斯担任此职位。苹果2024年营收达到3900亿美元。",
    "schema_name": "general",
    "strategy": "tool"
  }'
```

**使用 ProviderStrategy（LLM 原生结构化输出）：**

```bash
curl -X POST http://localhost:5000/api/v1/extract \
  -H "Content-Type: application/json" \
  -d '{
    "text": "张三是一位资深软件工程师，在字节跳动担任技术负责人，专注于分布式系统和云原生架构。他拥有10年开发经验，曾主导多个大规模微服务项目。",
    "schema_name": "person",
    "strategy": "provider"
  }'
```

**自定义提取字段：**

```bash
curl -X POST http://localhost:5000/api/v1/extract \
  -H "Content-Type: application/json" \
  -d '{
    "text": "任何文本内容...",
    "custom_fields": ["company_name", "founding_year", "headquarters", "ceo"],
    "strategy": "tool"
  }'
```

## 核心特性

### 1. 动态提示词机制

通过 `@dynamic_prompt` 装饰器实现系统提示词的动态注入。支持根据请求参数切换回答风格：

- `professional` — 专业研究助手风格，注重准确性和引用
- `casual` — 友好对话风格，通俗易懂

```python
@dynamic_prompt
def get_system_prompt(state: dict) -> SystemMessage:
    style = state.get("style", "professional")
    # 根据 style 返回不同的系统提示
    ...
```

### 2. 自定义状态扩展

通过 `state_schema` 扩展 Agent 状态，支持传入用户偏好、业务标识等自定义参数：

```python
class AgentState(TypedDict):
    messages: Annotated[list[BaseMessage], add_messages]
    style: str          # 回答风格
    user_id: str        # 用户标识
    business_tag: str   # 业务上下文
```

### 3. 结构化输出

同时支持两种结构化输出策略：

- **ToolStrategy**: 通过 Agent 的 `response_format` 参数，利用工具调用机制输出结构化数据
- **ProviderStrategy**: 通过 LLM 的 `with_structured_output` 方法，利用模型原生结构化输出能力

### 4. 安全约束

- `max_iterations`: 最大迭代次数限制（默认 5 轮），防止 Agent 死循环
- `recursion_limit`: LangGraph 递归限制，根据迭代次数自动计算
- 工具调用超时机制

### 5. 流式输出

原生支持 Agent 流式返回，使用 `astream_events` (v2) 实现：

- `values` 模式：每个完整状态步骤后输出
- `messages` 模式：逐 token 实时输出

## 扣子平台适配说明

### 1. 替换为扣子平台搜索插件

将 `app/agents/tools/web_search.py` 中的 Tavily 工具替换为扣子平台的网页搜索插件：

```python
from langchain_core.tools import tool
import httpx

@tool
def coze_web_search(query: str) -> str:
    """使用扣子平台网页搜索插件进行搜索"""
    # 调用扣子平台插件 API
    response = httpx.post(
        "https://api.coze.com/v1/plugin/execute",
        headers={"Authorization": f"Bearer {COZE_API_KEY}"},
        json={"plugin_id": "web_search", "parameters": {"query": query}}
    )
    return response.json()["data"]
```

然后在 `get_default_tools()` 中替换即可。

### 2. 对接扣子平台大模型（豆包系列）

豆包模型兼容 OpenAI API 格式，只需修改环境变量：

```env
OPENAI_API_KEY=your-coze-api-key
OPENAI_API_BASE=https://ark.cn-beijing.volces.com/api/v3
MODEL_NAME=doubao-pro-128k
```

或在代码中直接配置：

```python
from langchain_openai import ChatOpenAI

llm = ChatOpenAI(
    model="doubao-pro-128k",
    api_key="your-coze-api-key",
    base_url="https://ark.cn-beijing.volces.com/api/v3",
)
```

### 3. 部署到扣子云托管环境

注意事项：

1. **端口配置**: 服务端口从环境变量 `DEPLOY_RUN_PORT` 读取，不要硬编码
2. **依赖安装**: 确保 `requirements.txt` 中所有依赖版本兼容云托管环境的 Python 版本
3. **环境变量**: 通过扣子平台的「环境变量」功能配置 `OPENAI_API_KEY`、`TAVILY_API_KEY` 等
4. **健康检查**: 扣子平台会通过 `/health` 接口检测服务状态，确保该接口正常返回
5. **日志输出**: 所有日志输出到 stdout，扣子平台会自动采集
6. **无状态设计**: 当前服务设计为无状态，可水平扩展

## 依赖版本

| 包名 | 版本要求 | 说明 |
|------|---------|------|
| fastapi | >=0.115.0 | Web 框架 |
| uvicorn | >=0.32.0 | ASGI 服务器 |
| pydantic | >=2.9.0 | 数据校验 |
| pydantic-settings | >=2.6.0 | 配置管理 |
| langchain | >=0.3.0 | LangChain 核心 |
| langchain-core | >=0.3.0 | LangChain 核心抽象 |
| langchain-openai | >=0.3.0 | OpenAI 模型集成 |
| langchain-community | >=0.3.0 | 社区工具集成 |
| langgraph | >=0.2.60 | Agent 图引擎 |
| tavily-python | >=0.5.0 | Tavily 搜索客户端 |
| sse-starlette | >=2.0.0 | SSE 支持 |
| httpx | >=0.27.0 | 异步 HTTP 客户端 |
