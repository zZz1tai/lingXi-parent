import request from '@/utils/request'
import { streamChatWithQwen } from '@/api/ai'

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

// ── 短篇正文 ──────────────────────────────────────────

export function saveNovelManuscript(workId, data) {
  return request({
    url: '/novel/work/' + workId + '/manuscript',
    method: 'put',
    data: data
  })
}

/**
 * 流式调用灵犀智能助手进行创作（复用现有 AI 对话链路）。
 * 通过 prompt 携带作品上下文，让模型按指定角色/要求续写或润色。
 */
export function streamNovelWrite(payload, { signal, onChunk, onEvent } = {}) {
  return streamChatWithQwen(
    payload.message,
    payload.sessionId,
    payload.userId,
    payload.userName,
    { signal, onChunk, onEvent }
  )
}
