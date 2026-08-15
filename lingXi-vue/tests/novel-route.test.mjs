import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const routerSource = await readFile(
  new URL('../src/router/index.js', import.meta.url),
  'utf8'
)
const menuMigration = await readFile(
  new URL('../../dkd-parent/sql/migration_ai_novel_menu_to_aivedio.sql', import.meta.url),
  'utf8'
)

test('小说旧入口会重定向到实际子页面', () => {
  assert.match(
    routerSource,
    /path:\s*['"]\/novel\/writing['"][\s\S]*?redirect:\s*['"]\/novel\/writing\/index['"]/
  )
})

test('小说动态菜单使用稳定的非空叶子路由', () => {
  assert.match(menuMigration, /SET path = 'writing'/)
  assert.match(menuMigration, /component = 'novel\/index'/)
  assert.doesNotMatch(menuMigration, /SET path = ''/)
})

test('菜单迁移会补齐新父目录的角色授权', () => {
  assert.match(menuMigration, /SELECT DISTINCT rm\.role_id, @ai_vedio_parent_id/)
  assert.match(menuMigration, /m\.perms LIKE 'novel:%'/)
})
