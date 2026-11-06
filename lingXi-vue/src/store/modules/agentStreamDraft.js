// 智能体 SSE 事件 → 会话草稿状态机的纯函数实现。
// 不依赖 pinia / vue / 后端 API，可在 node:test 与浏览器两端直接运行。

const toolLabels = {
  get_current_datetime: '查询当前时间',
  calculate: '进行精确计算',
  convert_units: '换算单位',
  date_calculator: '计算日期',
  get_weather: '查询天气',
  search_knowledge: '检索内部知识',
  web_search: '搜索公开信息',
  query_sales_summary: '查询销售汇总',
  query_task_statistics: '查询工单统计',
  query_abnormal_devices: '查询异常设备',
  lookup_device: '查询设备状态',
  generate_image: '生成图片',
  propose_maintenance_task: '准备维修工单提案',
  execute_maintenance_task: '创建维修工单'
}

function mapToolStepStatus(status, isEnd) {
  const s = String(status || '');
  if (s === 'failed' || s === 'error' || s === 'cancelled') return 'error';
  if (isEnd || s === 'completed' || s === 'success' || s === 'succeeded') return 'completed';
  return 'running';
}

function findToolStepByCallId(draft, callId) {
  return draft.activities.find(item => item.callId === callId);
}

function findToolStepByTool(draft, tool) {
  const steps = [...draft.activities].reverse();
  return (
    steps.find(item => item.tool === tool && item.status !== 'completed')
    || steps.find(item => item.tool === tool)
  );
}

function pushToolStep(draft, event) {
  let callId = String(event.call_id || '');
  if (!callId) {
    draft.fallbackCallCount += 1;
    callId = `local-${draft.fallbackCallCount}-${event.tool || 'tool'}`;
  }
  const existing = findToolStepByCallId(draft, callId);
  if (existing) return existing;
  const step = {
    callId,
    sequence: Number.isInteger(event.sequence) && event.sequence > 0
      ? event.sequence
      : draft.activities.length + 1,
    tool: event.tool || 'unknown',
    label: toolLabels[event.tool] || '使用智能工具',
    status: 'running',
    inputSummary: typeof event.input_summary === 'string' ? event.input_summary : '',
    resultCount: null,
    startedAt: Date.now(),
    endedAt: null,
    elapsedMs: null,
    errorCode: '',
    retryable: false
  };
  draft.activities.push(step);
  draft.activities.sort((a, b) => (a.sequence || 0) - (b.sequence || 0));
  return step;
}

function updateToolStep(draft, event, isEnd) {
  const callId = String(event.call_id || '');
  let step = callId ? findToolStepByCallId(draft, callId) : undefined;
  if (!step) step = findToolStepByTool(draft, event.tool);
  if (!step) {
    if (!isEnd) return undefined;
    step = pushToolStep(draft, event);
  }
  step.status = mapToolStepStatus(event.data?.status, isEnd);
  if (Number.isInteger(event.data?.result_count) && event.data.result_count >= 0) {
    step.resultCount = event.data.result_count;
  }
  if (isEnd) {
    step.endedAt = Date.now();
    if (Number.isInteger(event.elapsed_ms) && event.elapsed_ms >= 0) {
      step.elapsedMs = event.elapsed_ms;
    }
    const errorCode = String(event.data?.error_code || '');
    if (errorCode) step.errorCode = errorCode;
  }
  return step;
}

function findLastOpenUiRender(draft, renderId) {
  const key = String(renderId || '');
  return [...draft.uiRenders].reverse().find(item => item.renderId === key);
}

// 流式和历史回放共用同一份白名单；引用只展示文本，不能携带内部存储地址。
function normalizeCitation(raw) {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return null
  const sourceId = typeof raw.source_id === 'string' ? raw.source_id : ''
  if (!sourceId.trim() || Array.from(sourceId).length > 512) return null
  const citation = { source_id: sourceId }
  for (const [field, limit] of Object.entries({
    title: 300, section: 300, version: 128, document_type: 128, excerpt: 2000
  })) {
    if (typeof raw[field] === 'string') {
      citation[field] = Array.from(raw[field]).slice(0, limit).join('')
    }
  }
  citation.excerpt_truncated = raw.excerpt_truncated === true
    || (typeof raw.excerpt === 'string' && Array.from(raw.excerpt).length > 2000)
  if (typeof raw.score === 'number' && Number.isFinite(raw.score)) citation.score = raw.score
  return citation
}

function appendCitation(citations, raw) {
  if (citations.length >= 20) return
  const citation = normalizeCitation(raw)
  if (citation && !citations.some(item => item.source_id === citation.source_id)) {
    citations.push(citation)
  }
}

