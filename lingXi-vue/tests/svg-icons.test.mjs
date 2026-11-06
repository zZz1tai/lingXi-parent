import assert from 'node:assert/strict'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { runInNewContext } from 'node:vm'
import test from 'node:test'
import createSvgIcon, { iconSymbolId, toSvgSymbol } from '../vite/plugins/svg-icon.js'

const root = fileURLToPath(new URL('..', import.meta.url))

test('图标标识兼容原有目录规则，并从尺寸补齐 viewBox', () => {
  assert.equal(iconSymbolId('tool.svg'), 'icon-tool')
  assert.equal(iconSymbolId('nested/dir/tool.svg'), 'icon-nested-dir-tool')
  assert.equal(iconSymbolId('nested\\tool.svg'), 'icon-nested-tool')
  const symbol = toSvgSymbol('<svg width="128" height="64"><path fill="currentColor" d="M0 0h128v64z"/></svg>', 'icon-tool')
  assert.match(symbol, /<symbol[^>]*viewBox="0 0 128 64"[^>]*id="icon-tool"/)
  assert.ok(!symbol.includes('width='))
  assert.ok(symbol.includes('fill="currentColor"'))
  const existing = toSvgSymbol('<svg width="24" height="24" viewBox="-2 -3 48 32"><path d="M0 0h1"/></svg>', 'icon-box')
  assert.ok(existing.includes('viewBox="-2 -3 48 32"'))
})

test('图标样式、根 ID、内部引用和渐变在拼接后保持独立', () => {
  const source = `<svg id="root" width="24" height="24" xmlns:xlink="http://www.w3.org/1999/xlink">
    <defs><linearGradient id="paint"/><clipPath id="clip"><path d="M0 0h5"/></clipPath></defs>
    <style>.shape{fill:url(#paint)} .shape:hover{opacity:.5}</style>
    <path id="outline" class="shape" clip-path="url('#clip')" style="stroke:url('#paint')"/>
    <use href="#outline"/><use xlink:href="#outline"/>
  </svg>`
  for (const id of ['icon-first', 'icon-second']) {
    const symbol = toSvgSymbol(source, id)
    for (const local of ['root', 'paint', 'clip', 'outline']) assert.ok(symbol.includes(`id="${id}__${local}"`))
    assert.ok(symbol.includes(`id="${id}"`))
    assert.ok(symbol.includes(`url(#${id}__paint)`))
    assert.ok(symbol.includes(`url(#${id}__clip)`))
    assert.ok(symbol.includes(`href="#${id}__outline"`))
    assert.ok(symbol.includes(`xlink:href="#${id}__outline"`))
    assert.ok(symbol.includes(`.${id}__shape:hover`))
    assert.ok(!symbol.includes('url(#paint)'))
  }
  assert.throws(() => toSvgSymbol('<svg><path></svg>', 'icon-invalid'), /解析失败/)
  assert.throws(() => toSvgSymbol('<html/>', 'icon-invalid'), /SVG 根元素/)
})

test('虚拟模块完整注册仓库图标，重复执行不会追加第二个 sprite', async () => {
  const plugin = createSvgIcon()
  plugin.configResolved({ root })
  const watched = []
  const context = { addWatchFile: file => watched.push(file) }
  const nameCode = await plugin.load.call(context, plugin.resolveId('virtual:svg-icons-names'))
  const ids = runInNewContext(nameCode.replace('export default ', ''))
  assert.ok(ids.includes('icon-button'))
  assert.ok(ids.includes('icon-工单管理'))
  const registerCode = await plugin.load.call(context, plugin.resolveId('virtual:svg-icons-register'))
  assert.equal((registerCode.match(/<symbol\b/g) || []).length, ids.length)
  let sprite
  let appends = 0
  const document = {
    readyState: 'complete',
    getElementById: () => sprite,
    createElementNS: () => ({ style: {}, setAttribute() {} }),
    body: { appendChild: value => { sprite = value; appends++ } }
  }
  const script = registerCode.replace('export default {}', '')
  runInNewContext(script, { document })
  runInNewContext(script, { document })
  assert.equal(appends, 1)
  assert.equal((sprite.innerHTML.match(/<symbol\b/g) || []).length, ids.length)
  assert.ok(watched.includes(path.join(root, 'src/assets/icons/svg/button.svg')))
  assert.ok(watched.every(file => file.endsWith('.svg')), '目录不能作为 Vite 模块依赖')
  assert.equal(plugin.resolveId('unrelated'), null)
  assert.equal(await plugin.load.call(context, 'unrelated'), null)
})

test('图标监听会刷新虚拟模块且不会安装放宽跨域的中间件', () => {
  const plugin = createSvgIcon()
  plugin.configResolved({ root })
  let onChange
  let reloads = 0
  let invalidated = 0
  plugin.configureServer({
    watcher: { add() {}, on(event, callback) { assert.equal(event, 'all'); onChange = callback } },
    moduleGraph: { getModuleById: id => ({ id }), invalidateModule() { invalidated++ } },
    ws: { send(message) { assert.equal(message.type, 'full-reload'); reloads++ } },
    get middlewares() { throw new Error('图标插件不得改变 HTTP 安全策略') }
  })
  onChange('change', path.join(root, 'src/assets/icons/svg/button.svg'))
  assert.equal(reloads, 1)
  assert.equal(invalidated, 2)
  onChange('change', path.join(root, 'src/main.js'))
  assert.equal(reloads, 1)
})
