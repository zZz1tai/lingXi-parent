# 灵犀终端管理系统

灵犀（LingXi）是一个面向智能零售终端的前后端分离管理平台，基于 RuoYi-Vue 演进。它覆盖设备、商品、库存、订单、工单、合作商、区域和运营策略等后台管理能力，并在同一套系统中提供 AI 对话、数据分析及小说视频创作工作流。

本仓库不是单体应用：浏览器管理端、Java 业务服务与 Python AI 服务需要协同运行。

## 系统组成

```text
浏览器 / Tauri 桌面端
          │
          ▼
lingXi-vue ────────────── Vue 3 管理端（Vite 开发端口 3000）
          │ /dev-api 代理
          ▼
dkd-parent / lingXi-admin ─ Spring Boot 业务入口（HTTP 8080）
          ├─────────────── MySQL：业务、权限与任务数据
          ├─────────────── Redis：缓存等基础能力
          ├─────────────── OSS：上传文件与生成资产
          └─────────────── HTTP + X-Agent-Service-Key
                              ▼
                    lingXi-agent（FastAPI，端口 5000）
                              └─ LLM、Qwen Image、视频供应商
```

| 路径 | 角色 | 主要技术 |
| --- | --- | --- |
| [`lingXi-vue`](lingXi-vue) | 管理端及桌面端 UI | Vue 3、Vite 6、Element Plus、Pinia、Vue Router、ECharts、Tauri 2 |
| [`dkd-parent/lingXi-admin`](dkd-parent/lingXi-admin) | Java Web 服务启动模块 | Spring Boot 2.5.15、Swagger、Druid |
| [`dkd-parent/lingXi-manage`](dkd-parent/lingXi-manage) | 智能零售与 AI 业务模块 | MyBatis-Plus、HTTP Agent 客户端、AI 视频业务 |
| [`dkd-parent/lingXi-system`](dkd-parent/lingXi-system) | 系统管理与权限领域 | 用户、角色、菜单、字典等 |
| [`dkd-parent/lingXi-framework`](dkd-parent/lingXi-framework) | 认证、缓存、Web、MyBatis 等框架配置 | Spring Security、Redis |
| [`dkd-parent/lingXi-quartz`](dkd-parent/lingXi-quartz) | 定时任务 | Quartz |
| [`dkd-parent/lingXi-generator`](dkd-parent/lingXi-generator) | 代码生成 | Velocity |
| [`dkd-parent/lingXi-common`](dkd-parent/lingXi-common) | 跨模块工具、常量与基础模型 | Java 通用库 |
| [`lingXi-agent`](lingXi-agent) | AI 执行服务 | Python、FastAPI、LangChain 1.3、LangGraph 1.2、Pydantic v2 |
| [`dkd-parent/sql`](dkd-parent/sql) | 数据库初始化与演进脚本 | MySQL |

Java 启动类是 `com.lingXi.LingXiApplication`。它由 `lingXi-admin` 模块打包，并装配其余 Maven 模块。

## 能力范围

- **终端运营**：售货设备、货道、商品、库存、订单、维修/运营工单、合作商及区域管理。
- **平台能力**：用户、角色、菜单、部门、参数、字典、日志、代码生成和定时任务。
- **AI 对话与分析**：Java 负责权限、业务数据和事务；Agent 负责 Prompt、模型调用、结构化输出与短期记忆。
- **AI 视频创作**：从小说章节生成章节规划、场景、分镜、人物/场景/关键帧提示词，并提交图片、视频任务。用户确认关键帧后才允许提交视频任务。

## 运行环境

| 组件 | 要求 | 说明 |
| --- | --- | --- |
| Java | JDK 11 | 后端 POM 的 `java.version` 为 11。 |
| Maven | 3.6+ | 用于构建和启动 Java 多模块项目。 |
| Node.js | 18+（建议） | 用于 Vite 6 前端构建；使用 npm。 |
| Python | 3.12+（建议） | 用于 FastAPI / LangChain Agent。 |
| MySQL | 8.0+ | 整合初始化脚本以 MySQL 8.0 / `utf8mb4` 为目标。 |
| Redis | 6+ | 默认连接 `localhost:6379`。 |
| PostgreSQL | 14+（可选） | 用于 Agent 生产环境的 checkpoint 和用户记忆持久化；本地开发可用内存模式。 |
| Windows Terminal | 可选 | 仅根目录一键启动脚本需要 `wt.exe`。 |

