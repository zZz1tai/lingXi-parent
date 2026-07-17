import request from '@/utils/request';

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
 * 获取用户的对话历史记录
 * @param {string} sessionId 会话ID
 * @returns {Promise<Array>}
 */
export function getChatHistory(sessionId) {
  return request({
    url: '/api/ai/history',
    method: 'get',
    params: {
      sessionId: sessionId,
      userId: '1', // 临时使用固定用户ID，后续应从登录状态获取
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
 * @param {Object} sessionData 会话数据，包含id和sessionName
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
export function generateSmartQuestions(chatHistory, userId, userName) {
  return request({
    url: '/api/ai/generate-questions',
    method: 'post',
    data: {
      chatHistory,
      sessionId: getSessionId(),
      userId: userId,
      userName: userName
    },
    timeout: 30000
  });
}

