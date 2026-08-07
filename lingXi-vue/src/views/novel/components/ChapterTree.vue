<template>
  <div class="nk-chapter-list">
    <article
      v-for="chapter in chapters"
      :key="chapter.chapterId"
      class="nk-chapter-item"
      :class="{ 'is-active': chapter.chapterId === selectedId }"
      @click="handleSelect(chapter)"
    >
      <span class="nk-chapter-no">{{ chapterNo(chapter) }}</span>
      <span class="nk-chapter-name">{{ chapter.chapterTitle || `第 ${chapterNo(chapter)} 章` }}</span>
      <span class="nk-chapter-meta">{{ chapter.wordCount || 0 }}</span>
      <el-dropdown trigger="click" placement="bottom-end" @command="command => handleCommand(command, chapter)" @click.stop>
        <el-icon class="nk-chapter-more"><MoreFilled /></el-icon>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="rename">
              <el-icon><EditPen /></el-icon><span>重命名</span>
            </el-dropdown-item>
            <el-dropdown-item command="delete" divided class="danger-item">
              <el-icon><Delete /></el-icon><span>删除章节</span>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </article>

    <button class="nk-btn nk-chapter-add" type="button" @click="handleAdd">
      <el-icon><Plus /></el-icon>新起一章
    </button>
  </div>
</template>

<script setup>
import { Delete, EditPen, MoreFilled, Plus } from '@element-plus/icons-vue'

const props = defineProps({
  chapters: { type: Array, default: () => [] },
  selectedId: { type: [Number, String], default: null }
})

const emit = defineEmits(['select', 'add', 'rename', 'delete'])

function chapterNo(chapter, index) {
  if (chapter.chapterNo) return `第 ${chapter.chapterNo} 章`
  return `第 ${props.chapters.indexOf(chapter) + 1} 章`
}

function handleSelect(chapter) {
  emit('select', chapter)
}

function handleAdd() {
  emit('add')
}

function handleCommand(command, chapter) {
  if (command === 'rename') emit('rename', chapter)
  else if (command === 'delete') emit('delete', chapter)
}
</script>

<style scoped>
.nk-chapter-meta {
  font-size: 11px;
  color: var(--nk-ink-faint);
  flex: 0 0 auto;
}

.nk-chapter-item.is-active .nk-chapter-name { color: var(--nk-sienna); font-weight: 700; }
.danger-item :deep(.el-dropdown-menu__item) { color: var(--nk-seal); }
</style>
