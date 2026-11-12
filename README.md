# 灵犀智能应用平台

灵犀智能应用平台（LingXi）是一个面向智能零售终端与 AI 应用场景的前后端分离平台，基于 RuoYi-Vue 演进。它覆盖设备、商品、库存、订单、工单、合作商、区域和运营策略等后台管理能力，并在同一套系统中提供 AI 对话、数据分析、小说视频创作工作流、内部知识检索及受控业务工具。

本仓库不是单体应用：浏览器管理端、Java 业务服务与 Python AI 服务需要协同运行。

当前版本：**3.8.7**（前后端版本号保持一致）。

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
| [`lingXi-vue`](lingXi-vue) | 管理端及桌面端 UI | Vue 3、Vite 7、Element Plus、Pinia、Vue Router、ECharts、Tauri 2 |
| [`dkd-parent/lingXi-admin`](dkd-parent/lingXi-admin) | Java Web 服务启动模块 | Spring Boot 4.1、Springdoc、Druid |
| [`dkd-parent/lingXi-manage`](dkd-parent/lingXi-manage) | 智能零售与 AI 业务模块 | MyBatis-Plus、HTTP Agent 客户端、AI 视频业务 |
| [`dkd-parent/lingXi-system`](dkd-parent/lingXi-system) | 系统管理与权限领域 | 用户、角色、菜单、字典等 |
| [`dkd-parent/lingXi-framework`](dkd-parent/lingXi-framework) | 认证、缓存、Web、MyBatis 等框架配置 | Spring Security、Redis |
| [`dkd-parent/lingXi-quartz`](dkd-parent/lingXi-quartz) | 定时任务 | Quartz |
| [`dkd-parent/lingXi-generator`](dkd-parent/lingXi-generator) | 代码生成 | Velocity |
| [`dkd-parent/lingXi-common`](dkd-parent/lingXi-common) | 跨模块工具、常量与基础模型 | Java 通用库 |
| [`lingXi-agent`](lingXi-agent) | AI 执行服务 | Python、FastAPI、LangChain 1.3、LangGraph 1.2、Pydantic v2 |
| [`lingXi-agent/knowledge`](lingXi-agent/knowledge) | 内部知识索引与发布规范 | JSONL 后端、权限优先过滤 |
| [`dkd-parent/sql`](dkd-parent/sql) | 数据库初始化与演进脚本 | MySQL |
| [`docs`](docs) | AI 对话与视频工作流设计文档 | Markdown |
| [`.github/workflows`](.github/workflows) | CI 与 Tauri 桌面发布流水线 | GitHub Actions |

Java 启动类是 `com.lingXi.LingXiApplication`。它由 `lingXi-admin` 模块打包，并装配其余 Maven 模块。

## 目录结构

```text
lingXi-parent/
├── lingXi-vue/                 # Vue 3 管理端 + Tauri 2 桌面端
├── dkd-parent/                 # Java 多模块工程（Maven）
│   ├── lingXi-admin/           # 启动模块，含 application*.yml 配置
│   ├── lingXi-manage/          # 智能零售与 AI 业务
│   ├── lingXi-system/          # 系统管理与权限
│   ├── lingXi-framework/       # 认证、缓存、Web 框架配置
│   ├── lingXi-quartz/          # 定时任务
│   ├── lingXi-generator/       # 代码生成
│   ├── lingXi-common/          # 通用工具与基础模型
│   └── sql/                    # 初始化与演进脚本
├── lingXi-agent/               # Python AI 执行服务（FastAPI）
│   ├── app/                    # agents / chains / api / services / schemas 等
│   ├── knowledge/              # 知识索引规范（knowledge/README.md）
│   ├── tests/                  # 离线测试
│   └── .env.example            # Agent 环境变量模板
├── docs/                       # AI 对话与视频工作流设计文档
├── .github/workflows/          # CI 与 Tauri 桌面发布流水线
├── start.ps1 / start.bat       # Windows 一键启动
└── pyproject.toml              # Python 工程元数据
```

## 版本信息

