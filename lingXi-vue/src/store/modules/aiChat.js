import { streamAnalyzeDashboard, streamChatWithQwen } from '@/api/ai'

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
        assistantContent: '',
        userMessageId,
        assistantMessageId,
        error: ''
      }

      const onChunk = chunk => {
        const draft = this.draftsBySession[sessionId]
        if (draft?.status === 'streaming') {
          draft.assistantContent += chunk
        }
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
            { onChunk }
          )
        } else {
          await streamChatWithQwen(
            message,
            sessionId,
            userId,
            userName,
            { onChunk }
          )
        }

        const draft = this.draftsBySession[sessionId]
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
    clearDraft(sessionId) {
      delete this.draftsBySession[sessionId]
    }
  }
})

export default useAiChatStore
