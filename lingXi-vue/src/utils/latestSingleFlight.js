export function createLatestSingleFlight() {
  let generation = 0
  let activeKey = ''
  let activePromise = null
  let resolvedKey = ''

  const run = (key, task) => {
    if (!key) return Promise.resolve({ status: 'skipped' })
    if (key === resolvedKey) return Promise.resolve({ status: 'cached' })
    if (activePromise && key === activeKey) return activePromise

    const requestGeneration = ++generation
    let requestPromise
    requestPromise = Promise.resolve()
      .then(task)
      .then(value => {
        if (requestGeneration !== generation) {
          return { status: 'stale', value }
        }
        resolvedKey = key
        return { status: 'applied', value }
      })
      .finally(() => {
        if (activePromise === requestPromise) {
          activeKey = ''
          activePromise = null
        }
      })

    activeKey = key
    activePromise = requestPromise
    return requestPromise
  }

  const invalidate = () => {
    generation += 1
    activeKey = ''
    activePromise = null
    resolvedKey = ''
  }

  return { run, invalidate }
}

export function smartQuestionRequestKey(sessionId, chatHistory) {
  if (!sessionId || !Array.isArray(chatHistory) || chatHistory.length === 0) return ''
  return JSON.stringify([
    sessionId,
    chatHistory.map(item => [
      item.role || item.messageType || (item.isUser ? 'user' : 'assistant'),
      String(item.content || '')
    ])
  ])
}