## 配置与安全

请在启动前检查以下文件。仓库中的连接地址和示例凭据只适用于本地开发，必须替换为自己的安全配置；不要将密钥、密码或生产地址提交到 Git。

| 文件 | 需要配置的内容 |
| --- | --- |
| `dkd-parent/lingXi-admin/src/main/resources/application-druid.yml` | MySQL 地址、数据库名、账号、密码。 |
| `dkd-parent/lingXi-admin/src/main/resources/application.yml` | Redis 密码、上传目录 `ruoyi.profile`、Agent 地址和超时。 |
| `lingXi-agent/.env` | Agent 服务密钥、供应商白名单、可选的 Tavily / PostgreSQL 连接参数。 |
| `lingXi-vue/.env.development` | 开发 API 前缀（默认 `/dev-api`）。 |
| `lingXi-vue/.env.production` | 生产 API 前缀（默认 `/prod-api`）。 |

### 敏感配置管理（系统安全配置）

阿里云 OSS、Token 签名密钥、Agent 服务 API Key 等敏感配置项已从 `application.yml` 迁移到数据库 `sys_config` 表，通过后台管理界面维护。

**配置路径：** 参数管理 → 安全配置

| 配置项 | 说明 |
| --- | --- |
| 阿里云 OSS AccessKey / SecretKey | 文件上传服务的认证凭据 |
| 阿里云 OSS Endpoint / BucketName | OSS 连接地址和存储桶 |
| 阿里云 OSS 访问域名 / 基础路径 | 文件访问 URL 和存储前缀 |
| Agent 服务 API Key | Java 调用 Python Agent 的认证密钥（可选，也可通过环境变量提供） |

首次部署时需执行数据库迁移脚本初始化配置项：

```powershell
mysql -u root -p dkd < dkd-parent/sql/system_security_config_migration.sql
```

### Java 与 Agent 的服务认证

非健康检查的 Agent 路由都要求请求头 `X-Agent-Service-Key`。Java 侧的 `agent.service-api-key` 与 Agent 侧的 `AGENT_SERVICE_API_KEY` 必须是同一个随机、高强度值。

Java 的配置优先级是 `agent.service-api-key`，未设置时回退到 Java 进程环境变量 `AGENT_SERVICE_API_KEY`。根目录的 `start.ps1` 会把该环境变量传给子进程；若 `lingXi-agent/.env` 中也未设置密钥，它会生成一个本地密钥并写入该文件。

Agent 默认只允许向 `OUTBOUND_ALLOWED_HOSTS` 指定的 HTTPS 主机发送携带供应商凭据的请求。新增 OpenAI 兼容供应商时，请显式添加其主机名，而不是关闭该限制。

## 数据库初始化

使用 [`dkd-parent/sql/lingxi_all.sql`](dkd-parent/sql/lingxi_all.sql) 完成一站式初始化：它会创建并切换到 `dkd` 数据库，导入系统表和种子数据，创建 AI 视频工作流表，调整章节软删除字段，并写入 AI 视频菜单及管理员角色授权。

```powershell
mysql -u root -p < dkd-parent/sql/lingxi_all.sql
```

该脚本面向**新环境**。对已有环境，请在备份后审阅并按需执行 `dkd-parent/sql` 中的独立迁移脚本；不要反复导入含有基础表重建语句的完整初始化脚本。

## 首次安装

以下命令在仓库根目录执行。Windows PowerShell 示例中的路径分隔符可按系统替换。

```powershell
# 1. 前端依赖（package-lock.json 存在，推荐 npm ci）
cd lingXi-vue
npm ci

# 2. Python 虚拟环境与 Agent 依赖
cd ..\lingXi-agent
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install --upgrade pip
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
Copy-Item .env.example .env

# 3. 编辑配置后回到仓库根目录
cd ..
```

`start.ps1` 不会安装 npm 或 Python 依赖；上述准备只需首次执行，依赖变更后再重复执行即可。

## 启动

### Windows 一键启动

```powershell
.\start.ps1
# 或
.\start.bat
```

脚本会打开三个 Windows Terminal 标签页，并按下列顺序运行：

