# AGENTS.md — lingXi-vue（Vue 3 管理端 + Tauri 桌面端）

## 项目概览

基于 RuoYi-Vue 演进的灵犀管理端：Vue 3.5 + Vite 7 + Element Plus 2.14 + Pinia + Vue Router 4，
可选 Tauri 2 打包为 Windows 桌面端（`build:desktop` / `build:exe`）。
开发时 Vite 监听 3000，将 `/dev-api/*` 代理到 `http://127.0.0.1:8080`。

## 常用命令

```powershell
cd lingXi-vue
npm run dev            # 开发（端口 3000，自动打开浏览器）
npm run test           # node --test tests/*.test.mjs
npm run build:prod     # Web 生产构建
npm run build:stage    # staging 构建
npm run build:desktop  # Tauri 桌面资源构建（DESKTOP_BUILD=true，资源用相对路径）
npm run build:exe      # NSIS Windows 安装包
```

`package.json` 版本号必须与 `dkd-parent/pom.xml` 保持一致（当前 3.8.7）。

## 目录约定

| 路径 | 说明 |
| --- | --- |
| `src/views/` | 页面组件；AI 对话在 `ai/`，AI 视频在 `aiVedio/`，小说在 `novel/`，零售在 `manage/` |
| `src/api/` | 接口封装，与 `views/` 一一对应（`ai.js`、`aiVedio/`、`novel/`、`manage/` 等）；组件不直接写 `axios` |
| `src/utils/request.js` | 统一 axios 实例：Token 注入、错误码处理、401 跳转 |
| `src/store/` | Pinia 状态 |
| `src/router/` | 路由（与后端菜单动态生成配合） |
| `src/components/` | 通用组件 |
| `src/utils/` | 工具（`request-data.js`、`markdownSafety.js`、`quickVideoImages.js` 等，均有对应测试） |
| `tests/` | `node --test` 纯逻辑单测（数据转换、安全过滤等，不依赖 DOM/浏览器） |

## 代码规范

- 新页面：`src/views/<域>/` 建页面组件 + `src/api/<域>/` 建接口模块，遵循现有页面模式。
- 使用 Element Plus 组件与 `@element-plus/icons-vue` 图标，保持中文文案。
- 新增纯逻辑（数据转换、字段校验、安全过滤等）时同步在 `tests/` 补 `node --test` 用例。
- 不要新增 UI 组件库；图表用 ECharts（已在依赖中）。
- `.env.development` / `.env.production` 等本地配置禁止提交改动。

## 与后端交互

- 接口返回约定（RuoYi 风格）：`{code, msg, data}`，`code === 200` 为成功；
  `src/utils/request.js` 已统一处理，业务页面用 `request({...})` 封装。
- 登录态用 Token（`src/utils/auth.js`），路由守卫在 `src/permission.js`。
- 桌面端构建时资源必须使用相对路径（`base: './'`），不要引入绝对 URL。