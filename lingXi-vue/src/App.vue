<template>
  <main v-if="desktop && runtime.phase !== 'ready'" class="desktop-startup" aria-live="polite">
    <h1>灵犀智能应用平台</h1>
    <p>{{ runtime.message }}</p>
    <p v-if="['error', 'restart-required'].includes(runtime.phase)">请关闭应用后重试。本机数据会保留。</p>
    <p v-else>首次运行会自动准备数据库，无需手动部署。</p>
  </main>
  <RouterView v-else />
</template>

<script setup>
import { RouterView } from 'vue-router'
import { ElMessage } from 'element-plus'
import { desktopUpdate, desktopPreferences, checkDesktopUpdate } from '@/store/modules/desktop'
import useSettingsStore from '@/store/modules/settings'
import { handleThemeStyle } from '@/utils/theme'

const desktop = Boolean(window.__TAURI_INTERNALS__)
const emit = defineEmits(['desktop-ready'])
const runtime = ref({ phase: 'starting', message: '正在准备本机服务…' })
let polling
async function checkForDesktopUpdate() {
  if (!desktop || !desktopPreferences.autoCheck || runtime.value.phase !== 'ready') return
  if (await checkDesktopUpdate()) ElMessage.info('发现新版本，可在设置中心的“版本更新”中查看。')
}

async function pollDesktopStatus() {
  if (['installing', 'restart-required'].includes(desktopUpdate.phase)) {
    runtime.value = { phase: desktopUpdate.phase, message: desktopUpdate.error || '正在安装更新，完成后请重新打开应用…' }
    return
  }
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
