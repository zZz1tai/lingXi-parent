<template>
  <el-button :icon="Search" :disabled="disabled || !messages.length" @click="visible = true">搜索对话</el-button>
  <el-dialog v-model="visible" title="搜索当前对话" width="min(640px, 94vw)" append-to-body @opened="input?.focus()" @closed="finishLocate">
    <el-input ref="input" v-model="query" placeholder="输入关键词查找消息正文" aria-label="搜索对话关键词" clearable :maxlength="200" />
    <p class="search-hint" role="status">{{ query.trim() ? `找到 ${results.length} 条消息` : '搜索当前会话已加载的消息正文，不含附件正文与参考资料。' }}</p>
    <p v-if="query.trim() && !results.length">没有匹配消息，请换个关键词。</p>
    <ol class="search-results">
      <li v-for="result in results" :key="result.index">
        <button type="button" class="search-result" @click="locate(result.index)">
          <span class="search-role">{{ result.role }} · 第 {{ result.index + 1 }} 条消息 · 定位原文</span>
          <span class="search-excerpt">{{ result.before }}<mark>{{ result.match }}</mark>{{ result.after }}</span>
        </button>
      </li>
    </ol>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { ElButton, ElDialog, ElInput } from 'element-plus';
import { Search } from '@element-plus/icons-vue';
import { searchChatMessages } from '@/utils/chatSearch';

const props = defineProps({ messages: { type: Array, default: () => [] }, disabled: Boolean });
const emit = defineEmits(['locate']);
const visible = ref(false);
const query = ref('');
const input = ref(null);
const pendingIndex = ref(null);
const results = computed(() => searchChatMessages(props.messages, query.value));
watch(() => props.disabled, disabled => { if (disabled) visible.value = false; });
function locate(index) {
  pendingIndex.value = index;
  visible.value = false;
}
function finishLocate() {
  if (pendingIndex.value !== null && !props.disabled) emit('locate', pendingIndex.value);
  pendingIndex.value = null;
}
</script>

<style scoped>
.search-hint, .search-role { color: var(--el-text-color-secondary); font-size: 12px; }
.search-results { list-style: none; margin: 0; padding: 0; max-height: 55vh; overflow-y: auto; }
.search-results li + li { margin-top: 10px; }
.search-result { display: block; width: 100%; padding: 12px; text-align: left; cursor: pointer; border: 1px solid var(--el-border-color); border-radius: 8px; background: var(--el-bg-color); color: var(--el-text-color-primary); }
.search-result:hover { border-color: var(--el-color-primary); }
.search-result:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: -2px; }
.search-excerpt { display: block; margin-top: 8px; white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.7; }
mark { background: var(--el-color-warning-light-7); color: var(--el-text-color-primary); }
</style>
