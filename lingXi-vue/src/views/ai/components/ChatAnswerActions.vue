<template>
  <span v-if="message.content?.trim()" class="answer-actions">
    <el-button text size="small" :disabled="disabled || copying" title="只复制正文，保留 Markdown 原文" @click="copy">复制回答</el-button>
    <el-button text size="small" :disabled="disabled" title="保存正文与引用证据为文本文件，不含附件和可视化卡片" @click="download">下载回答</el-button>
  </span>
</template>

<script setup>
import { ref } from 'vue';
import { ElButton, ElMessage } from 'element-plus';
import { saveAs } from 'file-saver';
import { answerDownloadText } from '@/utils/chatAnswerText';

const props = defineProps({ message: { type: Object, required: true }, position: { type: Number, required: true }, disabled: Boolean });
const copying = ref(false);
async function copy() {
  if (props.disabled || copying.value || !props.message.content?.trim()) return;
  copying.value = true;
  try {
    await navigator.clipboard.writeText(props.message.content);
    ElMessage.success('回答正文已复制，保留 Markdown 原文');
  } catch {
    ElMessage.warning('剪贴板不可用，可点击“下载回答”保存文字');
  } finally {
    copying.value = false;
  }
}
function download() {
  if (props.disabled) return;
  try {
    const text = answerDownloadText(props.message);
    saveAs(new Blob([text], { type: 'text/plain;charset=utf-8' }), `灵犀回答-${props.position}.txt`);
  } catch (err) {
    ElMessage.warning(err.message || '下载失败，请稍后再试');
  }
}
</script>

<style scoped>
.answer-actions { display: inline-flex; gap: 4px; flex-wrap: wrap; }
.answer-actions .el-button + .el-button { margin-left: 0; }
</style>