| 部分 | 版本 | 说明 |
| --- | --- | --- |
| 平台版本 | 3.8.7 | `package.json` 与 `dkd-parent/pom.xml` 保持一致 |
| 前端 | Vue 3.5 / Vite 7 / Element Plus 2.14 / ECharts 6 / Tauri 2 | 见 `lingXi-vue/package.json` |
| 后端 | Spring Boot 4.1 / JDK 17 / MyBatis-Plus 3.5 / Springdoc 3 | 见 `dkd-parent/pom.xml` |
| Agent | Python 3.12 / FastAPI / LangChain 1.3 / LangGraph 1.2 / Pydantic v2 | 见 `lingXi-agent/requirements.txt` |

## 能力范围

- **终端运营**：售货设备、货道、商品、库存、订单、维修/运营工单、合作商及区域管理。
- **平台能力**：用户、角色、菜单、部门、参数、字典、日志、代码生成和定时任务。
- **AI 对话与分析**：Java 负责权限、业务数据和事务；Agent 负责 Prompt、模型调用、结构化输出与短期记忆。Agent 连接层带熔断降级：连续连接故障快速失败，熔断期间同步/流式对话返回固定兜底回复（SUCCEEDED + `AGENT_DEGRADED`），半开窗口自动恢复探测。
- **AI 小说创作**：作品设定卡管理、三层大纲（全书→卷→章）AI 生成与断链检查报告、伏笔登记与追踪（按状态/优先级管理，未解伏笔随创作请求注入智能体）。
- **AI 视频创作**：从小说章节生成章节规划、场景、分镜、人物/场景/关键帧提示词，并异步提交图片、视频任务（outbox 事件投递、指数退避重试、重启恢复扫描）。用户确认关键帧后才允许提交视频任务；支持供应商回调（HMAC 验签、时间戳防重放、eventId 幂等）与生成队列页（筛选、详情、重试、自动刷新）。
- **内部知识检索与证据回看**：Agent 按登录角色、文档有效期和版本过滤后检索 JSONL 知识索引，默认关闭。启用后可展开回答中的参考资料，查看原文片段、章节与版本；成功回答保存检索快照，刷新或切换会话后仍可回看。
- **AI 对话纪要**：在对话页点击“对话纪要”，从已保存的有效文本中整理摘要、关键结论、待办和待确认事项，支持复制及下载 Markdown。超长会话只总结最近部分并显示截断提示；不改写原对话，也不自动执行待办。
- **业务数据工具**：启用后 Agent 可调用 Java 网关查询销售汇总、任务统计、异常设备等只读数据。
- **人工确认受控写操作**：模型只能生成维修工单提案，用户在登录端批准后才由 Java 执行创建。
- **长期回答偏好**：可启用确定性提取的用户回答偏好（长度、结构、数字格式），默认关闭。
- **抖音视频解析与浏览**：在“小说视频工作台”提供“抖音解析”（粘贴分享口令直接观看）与“刷抖音”（推荐/频道视频流、评论、真实弹幕飘屏、作者主页、搜索、相关推荐，列表+播放器布局）。Java 后端用本机 Edge/Chrome（无头）执行抖音页面脚本获取数据：视频流/评论/弹幕/主页游客即可看；**搜索需点击“浏览器登录”一次性登录抖音账号**（登录态保存在服务端长期复用）。视频由代理流接口转发播放（支持拖动进度条），播放令牌 30 分钟有效，不向浏览器暴露 CDN 直链。

## 运行环境

