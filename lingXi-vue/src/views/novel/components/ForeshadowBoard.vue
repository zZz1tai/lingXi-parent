<template>
  <div class="nf-board">
    <div class="nf-tabs">
      <button
        v-for="tab in tabs"
        :key="tab.value"
        type="button"
        class="nf-tab"
        :class="{ 'is-active': filter === tab.value }"
        @click="filter = tab.value"
      >
        {{ tab.label }}
        <span v-if="countOf(tab.value)" class="nf-count">{{ countOf(tab.value) }}</span>
      </button>
    </div>

    <div class="nf-list">
      <div v-for="card in filteredCards" :key="card.foreshadowId" class="nf-card" @click="handleEdit(card)">
        <div class="nf-card-head">
          <span class="nf-status" :class="`is-${card.status || 'buried'}`">{{ statusLabel(card.status) }}</span>
          <span class="nf-card-title">{{ card.title || '未命名伏笔' }}</span>
          <el-icon class="nf-card-more" @click.stop="handleDelete(card)"><Delete /></el-icon>
        </div>
        <div class="nf-card-meta">
          <span class="nf-priority" :class="`is-${card.priority || 'medium'}`">{{ priorityLabel(card.priority) }}</span>
          <span v-if="card.keyword" class="nf-keyword">「{{ card.keyword }}」</span>
          <span v-if="card.resolveChapterNo" class="nf-chapter">约第 {{ card.resolveChapterNo }} 章回收</span>
          <button
            v-if="card.status !== 'resolved'"
            type="button"
            class="nf-resolve"
            title="在正文中完成回收后，将伏笔标记为已解"
            @click.stop="handleResolve(card)"
          >
            <el-icon><CircleCheck /></el-icon>已回收
          </button>
        </div>
        <div class="nf-card-body">{{ card.description || '暂无描述，点击补充伏笔详情' }}</div>
      </div>

      <button class="nf-card nf-card-add" type="button" @click="handleAdd">
        <el-icon><Plus /></el-icon>埋一条新伏笔
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { CircleCheck, Delete, Plus } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'

const props = defineProps({
  cards: { type: Array, default: () => [] }
})

const emit = defineEmits(['add', 'edit', 'delete', 'resolve'])

const tabs = [
  { value: '', label: '全部' },
  { value: 'buried', label: '已埋' },
  { value: 'pending', label: '待解' },
  { value: 'resolved', label: '已解' }
]

const filter = ref('')

const statusLabels = { buried: '已埋', pending: '待解', resolved: '已解' }
const priorityLabels = { high: '高', medium: '中', low: '低' }

const filteredCards = computed(() => {
  if (!filter.value) return props.cards
  return props.cards.filter(card => (card.status || 'buried') === filter.value)
})

function countOf(value) {
  if (!value) return props.cards.length || ''
  return props.cards.filter(card => (card.status || 'buried') === value).length || ''
}

function statusLabel(status) {
  return statusLabels[status] || status
}

function priorityLabel(priority) {
  return priorityLabels[priority] || priority
}

function handleAdd() {
  emit('add')
}

function handleEdit(card) {
  emit('edit', card)
}

function handleDelete(card) {
  ElMessageBox.confirm(`确定删除「${card.title || '这条伏笔'}」吗？删除后不再参与上下文。`, '剪断这根线', {
    confirmButtonText: '删除',
    cancelButtonText: '留着',
    type: 'warning'
  })
    .then(() => emit('delete', card))
    .catch(() => {})
}

function handleResolve(card) {
  ElMessageBox.confirm(`确认「${card.title || '这条伏笔'}」已在正文中完成回收？`, '标记已解', {
    confirmButtonText: '标记已解',
    cancelButtonText: '再等等',
    type: 'info'
  })
    .then(() => emit('resolve', card))
    .catch(() => {})
}
</script>

<style scoped>
.nf-board {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.nf-tabs {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;

  .nf-tab {
    font-size: 12px;
    padding: 3px 10px;
    border-radius: 12px;
    border: 1px solid rgba(110, 72, 34, 0.3);
    background: transparent;
    color: var(--nk-ink-soft);
    cursor: pointer;
    letter-spacing: 1px;

    &:hover { color: var(--nk-sienna); border-color: rgba(160, 86, 46, 0.5); }

    &.is-active {
      background: rgba(160, 86, 46, 0.12);
      color: var(--nk-sienna);
      border-color: rgba(160, 86, 46, 0.55);
      font-weight: 700;
    }

    .nf-count {
      font-size: 10px;
      margin-left: 3px;
      opacity: 0.75;
    }
  }
}

.nf-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.nf-card {
  border-radius: 6px;
  border: 1px solid rgba(110, 72, 34, 0.4);
  background: rgba(255, 250, 235, 0.85);
  box-shadow: 0 3px 9px rgba(67, 48, 31, 0.16);
  padding: 10px 12px;
  position: relative;
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;

  &:hover { transform: translateY(-2px); box-shadow: 0 5px 14px rgba(67, 48, 31, 0.24); }

  &.nf-card-add {
    border-style: dashed;
    background: transparent;
    box-shadow: none;
    text-align: center;
    color: var(--nk-ink-soft);
    font-size: 13px;
    letter-spacing: 2px;

    &:hover { color: var(--nk-sienna); background: rgba(255, 250, 235, 0.5); }
  }

  .nf-card-head {
    display: flex;
    align-items: center;
    gap: 8px;

    .nf-status {
      font-size: 10.5px;
      letter-spacing: 1px;
      font-weight: 700;
      border-radius: 2px;
      padding: 1px 5px;
      transform: rotate(-2deg);

      &.is-buried { color: var(--nk-seal); border: 1px solid rgba(168, 59, 44, 0.5); background: rgba(168, 59, 44, 0.05); }
      &.is-pending { color: #8a6d1d; border: 1px solid rgba(190, 145, 20, 0.55); background: rgba(190, 145, 20, 0.07); }
      &.is-resolved { color: #3c6e47; border: 1px solid rgba(60, 110, 71, 0.5); background: rgba(60, 110, 71, 0.06); }
    }

    .nf-card-title {
      font-size: 14px;
      font-weight: 700;
      flex: 1;
      min-width: 0;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .nf-card-more { color: var(--nk-ink-faint); font-size: 13px; }
  }

  .nf-card-meta {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 6px;
    font-size: 11.5px;
    flex-wrap: wrap;

    .nf-priority {
      border-radius: 2px;
      padding: 0 5px;
      font-weight: 700;

      &.is-high { color: var(--nk-seal); border: 1px solid rgba(168, 59, 44, 0.5); }
      &.is-medium { color: #8a6d1d; border: 1px solid rgba(190, 145, 20, 0.5); }
      &.is-low { color: #5c7285; border: 1px solid rgba(92, 114, 133, 0.45); }
    }

    .nf-keyword, .nf-chapter { color: var(--nk-ink-faint); }

    .nf-resolve {
      margin-left: auto;
      display: inline-flex;
      align-items: center;
      gap: 3px;
      font-size: 11.5px;
      color: #3c6e47;
      background: rgba(60, 110, 71, 0.08);
      border: 1px solid rgba(60, 110, 71, 0.45);
      border-radius: 3px;
      padding: 1px 7px;
      cursor: pointer;

      .el-icon { font-size: 12px; }

      &:hover { background: rgba(60, 110, 71, 0.18); }
    }
  }

  .nf-card-body {
    margin-top: 6px;
    font-size: 12.5px;
    color: var(--nk-ink-soft);
    line-height: 1.8;
    display: -webkit-box;
    -webkit-line-clamp: 3;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
}
</style>
