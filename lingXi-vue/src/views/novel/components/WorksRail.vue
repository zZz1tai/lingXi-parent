<template>
  <aside class="nk-rail">
    <div class="nk-rail-head">
      <div class="nk-rail-title">
        <el-icon class="nk-quill"><EditPen /></el-icon>
        <span>我的书斋</span>
      </div>
      <button class="nk-rail-new" type="button" @click="handleNew">
        <el-icon><Plus /></el-icon>新作
      </button>
    </div>

    <div class="nk-rail-tabs">
      <button
        type="button"
        class="nk-rail-tab"
        :class="{ 'is-active': category === 'short' }"
        @click="switchCategory('short')"
      >
        短篇<span class="nk-tab-count">{{ shortCount }}</span>
      </button>
      <button
        type="button"
        class="nk-rail-tab"
        :class="{ 'is-active': category === 'novel' }"
        @click="switchCategory('novel')"
      >
        长篇<span class="nk-tab-count">{{ novelCount }}</span>
      </button>
    </div>

    <div class="nk-rail-search">
      <el-icon class="nk-search-icon"><Search /></el-icon>
      <input v-model.trim="keywordModel" type="text" placeholder="翻找手稿…" />
    </div>

    <div class="nk-rail-list">
      <div v-if="loading" class="nk-skeleton">
        <div v-for="i in 4" :key="i" class="nk-skeleton-line" />
      </div>

      <template v-else-if="visibleWorks.length">
        <article
          v-for="work in visibleWorks"
          :key="work.workId"
          class="nk-book"
          :class="{ 'is-active': work.workId === selectedId }"
          @click="handleSelect(work)"
        >
          <div class="nk-book-spine">{{ work.workName.slice(0, 3) }}</div>
          <div class="nk-book-body">
            <div class="nk-book-title">{{ work.workName }}</div>
            <div class="nk-book-meta">
              <span>{{ formatWordCount(work.wordCount || 0) }}</span>
              <span v-if="work.genre">{{ work.genre }}</span>
              <span class="nk-book-seal">{{ workTypeLabel(work.workType) }}</span>
            </div>
          </div>
          <el-dropdown
            trigger="click"
            placement="bottom-end"
            @command="(command) => handleCommand(command, work)"
            @click.stop
          >
            <el-icon class="nk-book-more"><MoreFilled /></el-icon>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="edit">
                  <el-icon><EditPen /></el-icon><span>修改信息</span>
                </el-dropdown-item>
                <el-dropdown-item command="delete" divided class="danger-item">
                  <el-icon><Delete /></el-icon><span>焚毁手稿</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </article>
      </template>

      <div v-else class="nk-rail-empty">
        <span class="nk-empty-icon"><el-icon><Notebook /></el-icon></span>
        {{ keyword ? '没有找到相符的手稿' : '书斋还空着，动笔写第一本吧' }}
      </div>
    </div>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import { EditPen, Plus, Search, MoreFilled, Delete, Notebook } from '@element-plus/icons-vue'

const props = defineProps({
  works: { type: Array, default: () => [] },
  selectedId: { type: [Number, String], default: null },
  category: { type: String, default: 'short' },
  loading: { type: Boolean, default: false },
  keyword: { type: String, default: '' }
})

const emit = defineEmits([
  'update:category',
  'update:keyword',
  'select',
  'new',
  'edit',
  'delete'
])

const shortCount = computed(() => props.works.filter(w => w.workType === 'short').length)
const novelCount = computed(() => props.works.filter(w => w.workType === 'novel').length)

const visibleWorks = computed(() => {
  const kw = props.keyword.trim().toLowerCase()
  return props.works.filter(work => {
    const inCategory = work.workType === props.category
    if (!inCategory) return false
    if (!kw) return true
    return String(work.workName || '').toLowerCase().includes(kw)
  })
})

const keywordModel = computed({
  get: () => props.keyword,
  set: value => emit('update:keyword', value)
})

function switchCategory(category) {
  emit('update:category', category)
}

function handleSelect(work) {
  emit('select', work)
}

function handleNew() {
  emit('new')
}

function handleCommand(command, work) {
  if (command === 'edit') emit('edit', work)
  else if (command === 'delete') emit('delete', work)
}

function workTypeLabel(type) {
  return type === 'novel' ? '长篇' : '短篇'
}

function formatWordCount(count) {
  if (!count) return '0 字'
  if (count < 10000) return `${count} 字`
  return `${(count / 10000).toFixed(1)} 万字`
}
</script>

<style scoped>
.danger-item :deep(.el-dropdown-menu__item) { color: var(--nk-seal); }
</style>