| 组件 | 要求 | 说明 |
| --- | --- | --- |
| Java | JDK 17 | 后端 POM 的 `java.version` 为 17。 |
| Maven | 3.6+ | 用于构建和启动 Java 多模块项目。 |
| Node.js | 20.19+ 或 22.12+ | 用于 Vite 7 前端构建；使用 npm。 |
| Python | 3.12（与 CI 一致） | 用于 FastAPI / LangChain Agent。 |
| MySQL | 8.0+ | 整合初始化脚本以 MySQL 8.0 / `utf8mb4` 为目标。 |
| Redis | 6+ | 默认连接 `localhost:6379`。 |
| PostgreSQL | 14+（可选） | 用于 Agent 生产环境的 checkpoint 和用户记忆持久化；本地开发可用内存模式。 |
| Rust / MSVC / WebView2 | 可选 | 仅 Tauri 桌面应用开发与打包需要；浏览器版无需安装。 |
| Windows Terminal | 可选 | 有 `wt.exe` 时打开标签页；未安装时后台运行并写入 `output/` 日志。 |
| Edge 或 Chrome | 可选 | 仅“抖音视频解析”工具需要；Java 后端以无头模式驱动本机浏览器抓取视频数据，浏览器路径可用环境变量 `LINGXI_BROWSER_PATH` 指定，运行缓存位于 `~/.lingxi/douyin-browser`。 |

## 配置与安全

请在启动前检查以下文件。仓库中的连接地址和示例凭据只适用于本地开发，必须替换为自己的安全配置；不要将密钥、密码或生产地址提交到 Git。

| 文件 | 需要配置的内容 |
| --- | --- |
| `dkd-parent/lingXi-admin/src/main/resources/application-druid.yml` | MySQL 地址、数据库名、账号（`DB_USERNAME`）、密码（`DB_PASSWORD`）以及 Druid 控制台账号（`DRUID_USERNAME` / `DRUID_PASSWORD`）。 |
| `dkd-parent/lingXi-admin/src/main/resources/application.yml` | Redis 密码（`REDIS_PASSWORD`）、上传目录 `ruoyi.profile`（`RUOYI_PROFILE`）、Token 密钥（`TOKEN_SECRET`）、Agent/视频地址和超时、业务工具令牌与写操作开关。 |
| `lingXi-agent/.env` | 服务密钥、供应商出站白名单、Tavily / 天气 / 知识检索 / 业务工具 / 记忆与 PostgreSQL 等可选项；模板见 `.env.example`。 |
| `lingXi-vue/.env.development` | 开发 API 前缀（默认 `/dev-api`）。 |
| `lingXi-vue/.env.production` | 生产 API 前缀（默认 `/prod-api`）。 |

`start.ps1` 会从 `lingXi-agent/.env` 导入 `DB_PASSWORD`、`REDIS_PASSWORD`、`DRUID_PASSWORD`、`RUOYI_PROFILE`、`TOKEN_SECRET` 等变量传给 Java 子进程；这些变量也可直接设置在进程环境变量中。

### 敏感配置管理（系统安全配置）

阿里云 OSS、Token 签名密钥、Agent 服务 API Key 等敏感配置项已从 `application.yml` 迁移到数据库 `sys_config` 表，通过后台管理界面维护。

**配置路径：** 参数管理 → 安全配置

| 配置项 | 说明 |
| --- | --- |
| 阿里云 OSS AccessKey / SecretKey | 文件上传服务的认证凭据 |
| 阿里云 OSS Endpoint / BucketName | OSS 连接地址和存储桶 |
| 阿里云 OSS 访问域名 / 基础路径 | 文件访问 URL 和存储前缀 |
| Agent 服务 API Key | Java 调用 Python Agent 的认证密钥（可选，也可通过环境变量提供） |
| Tavily API Key | 联网搜索凭据（可选，离线部署时通过 `lingXi-agent/.env` 兜底） |

首次部署时 `lingxi_all.sql` 已自动初始化安全配置项；既有环境也可通过参数管理界面（系统管理 → 参数管理 → 系统安全配置）手工维护或补录。

### Java 与 Agent 的服务认证

非健康检查的 Agent 路由都要求请求头 `X-Agent-Service-Key`。Java 侧的 `agent.service-api-key` 与 Agent 侧的 `AGENT_SERVICE_API_KEY` 必须是同一个随机、高强度值。

Java 的配置优先级是 `agent.service-api-key`，未设置时回退到 Java 进程环境变量 `AGENT_SERVICE_API_KEY`。根目录的 `start.ps1` 会把该环境变量传给子进程；若 `lingXi-agent/.env` 中也未设置密钥，它会生成一个本地密钥并写入该文件。

