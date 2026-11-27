import test from 'node:test'
import assert from 'node:assert/strict'
import { createDesktopUpdater } from '../src/utils/desktopUpdates.js'
import { resolveSettingsSection, settingsTarget } from '../src/utils/settingsNavigation.js'
import { workspacePage } from '../src/utils/workspacePages.js'

test('管理页面有统一标题，创作工作台和嵌入页面不重复添加标题', () => {
  assert.deepEqual(workspacePage({ path: '/manage/region', meta: { title: '区域管理' } }),
    { category: '业务管理', title: '区域管理', description: '维护业务区域，查看各区域关联的点位数量。' })
  assert.equal(workspacePage({ path: '/settings', meta: { title: '设置' } }), null)
  assert.equal(workspacePage({ path: '/aiVedio/project', meta: { title: '视频创作' } }), null)
  assert.equal(workspacePage({ path: '/system/user', meta: { title: '用户', hidePageHeading: true } }), null)
})

test('旧设置入口集中到设置中心，无权限或浏览器不支持的分类回退', () => {
  assert.deepEqual(settingsTarget('system/config/index'), { path: '/settings', query: { section: 'parameters' } })
  assert.deepEqual(settingsTarget('aiVedio/modelConfig/index'), { path: '/settings', query: { section: 'models' } })
  assert.equal(settingsTarget('manage/order/index'), null)
  assert.equal(resolveSettingsSection('storage', ['appearance', 'account']), 'appearance')
})

test('并发检查只请求一次，展示版本和说明', async () => {
  let calls = 0
  let complete
  const updater = createDesktopUpdater({ check: () => { calls++; return new Promise(resolve => { complete = resolve }) }, stopServices: async () => {} })
  const first = updater.checkForUpdates()
  const second = updater.checkForUpdates()
  complete({ version: '3.8.8', body: '改进存储', close: async () => {} })
  await Promise.all([first, second])
  assert.equal(calls, 1)
  assert.equal(updater.state.phase, 'available')
  assert.equal(updater.state.notes, '改进存储')
})

test('下载及签名校验成功后才停服务并安装，重复点击不重复安装', async () => {
  const events = []
  const updater = createDesktopUpdater({
    check: async () => ({ version: '3.8.8', download: async progress => {
      events.push('download'); progress({ event: 'Started', data: { contentLength: 100 } });
      progress({ event: 'Progress', data: { chunkLength: 100 } })
    }, install: async () => events.push('install') }),
    stopServices: async () => events.push('stop')
  })
  await updater.checkForUpdates()
  await Promise.all([updater.installUpdate(), updater.installUpdate()])
  assert.deepEqual(events, ['download', 'stop', 'install'])
  assert.equal(updater.state.progress, 100)
})

test('离线与下载失败保留可运行的本机服务', async () => {
  let stopped = false
  const updater = createDesktopUpdater({
    check: async () => ({ version: '3.8.8', download: async () => { throw new Error('bad signature') } }),
    stopServices: async () => { stopped = true }
  })
  await updater.checkForUpdates()
  await updater.installUpdate()
  assert.equal(stopped, false)
  assert.equal(updater.state.phase, 'error')
  const offline = createDesktopUpdater({ check: async () => { throw new Error('offline') }, stopServices: async () => {} })
  assert.equal(await offline.checkForUpdates(), false)
  assert.match(offline.state.error, /网络/)
})
