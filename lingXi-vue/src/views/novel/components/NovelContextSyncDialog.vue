<template>
  <el-dialog
    :model-value="modelValue"
    title="AI 资料同步建议"
    width="min(720px, calc(100vw - 32px))"
    append-to-body
    destroy-on-close
    class="nk-dialog-paper nk-context-sync-dialog"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-alert
      title="AI 只整理候选变化，不会静默修改或删除资料。只有你勾选并确认后，设定集与伏笔才会写回。"
      type="info"
      :closable="false"
      show-icon
    />
    <div class="ncs-summary">
      <span>分析章节：{{ chapterTitle || '当前章节' }}</span>
      <span>共 {{ suggestions.length }} 条建议，已选 {{ selectedIds.length }} 条</span>
    </div>

    <el-checkbox-group v-model="selectedIds" class="ncs-list">
      <div
        v-for="item in suggestions"
        :key="item.clientId"
        class="ncs-card"
        :class="{ 'is-selected': selectedIds.includes(item.clientId) }"
        @click="toggleSelected(item.clientId)"
      >
        <el-checkbox :value="item.clientId" @click.stop />
        <div class="ncs-card-main">
          <div class="ncs-card-head">
            <el-tag size="small" :type="item.resourceType === 'setting' ? 'primary' : 'warning'">
              {{ item.resourceType === 'setting' ? '设定' : '伏笔' }}
            </el-tag>
            <el-tag size="small" :type="item.operation === 'ADD' ? 'success' : 'info'">
              {{ item.operation === 'ADD' ? '新增' : '更新' }}
            </el-tag>
            <strong>{{ item.title }}</strong>
            <span v-if="item.targetId" class="ncs-target">#{{ item.targetId }}</span>
          </div>
          <p class="ncs-content">{{ suggestionContent(item) }}</p>
          <div v-if="item.resourceType === 'foreshadow'" class="ncs-meta">
            <span>状态：{{ statusLabel(item.status) }}</span>
            <span>等级：{{ priorityLabel(item.priority) }}</span>
            <span v-if="item.keyword">关键词：{{ item.keyword }}</span>
            <span v-if="item.resolveChapterNo">计划第 {{ item.resolveChapterNo }} 章回收</span>
          </div>
          <blockquote>“{{ item.evidence }}”</blockquote>
          <p class="ncs-reason">{{ item.reason }}</p>
        </div>
      </div>
    </el-checkbox-group>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">稍后处理</el-button>
      <el-button
        type="primary"
        :loading="applying"
        :disabled="selectedIds.length === 0"
        @click="applySelected"
      >
        确认应用 {{ selectedIds.length }} 条
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  suggestions: { type: Array, default: () => [] },
  chapterTitle: { type: String, default: '' },
  applying: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'apply'])
const selectedIds = ref([])

watch(
  () => props.suggestions,
  suggestions => {
    selectedIds.value = suggestions.filter(item => item.defaultSelected).map(item => item.clientId)
  },
  { immediate: true, deep: true }
)

function applySelected() {
  const selected = new Set(selectedIds.value)
  emit('apply', props.suggestions.filter(item => selected.has(item.clientId)))
}

function toggleSelected(clientId) {
  selectedIds.value = selectedIds.value.includes(clientId)
    ? selectedIds.value.filter(id => id !== clientId)
    : [...selectedIds.value, clientId]
}

function suggestionContent(item) {
  return item.resourceType === 'setting'
    ? item.content
    : (item.description || '正文中出现了新的伏笔变化')
}

function statusLabel(status) {
  return { buried: '已埋', pending: '待解', resolved: '已解' }[status] || status || '未指定'
}

function priorityLabel(priority) {
  return { high: '高', medium: '中', low: '低' }[priority] || priority || '未指定'
}
</script>

<style scoped>
.ncs-summary {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin: 14px 2px 10px;
  color: var(--nk-ink-soft);
  font-size: 13px;
}

.ncs-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 52vh;
  overflow-y: auto;
  padding-right: 4px;
}

.ncs-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px;
  border: 1px solid rgba(110, 72, 34, 0.25);
  border-radius: 7px;
  background: rgba(255, 252, 242, 0.72);
  cursor: pointer;
  transition: border-color 0.15s, background-color 0.15s;
}

.ncs-card.is-selected {
  border-color: rgba(160, 86, 46, 0.55);
  background: rgba(160, 86, 46, 0.06);
}

.ncs-card-main { flex: 1; min-width: 0; }
.ncs-card-head { display: flex; align-items: center; gap: 7px; }
.ncs-card-head strong { flex: 1; color: var(--nk-ink); }
.ncs-target { color: var(--nk-ink-faint); font-size: 11px; }
.ncs-content { margin: 8px 0 5px; line-height: 1.7; color: var(--nk-ink-soft); white-space: pre-wrap; }
.ncs-meta { display: flex; flex-wrap: wrap; gap: 10px; color: var(--nk-ink-faint); font-size: 12px; }
.ncs-card blockquote {
  margin: 8px 0 4px;
  padding: 5px 9px;
  border-left: 3px solid rgba(160, 86, 46, 0.35);
  color: var(--nk-ink-faint);
  background: rgba(110, 72, 34, 0.04);
}
.ncs-reason { margin: 4px 0 0; color: var(--nk-ink-soft); font-size: 12px; }
</style>
