<template>
  <section v-loading="loading" class="settings-section">
    <el-alert v-if="failed" title="无法读取本机存储设置" type="error" :closable="false" show-icon>
      <el-button text @click="load">重新加载</el-button>
    </el-alert>
    <template v-else>
      <div class="storage-mode"><el-icon><FolderOpened /></el-icon><div><strong>保存在这台电脑</strong><p>上传文件、聊天附件与生成的图片、视频会保存到本机。</p></div></div>
      <el-form label-position="top" @submit.prevent="save">
        <el-form-item label="文件保存目录">
          <div class="path-editor">
            <el-input v-model="path" :disabled="loading || saving" placeholder="例如 D:\灵犀文件" aria-label="文件保存目录" />
            <el-button :disabled="loading || saving || choosing" @click="choose">选择文件夹</el-button>
          </div>
        </el-form-item>
        <p class="settings-hint">修改后立即用于新文件。旧文件保留在原目录，应用仍可读取；请保留这些目录。</p>
        <el-button type="primary" native-type="submit" :loading="saving" :disabled="loading || !path.trim() || path === savedPath">保存目录</el-button>
      </el-form>
      <div v-if="previousPaths.length" class="storage-history">
        <h3>仍在读取的旧目录</h3>
        <p v-for="old in previousPaths" :key="old" class="path-value">{{ old }}</p>
      </div>
      <div class="settings-row data-location">
        <div><h3>数据库与运行数据</h3><p>由应用管理，升级后保留。修改文件保存目录不会移动数据库。</p><p class="path-value">{{ dataDirectory }}</p></div>
      </div>
    </template>
  </section>
</template>

<script setup>
import { FolderOpened } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getDesktopSettings, updateDesktopSettings } from '@/api/settings'

const loading = ref(true)
const failed = ref(false)
const saving = ref(false)
const choosing = ref(false)
const path = ref('')
const savedPath = ref('')
const dataDirectory = ref('')
const previousPaths = ref([])
function apply(data) {
  path.value = savedPath.value = data.storagePath
  dataDirectory.value = data.dataDirectory
  previousPaths.value = data.previousPaths || []
}
async function load() {
  loading.value = true
  failed.value = false
  try { apply((await getDesktopSettings()).data) }
  catch { failed.value = true }
  finally { loading.value = false }
}
async function choose() {
  choosing.value = true
  try {
    const { invoke } = await import('@tauri-apps/api/core')
    const selected = await invoke('choose_storage_directory')
    if (selected) path.value = selected
  } catch { ElMessage.error('无法打开目录选择器，请直接填写完整路径。') }
  finally { choosing.value = false }
}
async function save() {
  saving.value = true
  try { apply((await updateDesktopSettings({ storagePath: path.value.trim() })).data); ElMessage.success('文件保存目录已更新') }
  finally { saving.value = false }
}
load()
</script>

<style scoped>
.storage-mode { display: flex; align-items: center; gap: 1rem; padding: 1.2rem 0 2rem; }
.storage-mode > .el-icon { color: var(--lx-primary); font-size: 2rem; }
.storage-mode strong { font-size: 1.1rem; }.storage-mode p { margin: .5rem 0 0; color: var(--lx-muted); line-height: 1.6; }
.path-editor { display: flex; gap: .75rem; width: 100%; }.path-editor .el-input { flex: 1; min-width: 0; }
.storage-history { margin-top: 2rem; padding: 1rem 1.25rem; background: var(--lx-canvas); border-radius: .5rem; }
.data-location { margin-top: 2rem; }
@media(max-width: 640px) { .path-editor { flex-direction: column; } }
</style>
