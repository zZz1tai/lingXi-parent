<template>
  <section class="settings-section" aria-live="polite">
    <div class="version-overview">
      <span class="version-mark" aria-hidden="true">灵犀</span>
      <div><h3>灵犀智能应用平台</h3><p>当前版本 <strong>{{ currentVersion }}</strong></p></div>
    </div>
    <div class="update-status">
      <h3>{{ statusTitle }}</h3>
      <p v-if="status.error" class="update-error" role="alert">{{ status.error }}</p>
      <p v-else-if="status.phase === 'available'">下载更新后安装，应用会先关闭本机服务。文件和数据库会保留。</p>
      <p v-else-if="status.phase === 'current'">本次检查未发现更新。</p>
      <p v-else>更新来自项目的 GitHub Releases，安装前会自动校验签名。</p>
      <p v-if="status.checkedAt" class="checked-at">上次检查 {{ new Date(status.checkedAt).toLocaleString() }}</p>
      <el-progress v-if="status.phase === 'downloading'" :percentage="status.progress" :indeterminate="!status.progress" />
      <div class="settings-actions">
        <el-button type="primary" :loading="status.phase === 'checking'" :disabled="busy" @click="checkDesktopUpdate">检查更新</el-button>
        <el-button v-if="status.phase === 'available'" @click="install">下载并安装 {{ status.version }}</el-button>
      </div>
    </div>
    <div class="settings-row">
      <div><h3>自动检查更新</h3><p>启动应用、恢复联网及使用过程中定期检查。有更新时提示，由你决定安装。</p></div>
      <el-switch :model-value="preferences.autoCheck" aria-label="自动检查更新" @change="setAutoCheck" />
    </div>
    <section v-if="status.notes" class="release-notes"><h3>版本说明</h3><p>{{ status.notes }}</p></section>
  </section>
</template>

<script setup>
import { ElMessageBox } from 'element-plus'
import { desktopUpdate as status, desktopPreferences as preferences, checkDesktopUpdate, installDesktopUpdate, setAutoCheck } from '@/store/modules/desktop'
import packageInfo from '../../../package.json'

const currentVersion = ref(packageInfo.version)
const busy = computed(() => ['checking', 'downloading', 'installing', 'restart-required'].includes(status.phase))
const statusTitle = computed(() => ({ idle: '检查是否有新版本', checking: '正在检查更新…', available: `发现新版本 ${status.version}`,
  current: '已是最新版本', downloading: '正在下载更新…', installing: '正在安装更新…', error: '暂时无法完成更新',
  'restart-required': '请重新打开应用' }[status.phase]))
async function install() {
  try {
    await ElMessageBox.confirm('安装时将关闭本机服务，请先保存当前工作。文件与数据库会保留。', '安装应用更新',
      { confirmButtonText: '下载并安装', cancelButtonText: '稍后' })
  } catch { return }
  await installDesktopUpdate()
}
onMounted(async () => {
  try { currentVersion.value = await (await import('@tauri-apps/api/app')).getVersion() }
  catch { /* 开发环境展示源码版本。 */ }
})
</script>

<style scoped>
.version-overview { display: flex; align-items: center; gap: 1.25rem; padding: 1rem 0 2rem; }
.version-mark { display: grid; place-items: center; width: 4rem; height: 4rem; border-radius: 1rem; background: var(--lx-primary); color: white; font-size: 1.1rem; font-weight: 650; }
.version-overview h3 { margin: 0; font-size: 1.25rem; }.version-overview p { color: var(--lx-muted); margin: .65rem 0 0; }
.update-status { padding: 1.5rem; border: 1px solid var(--lx-border); border-radius: .75rem; margin-bottom: 1.5rem; }
.update-status p { color: var(--lx-muted); line-height: 1.7; }.update-status .update-error { color: var(--lx-danger); }
.checked-at { font-size: .8rem; font-variant-numeric: tabular-nums; }.release-notes { margin-top: 2rem; }.release-notes p { white-space: pre-wrap; line-height: 1.8; overflow-wrap: anywhere; color: var(--lx-muted); }
</style>