Agent 默认只允许向 `OUTBOUND_ALLOWED_HOSTS` 指定的 HTTPS 主机发送携带供应商凭据的请求。新增 OpenAI 兼容供应商时，请显式添加其主机名，而不是关闭该限制。生产环境应保持 `ALLOW_INSECURE_OUTBOUND_HTTP=false`。

需要启用知识检索、业务只读工具、人工确认写操作或长期回答偏好时，请同步开启 Java 与 Python 两侧的开关，并按各自 README 完成数据库迁移与生产配置（详见 [`lingXi-agent/README.md`](lingXi-agent/README.md)）。

## 数据库初始化

使用 [`dkd-parent/sql/lingxi_all.sql`](dkd-parent/sql/lingxi_all.sql) 完成一站式初始化：它会创建并切换到 `dkd` 数据库，导入系统表和种子数据，创建 AI 视频工作流、小说大纲/伏笔、任务尝试记录等表，写入 AI 视频菜单及管理员角色授权，并初始化安全配置项。

从仓库根目录启动 MySQL 客户端（需要 MySQL CLI；也可用数据库管理工具导入文件）：

```powershell
mysql --default-character-set=utf8mb4 -h 127.0.0.1 -u root -p
```

进入 MySQL 提示符后执行（`SOURCE` 是 MySQL 命令，不是 PowerShell 命令）：

```sql
SOURCE dkd-parent/sql/lingxi_all.sql;
```

PowerShell 不支持 shell 的 `<` 输入重定向，不要直接粘贴 Bash 的导入写法。

该脚本面向**新环境**，已整合所有历史迁移。`dkd-parent/sql/` 只保留 `lingxi_all.sql`，不再需要按顺序执行多个 SQL 文件。

2026-11-07 已通过 JDBC 只读查询本机数据库的表结构，核对全部 80 张表及字段、索引；未读取业务行或修改数据库。核对说明见 [数据库结构核对记录](docs/sql-schema-audit.md)。

**已有数据库禁止重新导入总脚本**：其中含有 DROP TABLE。升级时先备份、对比实际结构，再编写仅针对差异的升级 SQL；历史迁移可在 Git 历史中查阅，不能不经核对整批执行。

## 新电脑从零部署

浏览器全栈需要 Git、JDK 17（含 `javac`）、Maven、Node、Python、MySQL、Redis。Docker 和 IDE 均不是必需项；MySQL/Redis 可使用已有可访问服务。Agent 的 PostgreSQL、Langfuse、Tavily、知识索引以及 OSS 都按功能配置，不必一次安装。模型 Key 不影响进程基础启动，但真实 AI 请求必须有可用供应商配置。

