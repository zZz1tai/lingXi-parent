import { resumeAgentAction, streamAnalyzeDashboard, streamChatWithQwen } from '@/api/ai'
import { applyStreamEvent, createStreamDraft } from './agentStreamDraft'

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

this.draftsBySession[sessionId] = createStreamDraft({
        message,
        attachments,
        userMessageId,
        assistantMessageId
      })

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
