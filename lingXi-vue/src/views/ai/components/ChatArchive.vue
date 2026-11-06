<template>
  <el-button :icon="Download" :disabled="disabled || !messages.length" @click="open">导出对话</el-button>
  <el-dialog v-model="visible" title="导出 AI 对话" width="min(720px, 94vw)" append-to-body destroy-on-close>
    <el-checkbox v-model="assistantOnly">仅导出助手回答</el-checkbox>
    <p role="status">共 {{ archive.count }} 条消息。包含引用证据；附件仅列名称，不含附件内容和可视化卡片。</p>
    <p class="archive-hint">以下为 Markdown 原文预览，消息正文使用文本代码块保留格式。</p>
    <pre class="archive-preview" aria-label="对话归档预览">{{ archive.markdown }}</pre>
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button type="primary" :disabled="disabled || !archive.count" @click="download">下载 Markdown</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { ElButton, ElCheckbox, ElDialog } from 'element-plus';
import { Download } from '@element-plus/icons-vue';
import { saveAs } from 'file-saver';
import { createChatArchive } from '@/utils/chatArchive';

const props = defineProps({ messages: { type: Array, default: () => [] }, sessionName: String, disabled: Boolean });
const visible = ref(false);
const assistantOnly = ref(false);
const exportedAt = ref('');
const archive = computed(() => visible.value
  ? createChatArchive(props.messages, { title: props.sessionName, assistantOnly: assistantOnly.value, exportedAt: exportedAt.value })
  : { count: 0, markdown: '', filename: '' });
watch(() => props.disabled, disabled => { if (disabled) visible.value = false; });
function open() {
  exportedAt.value = new Date().toLocaleString('zh-CN');
  visible.value = true;
}
function download() {
  if (props.disabled || !archive.value.count) return;
  saveAs(new Blob([archive.value.markdown], { type: 'text/markdown;charset=utf-8' }), archive.value.filename);
}
</script>

<style scoped>
.archive-hint { color: var(--el-text-color-secondary); font-size: 12px; }
.archive-preview { max-height: 48vh; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; padding: 16px; border-radius: 8px; background: var(--el-fill-color-light); line-height: 1.6; font-size: 12px; }
</style>