1. 克隆仓库，在当前分支工作。不要复制旧电脑的 `.venv`、`node_modules`、`target` 或本地密钥。
2. 检查 `git --version`、`java -version`、`javac -version`、`mvn -version`、`node --version`、`npm --version`、`python --version`。Maven 使用的 Java 也必须是 JDK 17；路径有空格时使用引号。
3. 在仓库根目录运行以下命令（联网下载 npm/PyPI 依赖，失败即停止，不安装系统软件或初始化数据库）：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\setup.ps1
# 需要运行 Python 测试时改用：
# powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\setup.ps1 -Dev
```

4. 启动 MySQL、Redis；按前一节仅对新环境导入数据库。配置连接信息；默认 MySQL `localhost:3306/dkd`、账号 `root`，Redis `localhost:6379`、库 `0`、无密码。生产不可沿用这些假设。
5. 设置本地密码及路径，可在当前 PowerShell 进程设置环境变量，也可把下表允许的变量添加到忽略的 `lingXi-agent/.env`。无需复制整个 `.env.example` 才能启动；它的服务密钥是占位符，照抄后必须替换为真实随机密钥。不要覆盖已有文件。
6. 运行 `./start.ps1 -CheckOnly` 检查本地工具和依赖，然后 `./start.ps1`。首次 Java 启动还需联网下载 Maven 依赖。检查模式不会启动服务、写密钥或验证 MySQL/Redis 连接、表结构及模型。
7. 按“部署验收”验证登录和业务，最后再在模型管理及安全配置页面启用 AI、OSS 等所需能力。

### 本地配置入口

`start.ps1` 导入下列 `.env` 变量给 Java，已有进程变量优先。手动启动或生产进程管理必须自行注入环境变量，Java 不会自动读取 Python `.env`。

| 变量 | 用途 |
| --- | --- |
| `DB_USERNAME` / `DB_PASSWORD` | MySQL 账号 / 密码 |
| `SPRING_DATASOURCE_DRUID_MASTER_URL` | 自定义完整 JDBC URL（默认本地 dkd）；远程 MySQL 或自定义端口时设置 |
| `SPRING_DATA_REDIS_HOST` / `SPRING_DATA_REDIS_PORT` / `SPRING_DATA_REDIS_DATABASE` | 自定义 Redis 地址、端口、库 |
| `REDIS_PASSWORD` | Redis 密码；无密码时留空 |
| `RUOYI_PROFILE` | 可写上传目录；默认相对启动目录 `./uploadPath` |
| `TOKEN_SECRET` | Token 签名密钥；生产必须使用独立高强度值 |
| `DRUID_USERNAME` / `DRUID_PASSWORD` | Druid 控制台账号 / 密码 |

`LINGXI_JAVA_PORT`（优先）或 `SERVER_PORT` 可在运行启动脚本前设置，脚本同步默认前端代理和 Agent 工具回调地址。自定义 `VITE_APP_PROXY_TARGET`、`AGENT_TOOL_BASE_URL` 时保留显式值。

### macOS / Linux 首次依赖安装

在仓库根目录执行，先完成相同的系统工具、数据库和环境变量准备：

```bash
npm --prefix lingXi-vue ci
python3.12 -m venv lingXi-agent/.venv
lingXi-agent/.venv/bin/python -m pip install -r lingXi-agent/requirements.txt
# 需要 pytest 时使用 requirements-dev.txt
lingXi-agent/.venv/bin/python -m pip check
```

手动启动前为两个终端注入同一 `AGENT_SERVICE_API_KEY`；可以用 `python3.12 -c 'import secrets; print(secrets.token_urlsafe(32))'` 在私有终端生成，再安全保存。不要将输出粘贴到仓库、聊天或共享日志。

## 启动

### Windows 一键启动

```powershell
.\start.ps1
# 或
.\start.bat
```

脚本优先打开 Windows Terminal 标签页；未安装时使用系统自带 cmd 在后台运行，打印 PID，日志保存在忽略的 `output/LingXi-*.log`。三个服务并发启动，随后等待后端就绪：

1. Agent：`uvicorn app.main:app --host 0.0.0.0 --port 5000 --reload`；就绪地址为 `http://127.0.0.1:5000/readyz`；
2. Java：先执行 `mvn -pl lingXi-admin -am -DskipTests install`，再启动 `LingXiApplication`，监听 8080；
3. 前端：在 `lingXi-vue` 执行 `npm run dev`。Vite 会自动打开浏览器，默认端口为 3000。

启动前检查 Python 运行依赖、Java/Javac、Maven、Node/npm 和 Vite 安装情况。Agent 端口被非就绪服务占用时会报错；Java 端口已监听时会跳过启动，但这不证明占用者就是本项目，需核实。停止时关闭对应服务终端；后台模式按打印的 PID 定位进程树，确认归属后再停止，勿批量结束所有 Java/Python/Node 进程。

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

根启动脚本的离线回归检查：`powershell.exe -NoProfile -ExecutionPolicy Bypass -File tests/launcher.test.ps1`，不会启动业务服务；检查 PowerShell 语法、配置导入优先级和后台日志路径引用。

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
# pytest 需要先安装 requirements-dev.txt
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

## 部署验收与排错

