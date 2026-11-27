import { spawnSync } from 'node:child_process'

// 固定连接安装包管理的私有服务，避免沿用开发机 .env 中的地址。
const result = spawnSync(process.execPath, ['node_modules/vite/bin/vite.js', 'build', '--mode', 'desktop'], {
  stdio: 'inherit',
  env: { ...process.env, DESKTOP_BUILD: 'true', VITE_APP_BASE_API: 'http://127.0.0.1:18080' }
})
process.exit(result.status ?? 1)
