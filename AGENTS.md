# AGENTS.md — 灵犀智能应用平台（LingXi）

本仓库是一个多服务仓库，不是单体应用。修改任何功能前，先确认它属于哪个子系统，
并阅读对应的子 AGENTS.md。文档与代码注释使用中文。

## 系统组成

```text
浏览器 / Tauri 桌面端
      │
      ▼
lingXi-vue ─────── Vue 3 管理端（Vite 开发端口 3000，/dev-api 代理到 8080）
      ▼
dkd-parent / lingXi-admin ── Spring Boot 业务入口（HTTP 8080，JDK 17）
      │             ├─ MySQL（业务/权限/任务数据）+ Redis（缓存）
      │             └─ HTTP + X-Agent-Service-Key
      ▼
lingXi-agent ────── Python AI 执行层（FastAPI，端口 5000）
```

| 目录 | 角色 | 详见 |
| --- | --- | --- |
| `lingXi-vue/` | Vue 3 管理端 + Tauri 2 桌面端 | [`lingXi-vue/AGENTS.md`](lingXi-vue/AGENTS.md) |
| `dkd-parent/` | Java 多模块后端（com.lingXi / com.dkd 包） | [`dkd-parent/AGENTS.md`](dkd-parent/AGENTS.md) |
| `lingXi-agent/` | Python AI 服务（FastAPI + LangChain v1 + LangGraph v1） | [`lingXi-agent/AGENTS.md`](lingXi-agent/AGENTS.md) |
| `docs/` | AI 对话与视频工作流设计文档 | 读文档再改代码 |

版本号 3.8.7 在 `lingXi-vue/package.json` 与 `dkd-parent/pom.xml` 必须保持一致。

## 职责边界（改动前必读）

- Java 负责登录、权限、业务数据、数据库事务、任务状态、资产关系、OSS 转存、人工确认。
- Python Agent 负责模型调用、Prompt、结构化输出解析、模型能力规则、短期记忆、知识检索。
- Java 不直连模型，Python 不读写业务库。跨服务调用只通过 HTTP，携带 `X-Agent-Service-Key`
  （Java `agent.service-api-key` / 环境变量 `AGENT_SERVICE_API_KEY`，两侧必须一致）。
- Agent 出站请求受 `OUTBOUND_ALLOWED_HOSTS` 白名单限制；不要为了省事关闭该限制。

## 常用命令

```powershell
# Java：编译 / 测试（测试不依赖外部服务）
cd dkd-parent
mvn -pl lingXi-admin -am -DskipTests install
mvn -pl lingXi-manage -am test

# Agent：离线测试（不调用真实模型）
cd lingXi-agent
.\.venv\Scripts\python.exe -m unittest discover -s tests -v
.\.venv\Scripts\python.exe -m pytest -q
.\.venv\Scripts\python.exe -m pip check

# 前端
cd lingXi-vue
npm run dev             # 开发（端口 3000）
npm run test            # node --test tests/*.test.mjs
npm run build:prod      # 生产构建
```

Windows 一键启动见 `start.ps1`（Agent → Java → 前端）。

## 配置与安全

- 敏感配置不写死代码：OSS 密钥、Token 密钥、Agent Key 等存数据库 `sys_config`（参数管理→安全配置），
  或通过环境变量注入。`lingXi-agent/.env`、`application.yml`、`.env*` 都是本地配置，禁止修改后提交。
- 不要提交密钥/密码/生产地址；`.env` 类文件内容不得进 diff 或日志。
- 数据库脚本：新环境用 `dkd-parent/sql/lingxi_all.sql`；既有环境只能执行独立迁移脚本，禁止反复导入重建脚本。

## 定位指引

- AI 对话 / 视频 / 小说后端：`dkd-parent/lingXi-manage` 下 `com.lingXi.ai`、`com.lingXi.aiVedio`、`com.lingXi.aiNovel`、
  `com.lingXi.app`，Python 侧见 `lingXi-agent/README.md`。
- 前端 AI 页面：`lingXi-vue/src/views/ai/`、`views/aiVedio/`、`views/novel/`，接口封装在 `src/api/`。
- 架构约束由 ArchUnit 强制（`LingXiArchitectureRulesTest`）：Controller 不得依赖 Mapper；
  `com.lingXi.aiVedio` 不得依赖对话域/小说域/管理域；`com.lingXi.common` 不依赖任何业务模块。
  破坏这些规则的代码会被 `mvn test` 拒绝，不要绕过。