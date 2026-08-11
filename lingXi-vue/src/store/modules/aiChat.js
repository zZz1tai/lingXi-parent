import { resumeAgentAction, streamAnalyzeDashboard, streamChatWithQwen } from '@/api/ai'

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

function applyStreamEvent(draft, event) {
  if (!draft || !event) return

  if (event.type === 'token' && event.content) {
    draft.assistantContent += event.content
    return
  }
  if (event.type === 'done' && event.content && !draft.assistantContent) {
    draft.assistantContent = event.content
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
  if (event.type === 'citation' && event.data?.source_id) {
    const exists = draft.citations.some(item => item.source_id === event.data.source_id)
    if (!exists) draft.citations.push(event.data)
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

const useAiChatStore = defineStore('ai-chat', {
  state: () => ({
    draftsBySession: {}
  }),
  getters: {
    draftFor: state => sessionId => state.draftsBySession[sessionId] || null
  },
  actions: {
    async streamMessage({
      sessionId,
      message,
      userId,
      userName,
      dataAnalysis = false,
      attachments = [],
      userMessageId,
      assistantMessageId
    }) {
      const activeDraft = this.draftsBySession[sessionId]
      if (activeDraft?.status === 'streaming') {
        throw new Error('当前会话正在生成回答')
      }

      this.draftsBySession[sessionId] = {
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
        pendingAction: null
      }

      const onEvent = event => {
        const draft = this.draftsBySession[sessionId]
        if (draft?.status !== 'streaming' || !event) return
        applyStreamEvent(draft, event)
      }

      try {
        if (dataAnalysis) {
          await streamAnalyzeDashboard(
            message,
            null,
            null,
            sessionId,
            userId,
            userName,
            { onEvent }
          )
        } else {
          await streamChatWithQwen(
            message,
            sessionId,
            userId,
            userName,
            {
              onEvent,
              attachmentIds: attachments.map(item => item.attachmentId)
            }
          )
        }

        const draft = this.draftsBySession[sessionId]
        if (draft?.pendingAction?.decision === 'pending') {
          draft.status = 'awaiting_approval'
          return draft.assistantContent
        }
        if (!draft?.assistantContent.trim()) {
          throw new Error('智能体未返回有效内容')
        }
        draft.status = 'completed'
        return draft.assistantContent
      } catch (error) {
        const draft = this.draftsBySession[sessionId]
        if (draft) {
          draft.status = 'error'
          draft.error = error?.message || '流式请求失败'
        }
        throw error
      }
    },
    async decideAction({ sessionId, actionId, decision, description }) {
      const draft = this.draftsBySession[sessionId]
      if (!draft || draft.status !== 'awaiting_approval') {
        throw new Error('当前没有等待确认的受控操作')
      }
      if (!draft.pendingAction || draft.pendingAction.action_id !== actionId) {
        throw new Error('待确认操作与当前会话不匹配')
      }
      if (draft.pendingAction.submitting || draft.pendingAction.decision !== 'pending') {
        throw new Error('该操作已经处理或正在处理')
      }

      draft.pendingAction.submitting = true
      draft.pendingAction.submittingDecision = decision
      draft.pendingAction.error = ''
      draft.status = 'resuming'
      const onEvent = event => {
        const current = this.draftsBySession[sessionId]
        if (current?.status !== 'resuming') return
        applyStreamEvent(current, event)
      }
      try {
        await resumeAgentAction(actionId, sessionId, decision, description, { onEvent })
        const current = this.draftsBySession[sessionId]
        if (!current?.pendingAction) throw new Error('受控操作结果缺失')
        if (decision === 'approve' && current.pendingAction.decision !== 'approved') {
          throw new Error('维修工单未返回已创建状态')
        }
        if (decision === 'reject' && current.pendingAction.decision !== 'rejected') {
          throw new Error('拒绝结果未完成确认')
        }
        current.status = 'completed'
        return current.assistantContent
      } catch (error) {
        const current = this.draftsBySession[sessionId]
        if (current?.pendingAction) {
          current.pendingAction.submitting = false
          current.pendingAction.submittingDecision = ''
          current.pendingAction.error = error?.message || '审批续流失败'
          if (String(current.pendingAction.error).includes('已过期')) {
            current.pendingAction.decision = 'failed'
            current.status = 'completed'
          } else {
            current.status = 'awaiting_approval'
          }
        }
        throw error
      }
    },
    clearDraft(sessionId) {
      delete this.draftsBySession[sessionId]
    }
  }
})

export default useAiChatStore