1. Agent：`uvicorn app.main:app --host 0.0.0.0 --port 5000 --reload`，等待 `http://127.0.0.1:5000/readyz` 就绪；
2. Java：先执行 `mvn -pl lingXi-admin -am -DskipTests install`，再启动 `LingXiApplication`，监听 8080；
3. 前端：在 `lingXi-vue` 执行 `npm run dev`。Vite 会自动打开浏览器，默认端口为 3000。

启动前脚本会检查 Python、Maven、Windows Terminal，以及 5000/8080 端口可用性。若端口已被未就绪的服务占用，需先停止对应进程。

### 手动启动

适用于 macOS/Linux，或需要在独立终端排查服务时使用。按 Agent → Java → 前端顺序启动。

```bash
# 终端 1：Agent
cd lingXi-agent
.venv/bin/python -m uvicorn app.main:app --host 127.0.0.1 --port 5000 --reload

# Windows 等价命令：.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 5000 --reload
```

```bash
# 终端 2：Java 后端
cd dkd-parent
mvn -pl lingXi-admin -am -DskipTests install
mvn -f lingXi-admin/pom.xml -DskipTests -Dspring-boot.run.main-class=com.lingXi.LingXiApplication spring-boot:run
```

```bash
# 终端 3：前端
cd lingXi-vue
npm run dev
```

开发模式下，Vite 将 `/dev-api/*` 转发到 `http://127.0.0.1:8080/*`。后端 HTTP 服务为 `http://localhost:8080`；前端实际访问地址以 Vite 启动输出为准。

## 常用命令

```powershell
# 前端
cd lingXi-vue
npm run dev             # 本地开发
npm run build:prod      # Web 生产构建
npm run build:stage     # staging 构建
npm run build:desktop   # Tauri 桌面资源构建
npm run build:exe       # 生成 NSIS Windows 安装包

# Java：编译全部相关依赖；移除 -DskipTests 可运行 Maven 测试
cd ..\dkd-parent
mvn -pl lingXi-admin -am -DskipTests compile
mvn -pl lingXi-manage -am test

# Agent：所有测试均可离线运行，不调用真实模型服务
cd ..\lingXi-agent
.\.venv\Scripts\python.exe -m unittest discover -s tests -v
.\.venv\Scripts\python.exe -m pytest -q
.\.venv\Scripts\python.exe -m pip check
```

## 服务接口与诊断

| 服务 | 地址 | 用途 |
| --- | --- | --- |
| Agent 存活检查 | `GET http://localhost:5000/health`、`/livez` | 进程存活与基本信息。 |
| Agent 就绪检查 | `GET http://localhost:5000/readyz` | 供启动脚本判断服务是否可用。 |
| Agent API 文档 | `http://localhost:5000/docs` | 默认关闭；仅在受控开发环境设置 `DOCS_ENABLED=true`。 |
| 后端 Swagger | `http://localhost:8080/swagger-ui/index.html` | `swagger.enabled=true` 时可用；如版本差异导致路径不同，请查看启动日志。 |

Agent 的对话、章节分析、图片生成和视频任务接口统一在 `/api/v1` 下；健康检查以外的接口需要服务密钥。完整接口清单、流式协议、记忆后端和视频流程见 [`lingXi-agent/README.md`](lingXi-agent/README.md)。

## 数据与职责边界

| 事项 | Java 后端 | Python Agent |
| --- | --- | --- |
| 登录、权限、菜单、业务数据与数据库事务 | 负责 | 不负责 |
| 模型 API 调用、Prompt、结构化解析、模型能力规则 | 不直连模型 | 负责 |
| AI 视频人物/场景/分镜等资产关系、OSS 转存、任务状态 | 负责 | 返回经过校验的模型结果 |
| 会话短期记忆 | 管理会话标识并转发 | 默认进程内存；生产可配置 PostgreSQL checkpoint |

## 相关文档

- [AI Agent 开发与接口说明](lingXi-agent/README.md)
- [后端业务模块说明](dkd-parent/README.md)
- [整合数据库初始化脚本](dkd-parent/sql/lingxi_all.sql)

## 许可

前端 `package.json` 标注为 MIT。引入或发布前，请同时核验 RuoYi 基础代码、第三方依赖和所接入模型供应商的各自许可与服务条款。
