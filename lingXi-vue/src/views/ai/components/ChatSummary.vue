<template>
  <el-button
    class="summary-trigger"
    :icon="Document"
    :disabled="disabled || !sessionId"
    @click="openSummary"
  >对话纪要</el-button>
  <el-dialog
    v-model="visible"
    title="AI 对话纪要"
    width="min(720px, 94vw)"
    append-to-body
    destroy-on-close
    @close="invalidate"
  >
    <div class="summary-content" aria-live="polite" :aria-busy="busy">
      <p class="summary-session">{{ sessionName }}</p>
      <p v-if="busy" role="status">正在整理会话中的摘要、结论与待办，请稍候…</p>
      <el-alert v-else-if="error" :title="error" type="error" :closable="false" show-icon />
      <template v-else-if="result">
        <el-alert
          :title="`基于 ${result.message_count} 条消息${result.truncated ? '中的最近部分内容（已截断）' : ''}；AI 整理结果请核对，待办尚未执行。`"
          :type="result.truncated ? 'warning' : 'info'"
          :closable="false"
          show-icon
        />
        <h3>{{ result.title }}</h3>
        <p class="summary-text">{{ result.summary }}</p>
        <h4>关键结论</h4>
        <ul v-if="result.decisions.length">
          <li v-for="(item, index) in result.decisions" :key="index">{{ item }}</li>
        </ul>
        <p v-else class="summary-empty">暂无明确结论。</p>
        <h4>待办事项</h4>
        <ol v-if="result.actions.length">
          <li v-for="(item, index) in result.actions" :key="index">
            <span>{{ item.task }}</span>
            <p class="summary-meta">负责人：{{ item.owner || '未明确' }} · 期限：{{ item.due_date || '未明确' }}</p>
          </li>
        </ol>
        <p v-else class="summary-empty">暂无明确待办。</p>
        <h4>待确认事项</h4>
        <ul v-if="result.open_questions.length">
          <li v-for="(item, index) in result.open_questions" :key="index">{{ item }}</li>
        </ul>
        <p v-else class="summary-empty">暂无待确认事项。</p>
      </template>
    </div>
    <template #footer>
      <div class="summary-actions">
        <el-button @click="visible = false">关闭</el-button>
        <el-button :disabled="busy || disabled" @click="generate">{{ error ? '重试' : '重新生成' }}</el-button>
        <el-button :disabled="!result || busy" @click="copySummary">复制 Markdown</el-button>
        <el-button type="primary" :icon="Download" :disabled="!result || busy" @click="downloadSummary">下载纪要</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { ElAlert, ElButton, ElDialog, ElMessage } from 'element-plus'
import { Document, Download } from '@element-plus/icons-vue'
import { saveAs } from 'file-saver'
import { generateChatSummary } from '@/api/ai'
import { chatSummaryToMarkdown } from '@/utils/chatSummary'
import { createLatestSingleFlight } from '@/utils/latestSingleFlight'

const props = defineProps({
  sessionId: { type: String, default: '' },
  sessionName: { type: String, default: '' },
  sourceKey: { type: String, default: '' },
  disabled: Boolean
})
const visible = ref(false)
const busy = ref(false)
const error = ref('')
const result = ref(null)
const loader = createLatestSingleFlight()
let generation = 0

function invalidate() {
  generation += 1
  loader.invalidate()
  busy.value = false
  result.value = null
  error.value = ''
}

async function generate() {
  if (busy.value || props.disabled || !props.sessionId) return
  invalidate()
  const requestGeneration = generation
  const sessionId = props.sessionId
  busy.value = true
  try {
    const response = await loader.run(sessionId, () => generateChatSummary(sessionId))
    if (response.status === 'applied') result.value = response.value.data
  } catch (failure) {
    if (requestGeneration === generation) {
      error.value = failure?.message || '纪要生成失败，请稍后重试。'
    }
  } finally {
    if (requestGeneration === generation) busy.value = false
  }
}

function openSummary() {
  visible.value = true
  void generate()
}

async function copySummary() {
  try {
    await navigator.clipboard.writeText(chatSummaryToMarkdown(result.value))
    ElMessage.success('纪要已复制')
  } catch {
    ElMessage.warning('无法访问剪贴板，请使用“下载纪要”保存。')
  }
}

function downloadSummary() {
  saveAs(new Blob([chatSummaryToMarkdown(result.value)], { type: 'text/markdown;charset=utf-8' }), '对话纪要.md')
}

// 切换会话、增加消息或更新回答后，旧请求和旧纪要都不能覆盖当前内容。
watch(() => [props.sessionId, props.sourceKey], () => {
  visible.value = false
  invalidate()
}, { flush: 'sync' })
onBeforeUnmount(invalidate)
</script>

<style scoped>
.summary-trigger { margin-left: auto; flex-shrink: 0; }
.summary-content { max-height: 62vh; overflow-y: auto; overflow-wrap: anywhere; line-height: 1.7; }
.summary-session, .summary-meta, .summary-empty { color: var(--el-text-color-secondary); }
.summary-session { margin-top: 0; }
.summary-text { white-space: pre-wrap; }
.summary-content h3 { margin: 20px 0 8px; color: var(--el-text-color-primary); }
.summary-content h4 { margin: 20px 0 8px; }
.summary-content ul, .summary-content ol { padding-left: 24px; }
.summary-content li { margin-bottom: 8px; white-space: pre-wrap; }
.summary-meta { margin: 2px 0 0; font-size: 12px; }
.summary-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }
.summary-actions .el-button { margin: 0; }
</style>
