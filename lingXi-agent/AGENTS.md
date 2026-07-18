# AGENTS.md — LangChain Search Agent

## 项目概览

基于 LangChain + LangGraph 新版 Agent 架构的联网搜索智能体后端服务，使用 FastAPI 对外提供 RESTful API。

## 技术栈

- Python 3.12 + FastAPI
- LangChain 1.x / LangGraph 1.x
- Pydantic v2 + pydantic-settings
- Tavily Search (可替换)

## 构建与运行

```bash
# 安装依赖
pip install -r requirements.txt

# 开发模式
uvicorn app.main:app --host 0.0.0.0 --port 5000 --reload

# 生产模式
uvicorn app.main:app --host 0.0.0.0 --port 5000
```

## 关键文件定位

| 功能 | 文件路径 | 说明 |
|------|---------|------|
| FastAPI 入口 | `app/main.py` | 路由注册、全局异常处理、生命周期 |
| 配置管理 | `app/config/settings.py` | 环境变量加载，所有敏感配置 |
| Agent 构建 | `app/agents/builder.py` | `langchain.agents.create_agent`、结构化输出策略 |
| Agent 中间件 | `app/agents/middleware.py` | 动态提示词、模型路由、摘要和工具错误处理 |
| Agent 状态 | `app/agents/state.py` | v1 AgentState 与不可变 Context |
| 短期记忆 | `app/agents/checkpoints.py` | InMemory / AsyncPostgresSaver 生命周期 |
| 动态提示词 | `app/agents/prompts.py` | v1 `@dynamic_prompt` 提示词模板 |
| 搜索工具 | `app/agents/tools/web_search.py` | Tavily 封装，替换扩展点 |
| 对话接口 | `app/api/v1/chat.py` | `/invoke` 同步 + `/stream` SSE 流式 |
| 提取接口 | `app/api/v1/extract.py` | ToolStrategy / ProviderStrategy |
| 依赖注入 | `app/api/dependencies.py` | LLM/Agent 单例管理 |
| 请求模型 | `app/schemas/request.py` | ChatRequest, ExtractRequest |
| 响应模型 | `app/schemas/response.py` | 统一响应信封 |
| 日志工具 | `app/utils/logger.py` | 统一日志格式，请求 ID |
| 异常处理 | `app/utils/exceptions.py` | 自定义异常 + 全局 handler |

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/health` | 健康检查 |
| POST | `/api/v1/chat/invoke` | 同步对话 |
| POST | `/api/v1/chat/stream` | SSE 流式对话 |
| POST | `/api/v1/extract` | 结构化信息提取 |

## 代码规范

- 全代码类型注解
- Pydantic v2 模型校验所有输入
- 统一 JSON 错误信封 `{success, error: {code, message}}`
- 日志包含 request_id 用于链路追踪
- 配置与代码分离，敏感信息走环境变量
- 不使用 `langgraph.prebuilt.create_react_agent` 等旧版兼容 API
- 用户身份放 Context，会话身份放 `thread_id`，两者不得混用
