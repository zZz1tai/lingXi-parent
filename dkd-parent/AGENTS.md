# AGENTS.md — dkd-parent（Java 后端）

## 项目概览

基于 RuoYi-Vue 演进的 Spring Boot 多模块工程：灵犀智能零售终端管理 + AI 对话 / 视频 / 小说业务。
启动入口是 `lingXi-admin` 的 `com.lingXi.LingXiApplication`（Spring Boot 4.1，JDK 17）。
Java 负责登录、权限、业务数据、数据库事务、任务状态、资产关系、OSS 转存与人工确认，
**不直连任何模型**；模型调用统一转发给 Python Agent（`lingXi-agent`，HTTP 5000）。

## Maven 模块

| 模块 | 包 | 职责 |
| --- | --- | --- |
| `lingXi-admin` | `com.lingXi.web` | 启动模块：入口、全局 Controller、`application*.yml` 配置 |
| `lingXi-manage` | `com.lingXi.manage` 等 | 零售业务与 AI 业务（见下） |
| `lingXi-system` | `com.lingXi.system` | 用户、角色、菜单、字典等系统权限 |
| `lingXi-framework` | `com.dkd.framework` | 认证、缓存、Web、MyBatis、数据源等框架配置 |
| `lingXi-quartz` | `com.lingXi.quartz` | 定时任务 |
| `lingXi-generator` | `com.lingXi.generator` | 代码生成 |
| `lingXi-common` | `com.lingXi.common` | 通用工具、常量与基础模型（不依赖任何业务模块） |

技术栈：Spring Boot 4.1 / MyBatis-Plus 3.5 / MySQL 8 / Redis / Spring Security / Druid / Springdoc 3。

## lingXi-manage 业务域

| 包 | 领域 |
| --- | --- |
| `com.lingXi.manage` | 智能零售：售货机、货道、商品、库存、订单、工单、合作商、区域、策略 |
| `com.lingXi.ai` | AI 对话：Agent HTTP 客户端、会话、附件、人工确认写操作、安全配置 |
| `com.lingXi.aiVedio` | AI 视频工作流：章节/人物/场景/分镜/关键帧、图片与视频任务、OSS 转存、outbox/worker |
| `com.lingXi.aiNovel` | AI 小说创作 |
| `com.lingXi.app` | 应用侧业务（小程序/终端接口） |

## 分层规范

- 包结构按 RuoYi 约定：`controller → service(impl) → mapper → domain(entity/dto/vo)`。
- Controller 只做参数校验与结果返回，不写业务逻辑，**不得直接依赖 Mapper**。
- 业务与事务逻辑在 `service/impl`；复杂查询用 Mapper XML（`resources/mapper`）。
- ArchUnit 强制架构规则（`LingXiArchitectureRulesTest`，`mvn test` 会跑）：
  - Controller 不得依赖 `..mapper..`；
  - `com.lingXi.aiVedio` 不得依赖对话域/小说域/管理域（`com.lingXi.ai` 的 client 属基础设施除外）；
  - `com.lingXi.manage` 不得依赖 `com.lingXi.aiVedio`；
  - `com.lingXi.common` 不得依赖任何业务/框架模块。
  - 违反会直接测试失败，不要绕过或降级规则。

## 与 Agent 的集成

- Agent 服务地址、超时、业务工具令牌等配置在 `lingXi-admin/src/main/resources/application*.yml`。
- 调用 Agent 需携带 `X-Agent-Service-Key`：优先 `agent.service-api-key`，
  未设置时回退到进程环境变量 `AGENT_SERVICE_API_KEY`，两侧必须一致。
- 模型名、Key、Base URL 由 Java 按请求搬运给 Agent，代码中不保留模型默认值。
- 视频流程的关键节点：章节分析后不自动生成图片 → 用户手动生成关键帧 → 用户批准后才允许
  提交视频任务；`submission_uncertain=true` 时进入人工核对状态，禁止自动重试提交。

## 配置与安全

- 敏感配置（OSS 密钥、Token 密钥、Agent Key 等）存数据库 `sys_config` 表，
  通过参数管理→安全配置界面维护；`application.yml` / `application-druid.yml` / `.env*`
  中的地址凭据仅限本地，禁止提交修改后的值。
- 密钥字段（SecretStr / @JsonIgnore 等）不得出现在日志或接口响应中。
- 数据库脚本在 `sql/`：新环境用 `lingxi_all.sql` 一站式初始化；**既有环境只能执行独立
  迁移脚本**，禁止反复导入重建语句。新增表结构变化要提供独立迁移脚本。

## 常用命令

```powershell
cd dkd-parent
mvn -pl lingXi-admin -am -DskipTests install   # 编译 + 安装
mvn -pl lingXi-manage -am test                 # 跑测试（含 ArchUnit，不依赖外部服务）
mvn -pl lingXi-admin -am compile
```

测试位于各模块 `src/test`，使用 JUnit + ArchUnit，不依赖 MySQL/Redis/外部服务；改动后必须保证
`mvn test` 通过。单元测试放 `com.lingXi` 对应包下，命名 `XxxTest.java`。