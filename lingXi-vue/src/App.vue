<template>
  <router-view />
</template>

<script setup>
import { check } from '@tauri-apps/plugin-updater'
import { ElMessage, ElMessageBox } from 'element-plus'
import useSettingsStore from '@/store/modules/settings'
import { handleThemeStyle } from '@/utils/theme'

async function checkForDesktopUpdate() {
  // 浏览器版不加载桌面更新逻辑。
  if (!window.__TAURI_INTERNALS__) return

  try {
    const update = await check()
    if (!update) return

    await ElMessageBox.confirm(
      `发现新版本 ${update.version}，是否立即下载？`,
      '发现应用更新',
      { confirmButtonText: '下载更新', cancelButtonText: '稍后', type: 'info' }
    )
    await update.downloadAndInstall()
    ElMessage.success('更新已下载完成，请关闭并重新打开应用以使用新版本。')
  } catch (error) {
    // 网络不可用或尚未发布 Release 时，保持当前应用可正常使用。
    if (error !== 'cancel' && error !== 'close') console.warn('检查桌面更新失败', error)
  }
}

onMounted(async () => {
  nextTick(() => {
    // 初始化主题样式
    handleThemeStyle(useSettingsStore().theme)
  })
  await checkForDesktopUpdate()
})
</script>
