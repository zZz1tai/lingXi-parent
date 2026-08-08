import request from '@/utils/request';
import { getToken } from '@/utils/auth';

const apiBaseUrl = (import.meta.env.VITE_APP_BASE_API || '').replace(/\/$/, '');

function apiUrl(path) {
  return `${apiBaseUrl}/${path.replace(/^\//, '')}`;
}

async function responseError(response) {
  const fallback = `请求失败（${response.status}）`;
  try {
    const text = await response.text();
    if (!text) return fallback;
    const payload = JSON.parse(text);
    return payload.msg || payload.message || fallback;
  } catch {
    return fallback;
  }
}

function parseSseEvent(block) {
  let event = 'message';
  const data = [];

  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim();
    } else if (line.startsWith('data:')) {
      data.push(line.slice(5).replace(/^ /, ''));
    }
  }

  return { event, data: data.join('\n') };
}

function structuredEvent(event, data) {
  if (!data || data === '[DONE]') return null;
  try {
    const payload = JSON.parse(data);
    if (payload && typeof payload === 'object' && !Array.isArray(payload)) {
      return { ...payload, type: payload.type || event };
    }
  } catch {
    // 旧版纯文本 SSE 继续作为 token 处理。
  }
  return { type: event === 'message' ? 'token' : event, content: data };
}

