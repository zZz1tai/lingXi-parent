import request from '@/utils/request'
import { streamSse } from '@/api/ai'

/**
 * AI 小说创作工作台接口契约。
 * 后端尚未提供这些接口时，调用会返回 404，页面会给出明确的降级提示；
 * 所有接口路径按 RuoYi 风格命名，后续 Java 侧按此契约实现即可直接打通。
 */

// ── 作品 ──────────────────────────────────────────────

export function listNovelWork(query) {
  return request({
    url: '/novel/work/list',
    method: 'get',
    params: query
  })
}

export function getNovelWork(workId) {
  return request({
    url: '/novel/work/' + workId,
    method: 'get'
  })
}

export function addNovelWork(data) {
  return request({
    url: '/novel/work',
    method: 'post',
    data: data
  })
}

export function updateNovelWork(data) {
  return request({
    url: '/novel/work',
    method: 'put',
    data: data
  })
}

export function delNovelWork(workId) {
  return request({
    url: '/novel/work/' + workId,
    method: 'delete'
  })
}

/**
 * 根据书名自动拟写故事梗概（服务端直接调用 LLM，不进入创作会话）。
 * @param {{workName: string, workType: string, genre?: string}} data
 */
export function generateNovelSynopsis(data) {
  return request({
    url: '/novel/synopsis/generate',
    method: 'post',
    data: data,
    timeout: 60000
  })
}

/**
 * 流式拟写故事梗概（SSE），梗概文本逐字回调
 * @param {{workName: string, workType: string, genre?: string}} data
 * @param {{signal?: AbortSignal, onChunk?: (text: string) => void}} options
 */
export function streamNovelSynopsis(data, { signal, onChunk } = {}) {
  return streamSse('/novel/synopsis/stream', {
    body: data,
    signal,
    onChunk
  })
}

// ── 章节（长篇） ──────────────────────────────────────

export function listNovelChapter(workId) {
  return request({
    url: '/novel/work/' + workId + '/chapter/list',
    method: 'get'
  })
}

export function getNovelChapter(workId, chapterId) {
  return request({
    url: '/novel/work/' + workId + '/chapter/' + chapterId,
    method: 'get'
  })
}

export function addNovelChapter(workId, data) {
  return request({
    url: '/novel/work/' + workId + '/chapter',
    method: 'post',
    data: data
  })
}

export function updateNovelChapter(workId, data) {
  return request({
    url: '/novel/work/' + workId + '/chapter',
    method: 'put',
    data: data
  })
}

export function delNovelChapter(workId, chapterId) {
  return request({
    url: '/novel/work/' + workId + '/chapter/' + chapterId,
    method: 'delete'
  })
}

export function sortNovelChapter(workId, chapterIds) {
  return request({
    url: '/novel/work/' + workId + '/chapter/sort',
    method: 'put',
    data: { chapterIds }
  })
}

// ── 设定集（长篇：人物/世界观/大纲索引卡） ────────────

export function listNovelSetting(workId, type) {
  return request({
    url: '/novel/work/' + workId + '/setting/list',
    method: 'get',
    params: { type }
  })
}

export function addNovelSetting(workId, data) {
  return request({
    url: '/novel/work/' + workId + '/setting',
    method: 'post',
    data: data
  })
}

export function updateNovelSetting(workId, data) {
  return request({
    url: '/novel/work/' + workId + '/setting',
    method: 'put',
    data: data
  })
}

export function delNovelSetting(workId, settingId) {
  return request({
    url: '/novel/work/' + workId + '/setting/' + settingId,
    method: 'delete'
  })
}

// ── 伏笔（长篇：埋设/待解/已解追踪） ────────────────

export function listNovelForeshadow(workId, status) {
  return request({
    url: '/novel/work/' + workId + '/foreshadow/list',
    method: 'get',
    params: { status }
  })
}

export function addNovelForeshadow(workId, data) {
  return request({
    url: '/novel/work/' + workId + '/foreshadow',
    method: 'post',
    data: data
  })
}

export function updateNovelForeshadow(workId, data) {
  return request({
    url: '/novel/work/' + workId + '/foreshadow',
    method: 'put',
    data: data
  })
}

export function delNovelForeshadow(workId, foreshadowId) {
  return request({
    url: '/novel/work/' + workId + '/foreshadow/' + foreshadowId,
    method: 'delete'
  })
}

// ── 短篇正文 ──────────────────────────────────────────

export function saveNovelManuscript(workId, data) {
  return request({
    url: '/novel/work/' + workId + '/manuscript',
    method: 'put',
    data: data
  })
}

// ── 三层大纲（长篇：全书→卷→章） ─────────────────────

export function listNovelOutline(workId) {
  return request({
    url: '/novel/work/' + workId + '/outline/list',
    method: 'get'
  })
}

/**
 * AI 生成三层大纲并全量保存，返回 { tree, gaps }（tree 为 BOOK→VOLUME→CHAPTER
 * 嵌套结构，gaps 为断链检查报告）。
 * @param {number} workId
 */
export function generateNovelOutline(workId) {
  return request({
    url: '/novel/work/' + workId + '/outline/generate',
    method: 'post',
    timeout: 120000
  })
}

export function addNovelOutline(workId, data) {
  return request({
    url: '/novel/work/' + workId + '/outline',
    method: 'post',
    data: data
  })
}

export function updateNovelOutline(workId, data) {
  return request({
    url: '/novel/work/' + workId + '/outline',
    method: 'put',
    data: data
  })
}

export function delNovelOutline(workId, outlineId) {
  return request({
    url: '/novel/work/' + workId + '/outline/' + outlineId,
    method: 'delete'
  })
}

export function sortNovelOutline(workId, parentId, outlineIds) {
  return request({
    url: '/novel/work/' + workId + '/outline/sort',
    method: 'put',
    data: { parentId, outlineIds }
  })
}

/**
 * 流式调用小说创作智能体（独立智能体，自动联网核查）。
 * 服务端从作品库组装作品上下文（梗概/章节/正文末尾/设定卡），
 * 浏览器仅提交创作指令、作品会话与作品/章节标识。
 */
export function streamNovelWrite(payload, { signal, onChunk, onEvent } = {}) {
  return streamSse('/novel/write/stream', {
    body: {
      message: payload.message,
      sessionId: payload.sessionId,
      workId: payload.workId,
      chapterId: payload.chapterId
    },
    signal,
    onChunk,
    onEvent
  })
}
