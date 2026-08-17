import request, { download } from '@/utils/request'
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
    timeout: 1000000
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

/**
 * 对话式小说构思：模糊创意经过多轮追问后返回结构化构思文档。
 * @param {{message: string, sessionId: string}} data
 */
export function streamNovelIdea(data, { signal, onChunk, onEvent } = {}) {
  return streamSse('/novel/idea/stream', {
    body: {
      message: data.message,
      sessionId: data.sessionId
    },
    signal,
    onChunk,
    onEvent
  })
}

/** 由已经确认的构思文档创建长篇作品及首批设定卡。 */
export function createNovelWorkFromIdea(data) {
  return request({
    url: '/novel/idea/create-work',
    method: 'post',
    data
  })
}

/** 清理不再继续使用的构思会话 checkpoint。 */
export function deleteNovelIdeaThread(sessionId) {
  return request({
    url: '/novel/idea/thread',
    method: 'delete',
    params: { sessionId }
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

/** 提交章节资料分析异步任务，接口立即返回任务状态。 */
export function submitNovelContextTask(workId, chapterId, force = false) {
  return request({
    url: '/novel/work/' + workId + '/context/analyze',
    method: 'post',
    data: { chapterId, force }
  })
}

/** 查询指定章节资料分析任务。 */
export function getNovelContextTask(workId, taskId) {
  return request({
    url: '/novel/work/' + workId + '/context/task/' + taskId,
    method: 'get'
  })
}

/** 查询章节最近一次资料分析任务，用于刷新页面后恢复。 */
export function getLatestNovelContextTask(workId, chapterId) {
  return request({
    url: '/novel/work/' + workId + '/context/task/latest',
    method: 'get',
    params: { chapterId }
  })
}

/** 应用用户在确认框中勾选的资料变化。 */
export function applyNovelContextChanges(workId, data) {
  return request({
    url: '/novel/work/' + workId + '/context/apply',
    method: 'post',
    data
  })
}

/**
 * 导出作品全文为 txt（后端按章节顺序完整拼接，长篇不依赖前端已加载章节）。
 */
export function exportNovelWorkText(workId, fileName) {
  return download('/novel/work/' + workId + '/export', {}, fileName)
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
    timeout: 1000000
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
 * 分析章节节奏（评分/实际档位/维度/问题清单与建议）。
 * 请求体字段与 Java NovelPacingRequestDTO 对齐：workName/genre/chapterTitle/pacingLevel/content。
 * @param {{workName: string, genre?: string, chapterTitle?: string, pacingLevel?: string, content: string}} data
 */
export function analyzeNovelPacing(data) {
  return request({
    url: '/novel/work/pacing/analyze',
    method: 'post',
    data: data,
    timeout: 1000000
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