export async function streamSse(path, { body, query, signal, onChunk, onEvent } = {}) {
  const search = new URLSearchParams();
  Object.entries(query || {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value));
    }
  });
  const queryString = search.toString();
  const token = getToken();
  const response = await fetch(`${apiUrl(path)}${queryString ? `?${queryString}` : ''}`, {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream',
      ...(body ? { 'Content-Type': 'application/json;charset=utf-8' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: body ? JSON.stringify(body) : undefined,
    signal
  });

  if (!response.ok) {
    throw new Error(await responseError(response));
  }
  if (!response.body) {
    throw new Error('浏览器未返回可读取的流式响应');
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';

  const consume = (block) => {
    if (!block.trim()) return;
    const parsed = parseSseEvent(block);
    const payload = structuredEvent(parsed.event, parsed.data);
    if (!payload) return;
    if (payload.type === 'error') {
      throw new Error(payload.content || '流式请求失败');
    }
    onEvent?.(payload);
    if (payload.type === 'token' && payload.content) onChunk?.(payload.content);
    if (payload.type === 'done' && payload.content) onChunk?.(payload.content);
  };

  try {
    while (true) {
      const { value, done } = await reader.read();
      buffer = `${buffer}${decoder.decode(value, { stream: !done })}`.replace(/\r\n/g, '\n');

      let boundary = buffer.indexOf('\n\n');
      while (boundary !== -1) {
        consume(buffer.slice(0, boundary));
        buffer = buffer.slice(boundary + 2);
        boundary = buffer.indexOf('\n\n');
      }

      if (done) break;
    }
    consume(buffer);
  } finally {
    reader.releaseLock();
  }
}

/**
 * 生成或获取会话ID
 * @returns {string} 会话ID
 */
const getSessionId = () => {
  let sessionId = localStorage.getItem('ai_chat_session_id');
  if (!sessionId) {
    // 生成新的会话ID
    sessionId = 'session_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
    localStorage.setItem('ai_chat_session_id', sessionId);
  }
  return sessionId;
};

/**
 * 调用灵犀智能助手接口
 * @param {string} message
 * @param {string} userId 用户ID
 * @param {string} userName 用户名
 * @returns {Promise<string>}
 */
export function chatWithQwen(message, userId, userName) {
  return request({
    url: '/api/ai/chat',
    method: 'post',
    data: {
      sessionId: getSessionId(),
      userId: userId,
      userName: userName,
      message
    },
    timeout: 60000, // 增加超时时间到60秒
  });
}

/**
 * 流式调用灵犀智能助手，收到文本片段时立即交给 onChunk。
 */
export function streamChatWithQwen(
  message,
  sessionId,
  userId,
  userName,
  { signal, onChunk, onEvent, attachmentIds = [] } = {}
) {
  return streamSse('/api/ai/chat/stream/v2', {
    body: { sessionId, userId, userName, message, attachmentIds },
    signal,
    onChunk,
    onEvent
  });
}

/** 上传一个绑定到当前会话的私有 AI 附件。 */
export function uploadAiAttachment(file, sessionId) {
  const data = new FormData();
  data.append('file', file);
  data.append('sessionId', sessionId);
  return request({
    url: '/api/ai/attachments',
    method: 'post',
    data,
    timeout: 60_000
  });
}

/** 删除一个尚未发送的 AI 附件。 */
export function deleteAiAttachment(attachmentId, sessionId) {
  return request({
    url: `/api/ai/attachments/${encodeURIComponent(attachmentId)}`,
    method: 'delete',
    params: { sessionId }
  });
}

/**
 * 基于数据看板分析用户问题
 * @param {string} question 用户问题
 * @param {string} start 开始时间（可选）
 * @param {string} end 结束时间（可选）
 * @param {string} userId 用户ID
 * @param {string} userName 用户名
 * @returns {Promise<string>}
 */
export function analyzeDashboard(question, start, end, userId, userName) {
  const params = { 
    question,
    sessionId: getSessionId(),
    userId: userId,
    userName: userName
  };
  if (start) params.start = start;
  if (end) params.end = end;
  return request({
    url: '/api/ai/analyze',
    method: 'post',
    params: params,
    timeout: 60000, // 增加超时时间到60秒
  }).then(res => {
    // 后端返回的是 AjaxResult { code: 200, msg: 'success', data: 'answer' }
    // 响应拦截器已经返回了 res.data，所以这里 res 就是 AjaxResult 对象
    return res.data || res.msg || '分析完成';
  });
}

/**
 * 流式分析数据看板。该后端接口使用查询参数绑定 AnalyzeVO。
 */
export function streamAnalyzeDashboard(
  question,
  start,
  end,
  sessionId,
  userId,
  userName,
  { signal, onChunk, onEvent } = {}
) {
  return streamSse('/api/ai/analyze/stream/v2', {
    query: { question, start, end, sessionId, userId, userName },
    signal,
    onChunk,
    onEvent
  });
}

/** 登录用户批准或拒绝受控动作，并继续读取同一条助手消息的 SSE。 */
export function resumeAgentAction(
  actionId,
  sessionId,
  decision,
  description,
  { signal, onChunk, onEvent } = {}
) {
  return streamSse(`/api/ai/actions/${encodeURIComponent(actionId)}/decision`, {
    body: {
      sessionId,
      decision,
      ...(decision === 'approve' ? { description } : {})
    },
    signal,
    onChunk,
    onEvent
  });
}

/** 获取当前登录用户允许查看和修改的长期回答偏好。 */
export function getLongTermMemories() {
  return request({
    url: '/api/ai/memories',
    method: 'get'
  });
}

/** 修改一项枚举化长期回答偏好。 */
export function updateLongTermPreference(preference, value) {
  return request({
    url: '/api/ai/memories',
    method: 'put',
    data: { preference, value }
  });
}

/** 清空当前登录用户的全部长期回答偏好。 */
export function clearLongTermMemories() {
  return request({
    url: '/api/ai/memories',
    method: 'delete'
  });
}

/**
 * 获取用户的对话历史记录
 * @param {string} sessionId 会话ID
 * @param {string} userId 用户ID（沿用后端契约，缺省时退回固定用户ID）
 * @returns {Promise<Array>}
 */
export function getChatHistory(sessionId, userId) {
  return request({
    url: '/api/ai/history',
    method: 'get',
    params: {
      sessionId: sessionId,
      userId: userId || '1',
      queryScope: 'current'
    }
  });
}

/**
 * 保存用户的对话历史记录
 * @param {Object} historyData 对话历史数据
 * @returns {Promise<boolean>}
 */
export function saveChatHistory(historyData) {
  return request({
    url: '/api/ai/history',
    method: 'post',
    data: historyData
  });
}

/**
 * 获取用户的会话列表
 * @param {string} userId 用户ID
 * @returns {Promise<Array>}
 */
export function getSessions(userId) {
  return request({
    url: '/api/ai/sessions',
    method: 'get',
    params: {
      userId: userId
    }
  });
}

/**
 * 创建新会话
 * @param {string} userId 用户ID
 * @returns {Promise<Object>}
 */
export function createSession(userId) {
  return request({
    url: '/api/ai/sessions',
    method: 'post',
    params: {
      userId: userId
    }
  });
}

/**
 * 更新会话名称
 * @param {Object} sessionData 会话数据，包含sessionId和sessionName
 * @returns {Promise<boolean>}
 */
export function updateSession(sessionData) {
  return request({
    url: '/api/ai/sessions',
    method: 'put',
    data: sessionData
  });
}

/**
 * 删除会话
 * @param {string} sessionId 会话ID
 * @returns {Promise<boolean>}
 */
export function deleteSessionById(sessionId) {
  return request({
    url: '/api/ai/sessions',
    method: 'delete',
    params: {
      sessionId: sessionId
    }
  });
}

/**
 * 生成智能快捷提问
 * @param {Array} chatHistory 对话历史
 * @param {string} userId 用户ID
 * @param {string} userName 用户名
 * @returns {Promise<Array>}
 */
export function generateSmartQuestions(chatHistory, userId, userName, sessionId = getSessionId()) {
  return request({
    url: '/api/ai/generate-questions',
    method: 'post',
    data: {
      chatHistory,
      sessionId,
      userId: userId,
      userName: userName
    },
    timeout: 30000
  });
}

