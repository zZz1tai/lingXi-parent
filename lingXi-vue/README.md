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
- `npm run build:exe`：Windows Tauri/NSIS 打包，需要另行准备 Rust、MSVC C++ 构建工具、WebView2。前端构建由 Tauri 配置驱动；不属于浏览器开发前置条件。

## 目录

`src/views` 为页面（`manage`、`ai`、`aiVedio`、`novel`），`src/api` 为接口，`src/utils/request.js` 处理认证与通用错误，`src/store` 管理状态，`tests` 为 Node 测试。
