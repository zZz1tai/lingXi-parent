import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'
import { runInNewContext } from 'node:vm'
import { reactive, ref, watch } from 'vue'
import { parse } from '@vue/compiler-sfc'
import { chatSummaryToMarkdown } from '../src/utils/chatSummary.js'
import { createLatestSingleFlight } from '../src/utils/latestSingleFlight.js'

const summary = {
  title: '设备巡检复盘', summary: '已确认需要检查设备。',
  decisions: ['先确认网络'], actions: [{ task: '检查设备', owner: '', due_date: '' }],
  open_questions: ['设备何时恢复？'], message_count: 4, truncated: false
}

test('纪要导出保留结构、来源范围与未明确的负责人期限', () => {
  const text = chatSummaryToMarkdown(summary)
  assert.match(text, /^# 设备巡检复盘/)
  assert.match(text, /基于 4 条消息/)
  assert.match(text, /## 关键结论\n\n- 先确认网络/)
  assert.match(text, /- \[ \] 检查设备（负责人：未明确；期限：未明确）/)
  assert.match(text, /待办尚未执行/)
  assert.match(text, /## 待确认事项\n\n- 设备何时恢复？/)
})

test('纪要导出明确说明截断和空结果，不编造结论与行动', () => {
  const text = chatSummaryToMarkdown({ ...summary, decisions: [], actions: [], open_questions: [], truncated: true })
  assert.match(text, /仅最近部分内容，已截断/)
  assert.match(text, /暂无明确结论/)
  assert.match(text, /暂无明确待办/)
  assert.match(text, /暂无待确认事项/)
})

test('模型文本不能在纪要中注入 HTML、图片、链接或额外标题', () => {
  const text = chatSummaryToMarkdown({
    ...summary,
    title: '<script>alert(1)</script>\n# 假标题',
    decisions: ['![图片](https://example.com/track)'],
    actions: [{ task: '维护', owner: '张三', due_date: '周五' }]
  })
  assert.ok(!text.includes('<script>'))
  assert.ok(!text.includes('\n# 假标题'))
  assert.ok(text.includes('\\!\\[图片\\]\\(https://example\\.com/track\\)'))
  assert.match(text, /负责人：张三；期限：周五/)
})

const component = await readFile(new URL('../src/views/ai/components/ChatSummary.vue', import.meta.url), 'utf8')

// 执行组件的实际状态逻辑，不编译 Vue；模型接口由可控 Promise 替代。
function mountSummary(t, request) {
  const props = reactive({ sessionId: 'session-a', sourceKey: 'history-a', disabled: false })
  const cleanups = []
  const script = component.slice(component.indexOf('const props ='), component.indexOf('</script>'))
  const state = runInNewContext(`${script}\n;({ generate, invalidate, visible, busy, result, error });`, {
    defineProps: () => props,
    ref,
    watch: (...args) => cleanups.push(watch(...args)),
    onBeforeUnmount: cleanup => cleanups.push(cleanup),
    createLatestSingleFlight,
    generateChatSummary: request
  })
  t.after(() => cleanups.forEach(cleanup => cleanup()))
  return { props, ...state }
}

test('纪要模板结构可以解析，预览不将模型内容作为 HTML', () => {
  assert.deepEqual(parse(component).errors, [])
  assert.ok(!component.includes('v-html'))
})

test('纪要请求防重复，切换会话后旧结果不能污染新会话', async t => {
  const pending = []
  const state = mountSummary(t, sessionId => new Promise(resolve => pending.push({ sessionId, resolve })))
  const first = state.generate()
  await state.generate()
  assert.equal(pending.length, 1)
  state.props.sessionId = 'session-b'
  const second = state.generate()
  await Promise.resolve()
  assert.equal(pending[1].sessionId, 'session-b')
  pending[0].resolve({ data: summary })
  await first
  assert.equal(state.result.value, null)
  assert.equal(state.busy.value, true)
  pending[1].resolve({ data: { ...summary, title: '新会话纪要' } })
  await second
  assert.equal(state.result.value.title, '新会话纪要')
  assert.equal(state.busy.value, false)
})

test('历史更新使迟到错误失效，当前失败可以重试且不显示旧结果', async t => {
  let rejectRequest
  let calls = 0
  const state = mountSummary(t, () => {
    calls += 1
    if (calls === 1) return new Promise((_, reject) => { rejectRequest = reject })
    if (calls === 2) throw new Error('模型暂不可用')
    return { data: summary }
  })
  const first = state.generate()
  await Promise.resolve()
  state.props.sourceKey = 'history-updated'
  rejectRequest(new Error('过期错误'))
  await first
  assert.equal(state.error.value, '')
  await state.generate()
  assert.equal(state.error.value, '模型暂不可用')
  assert.equal(state.result.value, null)
  await state.generate()
  assert.equal(state.error.value, '')
  assert.equal(state.result.value.title, summary.title)
})

test('空会话或生成中的聊天不能发起纪要请求', async t => {
  let calls = 0
  const state = mountSummary(t, () => { calls += 1 })
  state.props.sessionId = ''
  await state.generate()
  state.props.sessionId = 'session-a'
  state.props.disabled = true
  await state.generate()
  assert.equal(calls, 0)
})
