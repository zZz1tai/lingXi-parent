<template>
  <main v-if="desktop && runtime.phase !== 'ready'" class="desktop-startup" aria-live="polite">
    <h1>灵犀智能应用平台</h1>
    <p>{{ runtime.message }}</p>
    <p v-if="runtime.phase === 'error'">请关闭应用后重试。本机数据会保留。</p>
    <p v-else>首次运行会自动准备数据库，无需手动部署。</p>
  </main>
  <RouterView v-else />
</template>

<script setup>
import { check } from '@tauri-apps/plugin-updater'
import { RouterView } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import useSettingsStore from '@/store/modules/settings'
import { handleThemeStyle } from '@/utils/theme'

const desktop = Boolean(window.__TAURI_INTERNALS__)
const emit = defineEmits(['desktop-ready'])
const runtime = ref({ phase: 'starting', message: '正在准备本机服务…' })
let polling
let updating = false

async function checkForDesktopUpdate() {
  // 浏览器版不加载桌面更新逻辑。
  if (!desktop || updating || runtime.value.phase !== 'ready') return

  updating = true
  let update
  try {
    update = await check({ timeout: 15000 })
    if (!update) return

    await ElMessageBox.confirm(
      `发现新版本 ${update.version}。下载后将关闭本机服务并安装更新，数据库和上传文件会保留。请先保存当前工作。`,
      '发现应用更新',
      { confirmButtonText: '下载更新', cancelButtonText: '稍后', type: 'info' }
    )
    const notice = ElMessage({ message: '正在下载完整桌面更新，请稍候…', duration: 0 })
    try {
      // 下载和验签成功后才停服务，下载失败不影响正在进行的工作。
      await update.download()
      const { invoke } = await import('@tauri-apps/api/core')
      runtime.value = { phase: 'updating', message: '正在安装更新，完成后将重新打开…' }
      await invoke('stop_desktop_services')
      await update.install()
    } finally {
      notice.close()
    }
  } catch (error) {
    // 网络不可用或尚未发布 Release 时，保持当前应用可正常使用。
    if (error !== 'cancel' && error !== 'close') {
      console.warn('检查桌面更新失败', error)
      if (runtime.value.phase === 'updating') {
        runtime.value = { phase: 'error', message: '更新安装失败，请关闭应用后重新打开。' }
      }
    }
  } finally {
    updating = false
    await update?.close().catch(() => {})
  }
}

async function pollDesktopStatus() {
  if (updating && runtime.value.phase === 'updating') return
  try {
    const { invoke } = await import('@tauri-apps/api/core')
    const previous = runtime.value.phase
    const status = await invoke('desktop_status')
    if (status.phase === 'ready' && previous !== 'ready') emit('desktop-ready')
    runtime.value = status
    if (runtime.value.phase === 'ready' && previous !== 'ready') checkForDesktopUpdate()
  } catch {
    runtime.value = { phase: 'error', message: '无法读取本机服务状态，请重新打开应用。' }
  }
}

onMounted(async () => {
  nextTick(() => {
    // 初始化主题样式
    handleThemeStyle(useSettingsStore().theme)
  })
  if (desktop) {
    await pollDesktopStatus()
    polling = window.setInterval(pollDesktopStatus, 2000)
    window.addEventListener('online', checkForDesktopUpdate)
  }
})

// 长时间保持打开的客户端也会检查更新，离线时仍可继续使用本机功能。
const updateTimer = desktop ? window.setInterval(checkForDesktopUpdate, 6 * 60 * 60 * 1000) : undefined
onUnmounted(() => {
  window.clearInterval(polling)
  window.clearInterval(updateTimer)
  window.removeEventListener('online', checkForDesktopUpdate)
})
</script>

<style scoped>
.desktop-startup {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 40px;
  color: var(--el-text-color-primary);
  background: var(--el-bg-color);
  text-align: center;
}
.desktop-startup h1 { font-size: 24px; }
.desktop-startup p { max-width: 720px; overflow-wrap: anywhere; }
</style>
