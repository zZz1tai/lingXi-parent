# LingXi Java 业务服务

负责登录权限、零售业务、AI 会话与视频/小说业务数据、事务、任务状态、OSS 转存及人工确认。模型调用通过 HTTP 交给 Python Agent。

## 首次运行

全栈部署、配置变量和排错以 [根 README](../README.md) 为准，智能体先读本目录 [AGENTS.md](AGENTS.md)。

必需：JDK 17（需有 `javac`）、Maven、MySQL 8、Redis。版本声明以 `pom.xml` 和 CI 为准。测试不需要启动 MySQL/Redis，首次下载 Maven 依赖需要网络。

1. 在全新数据库服务器导入 `sql/lingxi_all.sql`（创建 `dkd`，含表重建语句）。已有数据库须备份后对照结构差异编写升级 SQL，禁止重新导入总脚本。
2. 配置数据库账号密码、Redis 和可写上传目录；不要提交本机配置。Java 本身不会读取 Python 的 `.env`，只有根启动脚本会导入指定变量。
3. 在本目录执行：

```powershell
mvn -pl lingXi-admin -am -DskipTests install
mvn -f lingXi-admin/pom.xml -DskipTests -Dspring-boot.run.main-class=com.lingXi.LingXiApplication spring-boot:run
```

HTTP 默认 `8080`。Agent 默认 `http://localhost:5000`；启用 AI 时两侧必须使用相同的 `AGENT_SERVICE_API_KEY`。模型与 OSS 凭据通过管理界面按需配置，基础登录不需要模型服务。

## 模块

| 模块 | 职责 |
| --- | --- |
| lingXi-admin | Spring Boot 入口 `com.lingXi.LingXiApplication`、应用配置 |
| lingXi-manage | 零售、AI 对话、视频、小说、应用侧业务 |
| lingXi-system | 用户、角色、菜单、字典、参数 |
| lingXi-framework | 认证、Redis、Web、MyBatis、数据源 |
| lingXi-common | 通用工具和模型 |
| lingXi-quartz | 定时任务 |
| lingXi-generator | 代码生成 |

## 测试与发布

在本目录运行 `mvn -pl lingXi-admin -am test`，包含架构与离线契约测试；不得绕过 ArchUnit 规则。
发布运行 `mvn -pl lingXi-admin -am -DskipTests package`，再用 `java -jar lingXi-admin/target/lingXi-admin.jar` 启动（以实际产物文件名为准）。
生产部署需明确启用 `druid,prod` profile、设置环境变量、保护网络边界并备份 MySQL、上传文件；配置要求见根 README。不要用开发启动脚本代替生产进程管理。