/** 新建会话草稿（与 aiChat store 初始化结构一致）。 */
export function createStreamDraft({
  message,
  attachments = [],
  userMessageId = '',
  assistantMessageId = ''
}) {
  return {
    status: 'streaming',
    userContent: message,
    attachments,
    assistantContent: '',
    userMessageId,
    assistantMessageId,
    error: '',
    activities: [],
    fallbackCallCount: 0,
    citations: [],
    memorySaved: [],
    clarification: '',
    pendingAction: null,
    uiRenders: []
  }
}

/** 把单个 SSE 事件应用到会话草稿上（原地修改）。 */
export function applyStreamEvent(draft, event) {
  if (!draft || !event) return

  if (event.type === 'token' && event.content) {
    draft.assistantContent += event.content
    return
  }
  if (event.type === 'done' && event.content && !draft.assistantContent) {
    draft.assistantContent = event.content
    return
  }
  if (event.type === 'ui_start') {
    draft.uiRenders.push({
      renderId: String(event.render_id || `ui-${draft.uiRenders.length + 1}`),
      schemaVersion: Number.isInteger(event.schema_version) ? event.schema_version : null,
      status: 'streaming',
      errorCode: '',
      sections: []
    })
    return
  }
  if (event.type === 'ui_delta' && Array.isArray(event.delta)) {
    const render = findLastOpenUiRender(draft, event.render_id)
    if (render && render.status === 'streaming') {
      render.sections.push(...event.delta)
    }
    return
  }
  if (event.type === 'ui_complete' && Array.isArray(event.spec)) {
    const render = findLastOpenUiRender(draft, event.render_id)
    if (render) {
      render.sections = event.spec
      if (Number.isInteger(event.schema_version)) {
        render.schemaVersion = event.schema_version
      }
      render.status = 'complete'
    }
    return
  }
  if (event.type === 'ui_error') {
    const render = findLastOpenUiRender(draft, event.render_id)
    if (render) {
      render.status = 'error'
      render.errorCode = String(event.code || '')
    } else {
      draft.uiRenders.push({
        renderId: String(event.render_id || `ui-${draft.uiRenders.length + 1}`),
        schemaVersion: null,
        status: 'error',
        errorCode: String(event.code || ''),
        sections: []
      })
    }
    return
  }
  if (event.type === 'tool_start') {
    const step = pushToolStep(draft, event)
    if (typeof event.input_summary === 'string') {
      step.inputSummary = event.input_summary
    }
    return
  }
  if (event.type === 'tool_progress' || event.type === 'tool_end') {
    updateToolStep(draft, event, event.type === 'tool_end')
    return
  }
  if (event.type === 'citation') {
    appendCitation(draft.citations, event.data)
    return
  }
  if (event.type === 'memory_saved' && event.data?.preference) {
    const existing = draft.memorySaved.find(
      item => item.preference === event.data.preference
    )
    if (existing) Object.assign(existing, event.data)
    else draft.memorySaved.push(event.data)
    return
  }
  if (event.type === 'clarification' && event.content) {
    draft.clarification = event.content
    return
  }
  if (event.type === 'approval_required' && event.data?.action_id) {
    draft.pendingAction = {
      ...event.data,
      decision: 'pending',
      submitting: false,
      submittingDecision: '',
      error: ''
    }
    return
  }
  if (
    (event.type === 'action_completed' || event.type === 'action_rejected')
    && event.data?.action_id
  ) {
    const current = draft.pendingAction || {}
    draft.pendingAction = {
      ...current,
      ...event.data,
      decision: event.type === 'action_completed' ? 'approved' : 'rejected',
      submitting: false,
      submittingDecision: '',
      error: ''
    }
  }
}

/** 从历史 JSON（ui_json）还原已完成的 OpenUI 渲染，供会话历史回放。 */
export function restoreUiRendersFromHistory(uiJson) {
  if (!uiJson || typeof uiJson !== 'string') return []
  let parsed
  try {
    parsed = JSON.parse(uiJson)
  } catch {
    return []
  }
  const renders = Array.isArray(parsed?.renders) ? parsed.renders : []
  return renders
    .filter(item => item && typeof item === 'object')
    .map((item, index) => ({
      renderId: String(item.render_id || `ui-${index + 1}`),
      schemaVersion: Number.isInteger(item.schema_version) ? item.schema_version : null,
      status: 'complete',
      errorCode: '',
      sections: Array.isArray(item.spec) ? item.spec : []
    }))
}

/** 从已保存的回答中恢复检索证据快照，兼容不含引用的旧版历史。 */
export function restoreCitationsFromHistory(uiJson) {
  if (!uiJson || typeof uiJson !== 'string') return []
  try {
    const parsed = JSON.parse(uiJson)
    if (!Array.isArray(parsed?.citations)) return []
    const citations = []
    for (const raw of parsed.citations) {
      appendCitation(citations, raw)
      if (citations.length === 20) break
    }
    return citations
  } catch {
    return []
  }
}
