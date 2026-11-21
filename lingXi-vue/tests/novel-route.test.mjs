import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const routerSource = await readFile(
  new URL('../src/router/index.js', import.meta.url),
  'utf8'
)
const initialSchema = await readFile(
  new URL('../../dkd-parent/sql/lingxi_all.sql', import.meta.url),
  'utf8'
)
const menuMigration = await readFile(
  new URL('../../dkd-parent/sql/upgrades/novel_menu_hierarchy.sql', import.meta.url),
  'utf8'
)

test('小说旧入口会重定向到实际子页面', () => {
  assert.match(
    routerSource,
    /path:\s*['"]\/novel\/writing['"][\s\S]*?redirect:\s*['"]\/novel\/writing\/index['"]/
  )
})

for (const [source, sql] of [['新库初始化', initialSchema], ['旧库升级', menuMigration]]) {
  test(source + '保留小说写作台的非空叶子路由', () => {
    assert.match(sql, /UPDATE\s+sys_menu\s+SET\s+path\s*=\s*'writing',\s*component\s*=\s*'novel\/index'[^;]*WHERE\s+menu_id\s*=\s*@novel_writing_menu_id/i)
    assert.doesNotMatch(sql, /SET\s+path\s*=\s*''[^;]*WHERE\s+menu_id\s*=\s*@novel_writing_menu_id/i)
  })
}

test('旧库中已有小说权限的角色可访问视频目录、小说目录和写作台', () => {
  const grants = menuMigration.split(';').filter(sql => /INSERT INTO sys_role_menu/i.test(sql))
  for (const menu of ['ai_vedio_parent_id', 'novel_parent_id', 'novel_writing_menu_id']) {
    const grant = grants.find(sql => sql.includes(`SELECT DISTINCT rm.role_id, @${menu}`))
    assert.ok(grant, '缺少角色授权 ' + menu)
    assert.match(grant, /JOIN sys_menu m ON m\.menu_id = rm\.menu_id/i)
    assert.match(grant, /m\.perms LIKE 'novel:%'/i)
  }
})