| 验收 / 现象 | 检查与处理 |
| --- | --- |
| Agent 就绪 | `GET http://127.0.0.1:5000/readyz` 返回成功；这不代表模型可用 |
| Java 启动 | 日志出现应用启动成功；TCP 8080 监听仅作为初步检查 |
| 完整业务 | 打开 Vite 实际地址，使用本环境管理员账号登录，验证验证码、动态菜单和一项列表查询；新库账号由种子数据提供，首次登录即修改密码 |
| AI 验收 | 在模型管理配置 Key、模型名、Base URL，确保主机在 Agent 出站白名单，再发送一次测试消息 |
| 依赖安装失败 | 检查包源、代理、网络和运行时版本；不要关闭 TLS 校验或移除版本约束 |
| 数据库连接失败 | 检查 URL、用户名密码、网络授权及是否创建 dkd；禁止为排错重导整库 |
| Redis 连接失败 | 确认服务已启动、密码一致，远端地址用上述 Spring 环境变量 |
| `/dev-api` 502 / 连接拒绝 | 检查 Java 是否成功启动以及 `VITE_APP_PROXY_TARGET`，修改后重启 Vite |
| Agent 401 | 两端服务密钥或数据库安全配置中的覆盖值不一致；修正后重启服务，不打印密钥 |
| PostgreSQL 连接失败 | 仅启用 postgres checkpoint / Store 时需要；本地可选择 memory，不要把它当 MySQL 替代品 |

## 生产部署边界

`setup.ps1` / `start.ps1` 是本地开发工具，带热重载，不用于公网生产运行。生产由进程管理器分别托管 Java、Agent 和 Web 静态资源，准备 TLS 反向代理、持久化上传目录、日志及备份。
Java 明确设置 `SPRING_PROFILES_ACTIVE=druid,prod`，通过环境注入 MySQL URL/账号密码、`REDIS_HOST` / `REDIS_PASSWORD`、`TOKEN_SECRET`、`AIVIDEO_CALLBACK_SECRET`、Agent 共享密钥等；先审阅 `application-prod.yml` 的必填变量。
Agent 去掉 `--reload`，仅向内网开放 5000，按需为 checkpoint/Store 部署 PostgreSQL；内存状态在重启后丢失，不能跨实例共享。
Web 发布产物由静态服务器托管，`/prod-api` 反向代理到 Java 并剥离前缀，SSE 关闭代理缓冲。数据库、Redis、Agent 不直接暴露公网。

## 数据与职责边界

| 事项 | Java 后端 | Python Agent |
| --- | --- | --- |
| 登录、权限、菜单、业务数据与数据库事务 | 负责 | 不负责 |
| 模型 API 调用、Prompt、结构化解析、模型能力规则 | 不直连模型 | 负责 |
| AI 视频人物/场景/分镜等资产关系、OSS 转存、任务状态 | 负责 | 返回经过校验的模型结果 |
| 会话短期记忆 | 管理会话标识并转发 | 默认进程内存；生产可配置 PostgreSQL checkpoint |
| 长期回答偏好 | 不保存 | 仅保存三类枚举偏好，默认关闭 |
| 内部知识检索与权限过滤 | 透传角色等上下文 | 打分前按角色、有效期、版本过滤 |
| 业务工具网关与人工确认写操作 | 持有令牌、执行批准后的写操作 | 只读查询、生成提案，默认关闭 |

## 相关文档

- [平台架构治理与服务生命周期](docs/platform-architecture-lifecycle.md)
- [AI Agent 开发与接口说明](lingXi-agent/README.md)
- [知识索引发布规范](lingXi-agent/knowledge/README.md)
- [后端业务模块说明](dkd-parent/README.md)
- [整合数据库初始化脚本](dkd-parent/sql/lingxi_all.sql)
- [AI 对话链路与小说分析流程设计](docs/对话链路与小说分析流程.md)
- [AI 视频工作流设计](docs/ai-video-workflow-design.md)

## 许可

前端 `package.json` 标注为 MIT。引入或发布前，请同时核验 RuoYi 基础代码、第三方依赖和所接入模型供应商的各自许可与服务条款。
