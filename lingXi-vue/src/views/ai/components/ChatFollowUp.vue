<template>
  <el-dialog v-model="visible" title="引用追问" width="min(680px, 94vw)" append-to-body destroy-on-close>
    <p>保留需要讨论的原文片段，再填写问题。将追加到输入框，确认后再发送。</p>
    <el-alert v-if="truncated" type="info" :closable="false" title="回答较长，已截取前 6000 字；可替换为需要追问的片段。" />
    <el-form label-position="top" @submit.prevent>
      <el-form-item label="引用原文" label-for="follow-up-quote">
        <el-input id="follow-up-quote" v-model="quote" type="textarea" :rows="6" :maxlength="QUOTE_LIMIT" show-word-limit />
      </el-form-item>
      <el-form-item label="追问问题" label-for="follow-up-question">
        <el-input id="follow-up-question" v-model="question" type="textarea" :rows="3" :maxlength="QUESTION_LIMIT" show-word-limit placeholder="例如：请展开解释这一段，并给出具体例子" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :disabled="disabled || !quote.trim() || !question.trim()" @click="insert">放入输入框</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue';
import { ElAlert, ElButton, ElDialog, ElForm, ElFormItem, ElInput } from 'element-plus';
import { QUOTE_LIMIT, QUESTION_LIMIT } from '@/utils/chatFollowUp';

const props = defineProps({ disabled: Boolean });
const emit = defineEmits(['insert']);
const visible = ref(false);
const quote = ref('');
const question = ref('');
const truncated = ref(false);
watch(() => props.disabled, value => { if (value) visible.value = false; });
function open(content) {
  if (props.disabled || !content?.trim()) return;
  truncated.value = content.length > QUOTE_LIMIT;
  // 避免截断 UTF-16 代理对。
  quote.value = content.slice(0, QUOTE_LIMIT).replace(/[\uD800-\uDBFF]$/, '');
  question.value = '';
  visible.value = true;
}
function insert() {
  if (props.disabled || !quote.value.trim() || !question.value.trim()) return;
  emit('insert', quote.value, question.value);
  visible.value = false;
}
defineExpose({ open });
</script>
