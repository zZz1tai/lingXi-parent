# LingXi 管理端

Vue 3、Vite 7、Element Plus、Pinia、Vue Router；同时支持可选的 Tauri 2 桌面打包。版本以 `package.json` 和锁文件为准。

## 浏览器开发

全栈部署见 [根 README](../README.md)，智能体规则见 [AGENTS.md](AGENTS.md)。
需要 Node.js 20.19+（20.x）或 22.12+ 及 npm。仅浏览器开发不需要 Rust、Tauri 构建工具或桌面安装包。

在本目录执行：

```powershell
npm ci
npm run dev
```

默认访问 `http://localhost:3000`，端口占用时以 Vite 输出为准。`/dev-api` 默认代理到 `http://127.0.0.1:8080`；Java 必须可用才能登录、读取菜单和操作业务。
自定义后端地址可在启动前设置 `$env:VITE_APP_PROXY_TARGET='http://127.0.0.1:8081'`，无需修改跟踪的 `.env` 文件。

## 验证与发布

- `npm test`：运行纯逻辑测试；依赖安装完成后不需要数据库或模型。
- `npm run build:prod`：仅在需要发布 Web 产物时执行，输出 `dist/`；修改前端后不要求构建验证。
- 生产 Web 服务器需配置 SPA 回退，并把 `/prod-api` 反向代理到 Java（去掉该前缀）；SSE 代理关闭缓冲并设置合适超时。
- `npm run build:exe`：先打包私有 Java/Python/MySQL/Redis 运行环境，再生成 Windows Tauri/NSIS 完整安装包。构建机需要 Rust、MSVC、JDK/Maven、Python 3.12 x64；普通用户无需安装开发环境或部署数据库。下载、数据目录、端口、自动更新及 GitHub Release 流程统一见 [根 README 的完整桌面版说明](../README.md#windows-完整桌面版无需手工部署)。
- `npm run build:desktop`：只生成桌面前端资源，固定连接私有业务接口 `127.0.0.1:18080`；不单独生成可完整运行的 EXE。开发时仍用根 `start.ps1` 启动服务。

## 目录

`src/views` 为页面（`manage`、`ai`、`aiVedio`、`novel`），`src/api` 为接口，`src/utils/request.js` 处理认证与通用错误，`src/store` 管理状态，`tests` 为 Node 测试。
