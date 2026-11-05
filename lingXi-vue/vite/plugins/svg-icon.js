import { readFile, readdir } from 'node:fs/promises'
import path from 'node:path'
import { optimize } from 'svgo'

const registerId = 'virtual:svg-icons-register'
const namesId = 'virtual:svg-icons-names'
const moduleIds = [registerId, namesId]

export function iconSymbolId(file) {
  return `icon-${file.replace(/\\/g, '/').replace(/\.svg$/i, '').replace(/\//g, '-')}`
}

export function toSvgSymbol(source, id) {
  let foundSvg = false
  const result = optimize(source, {
    plugins: [
      'removeDoctype', 'removeXMLProcInst', 'removeComments',
      { name: 'inlineStyles', params: { onlyMatchedOnce: false } },
      'convertStyleToAttrs',
      {
        name: 'normalizeLocalReferences',
        type: 'visitor',
        fn: () => ({ element: { enter(node) {
          // SVGO 2 的 prefixIds 仅识别属性中无引号的 url(#id)。
          for (const name of Object.keys(node.attributes)) {
            node.attributes[name] = node.attributes[name].replace(/url\(\s*(['"]?)#([^\s'"()]+)\1\s*\)/g, 'url(#$2)')
          }
        } } })
      },
      { name: 'prefixIds', params: { prefix: id } },
      'removeDimensions',
      {
        name: 'svgToSymbol',
        type: 'visitor',
        fn: () => ({ element: { enter(node, parent) {
          if (parent.type !== 'root' || node.name !== 'svg') return
          foundSvg = true
          // 保留原根 ID 的引用，同时为外部 <use> 提供稳定的图标 ID。
          if (node.attributes.id) {
            node.children = [{ type: 'element', name: 'g', attributes: { id: node.attributes.id }, children: node.children }]
          }
          node.name = 'symbol'
          node.attributes.id = id
        } } })
      }
    ]
  })
  if (result.error) throw new Error(`图标 ${id} 解析失败：${result.error}`)
  if (!foundSvg) throw new Error(`图标 ${id} 缺少 SVG 根元素`)
  return result.data
}

export default function createSvgIcon() {
  let iconDir = path.resolve('src/assets/icons/svg')
  return {
    name: 'lingxi-svg-icons',
    configResolved(config) {
      iconDir = path.resolve(config.root, 'src/assets/icons/svg')
    },
    resolveId(id) {
      return moduleIds.includes(id) ? `\0${id}` : null
    },
    async load(id) {
      if (!moduleIds.some(name => id === `\0${name}`)) return null
      const entries = await readdir(iconDir, { recursive: true, withFileTypes: true })
      const files = entries.filter(entry => entry.isFile() && entry.name.endsWith('.svg'))
        .map(entry => path.join(entry.parentPath, entry.name)).sort()
      const ids = files.map(file => iconSymbolId(path.relative(iconDir, file)))
      if (new Set(ids).size !== ids.length) throw new Error('SVG 图标 ID 重复，请调整目录或文件名')
      this.addWatchFile(iconDir)
      files.forEach(file => this.addWatchFile(file))
      if (id === `\0${namesId}`) return `export default ${JSON.stringify(ids)}`
      const symbols = await Promise.all(files.map(async (file, index) => toSvgSymbol(await readFile(file, 'utf8'), ids[index])))
      return `
if (typeof document !== 'undefined') {
  const register = () => {
    let sprite = document.getElementById('__svg__icons__dom__')
    if (!sprite) {
      sprite = document.createElementNS('http://www.w3.org/2000/svg', 'svg')
      sprite.id = '__svg__icons__dom__'
      sprite.setAttribute('aria-hidden', 'true')
      sprite.style.cssText = 'position:absolute;width:0;height:0;overflow:hidden'
      document.body.appendChild(sprite)
    }
    sprite.innerHTML = ${JSON.stringify(symbols.join(''))}
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', register, { once: true })
  else register()
}
export default {}`
    },
    configureServer(server) {
      // 只监听图标变更，不安装 HTTP 中间件，沿用 Vite 自身的跨域和主机校验。
      server.watcher.add(iconDir)
      server.watcher.on('all', (event, file) => {
        const relative = path.relative(iconDir, file)
        if (!['add', 'change', 'unlink'].includes(event) || relative.startsWith('..') || path.isAbsolute(relative) || !relative.endsWith('.svg')) return
        for (const id of moduleIds) {
          const module = server.moduleGraph.getModuleById(`\0${id}`)
          if (module) server.moduleGraph.invalidateModule(module)
        }
        server.ws.send({ type: 'full-reload', path: '*' })
      })
    }
  }
}
