/** 更新检查与安装串行化，下载及验签成功后才关闭本机服务。 */
export function createDesktopUpdater({ check, stopServices, onChange = () => {} }) {
  const state = { phase: 'idle', version: '', notes: '', checkedAt: '', progress: 0, error: '' }
  let update
  let pending
  const publish = changes => { Object.assign(state, changes); onChange({ ...state }) }

  async function checkForUpdates() {
    if (pending) return pending
    pending = (async () => {
      publish({ phase: 'checking', error: '' })
      try {
        const next = await check({ timeout: 15000 })
        await update?.close().catch(() => {})
        update = next
        publish({ phase: next ? 'available' : 'current', version: next?.version || '',
          notes: next?.body || '', checkedAt: new Date().toISOString(), progress: 0 })
        return Boolean(next)
      } catch (error) {
        publish({ phase: 'error', error: '检查失败，请检查网络连接后重试。当前版本仍可继续使用。' })
        return false
      } finally { pending = undefined }
    })()
    return pending
  }

  async function installUpdate() {
    if (pending || !update) return
    pending = (async () => {
      let downloaded = 0
      let total = 0
      publish({ phase: 'downloading', progress: 0, error: '' })
      try {
        await update.download(event => {
          if (event.event === 'Started') total = event.data.contentLength || 0
          if (event.event === 'Progress') downloaded += event.data.chunkLength
          publish({ progress: total ? Math.min(100, Math.round(downloaded / total * 100)) : 0 })
        })
        publish({ phase: 'installing', progress: 100 })
        await stopServices()
        await update.install()
      } catch (error) {
        const stopped = state.phase === 'installing'
        publish({ phase: stopped ? 'restart-required' : 'error', error: stopped
          ? '安装未完成，请关闭应用后重新打开。本机数据会保留。'
          : '下载或签名校验失败，请重试。本机服务仍在运行。' })
      } finally { pending = undefined }
    })()
    return pending
  }

  return { state, checkForUpdates, installUpdate }
}
