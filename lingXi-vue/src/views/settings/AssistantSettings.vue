<template>
  <section v-loading="loading" class="settings-section assistant-settings">
    <el-alert v-if="failed" title="无法读取回答偏好，请检查本机服务后重试。" type="error" :closable="false" show-icon />
    <el-alert v-else-if="!enabled && !loading" title="长期回答偏好尚未启用" description="管理员启用长期记忆后，可在这里管理跨对话的回答习惯。" type="info" :closable="false" show-icon />
    <el-form v-else label-position="top" :disabled="saving || loading" @submit.prevent="save">
      <p class="preference-note">这些选项会应用于后续对话。这里只保存回答习惯，不保存聊天原文。</p>
      <el-form-item v-for="field in fields" :key="field.key" :label="field.label">
        <el-select v-model="form[field.key]" placeholder="跟随模型默认" :aria-label="field.label">
          <el-option v-for="option in field.options" :key="option[0]" :label="option[1]" :value="option[0]" />
        </el-select>
      </el-form-item>
      <div class="settings-actions">
        <el-button type="primary" native-type="submit" :loading="saving">保存回答偏好</el-button>
        <el-button @click="clear">清空并恢复默认</el-button>
      </div>
    </el-form>
    <el-button v-if="failed" class="retry-button" @click="load">重新加载</el-button>
  </section>
</template>
<script setup>
import { ElMessage, ElMessageBox } from 'element-plus'
import { getLongTermMemories, updateLongTermPreference, clearLongTermMemories } from '@/api/ai'

const loading = ref(true)
const saving = ref(false)
const enabled = ref(false)
const failed = ref(false)
const form = reactive({ answer_length: '', answer_structure: '', number_format: '' })
const fields = [
  { key: 'answer_length', label: '回答篇幅', options: [['short', '简短'], ['balanced', '均衡'], ['detailed', '详细']] },
  { key: 'answer_structure', label: '回答结构', options: [['conclusion_first', '结论优先'], ['natural', '自然组织']] },
  { key: 'number_format', label: '数字格式', options: [['two_decimals', '保留两位小数'], ['adaptive', '按内容自适应']] }
]
async function load() {
  loading.value = true
  failed.value = false
  try {
    const { data } = await getLongTermMemories()
    enabled.value = Boolean(data?.enabled)
    for (const field of fields) form[field.key] = ''
    for (const item of data?.items || []) if (Object.hasOwn(form, item.preference)) form[item.preference] = item.value
  } catch { failed.value = true }
  finally { loading.value = false }
}
async function save() {
  const entries = Object.entries(form).filter(([, value]) => value)
  if (!entries.length) { ElMessage.info('请选择至少一项回答偏好'); return }
  saving.value = true
  try {
    for (const [key, value] of entries) await updateLongTermPreference(key, value)
    ElMessage.success('回答偏好已保存')
    await load()
  } finally { saving.value = false }
}
async function clear() {
  try {
    await ElMessageBox.confirm('后续对话将跟随模型默认的回答方式。', '清空回答偏好',
      { confirmButtonText: '清空偏好', cancelButtonText: '取消', type: 'warning' })
  } catch { return }
  saving.value = true
  try { await clearLongTermMemories(); await load(); ElMessage.success('回答偏好已恢复默认') }
  finally { saving.value = false }
}
load()
</script>
<style scoped>
.assistant-settings .el-form { max-width: 34rem; }.preference-note { color: var(--lx-muted); font-size: .85rem; line-height: 1.8; padding: .25rem 0 1rem; }.retry-button { margin-top: 1rem; }
</style>
