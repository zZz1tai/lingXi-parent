<template>
  <aside class="project-rail" aria-label="视频项目列表">
    <div class="rail-heading">
      <div><span>项目目录</span><strong>{{ total }}</strong></div>
      <small>选择一个项目继续创作</small>
    </div>

    <section class="toolbar" aria-label="筛选项目">
      <el-input
        :model-value="search"
        clearable
        placeholder="搜索项目名称"
        :prefix-icon="Search"
        @update:model-value="$emit('update:search', $event)"
        @keyup.enter="$emit('search')"
      />
      <div class="toolbar-row">
        <el-select
          :model-value="status"
          clearable
          placeholder="全部状态"
          @update:model-value="$emit('update:status', $event)"
          @change="$emit('search')"
        >
          <el-option label="草稿" value="DRAFT" />
          <el-option label="制作中" value="ACTIVE" />
          <el-option label="已暂停" value="PAUSED" />
          <el-option label="已归档" value="ARCHIVED" />
        </el-select>
        <el-button :icon="Refresh" aria-label="重置项目筛选" @click="$emit('reset')" />
      </div>
    </section>

    <nav v-loading="loading" class="project-list">
      <button
        v-for="project in projects"
        :key="project.projectId"
        type="button"
        class="project-list-item"
        :class="{ active: selectedId === project.projectId }"
        :aria-current="selectedId === project.projectId ? 'true' : undefined"
        @click="$emit('select', project)"
      >
        <span class="project-monogram" aria-hidden="true">
          <img v-if="project.coverUrl" :src="resolveProjectCoverUrl(project.coverUrl)" alt="" />
          <template v-else>{{ project.projectName.slice(0, 1) }}</template>
        </span>
        <span class="project-list-copy">
          <strong>{{ project.projectName }}</strong>
          <small>{{ project.visualStyle || '视觉风格待设定' }}</small>
          <span class="project-list-meta">
            <i :class="`status-${project.status?.toLowerCase() || 'draft'}`" />
            {{ statusLabel(project.status) }} · {{ formatDate(project.updateTime) }}
          </span>
        </span>
      </button>

      <div v-if="!loading && !projects.length" class="rail-empty">
        <span>暂无匹配项目</span>
        <small>调整筛选条件，或从右上角创建新项目。</small>
      </div>
    </nav>
  </aside>
</template>

<script setup>
import { Refresh, Search } from '@element-plus/icons-vue'
import { isExternal } from '@/utils/validate'

defineProps({
  projects: { type: Array, default: () => [] },
  selectedId: { type: [Number, String], default: null },
  total: { type: Number, default: 0 },
  loading: { type: Boolean, default: false },
  search: { type: String, default: '' },
  status: { type: String, default: '' }
})

defineEmits(['select', 'search', 'reset', 'update:search', 'update:status'])

function statusLabel(status) {
  return { DRAFT: '草稿', ACTIVE: '制作中', PAUSED: '已暂停', ARCHIVED: '已归档' }[status] || status || '草稿'
}

function formatDate(value) {
  if (!value) return '刚刚'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value).slice(5, 10)
  return `${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function resolveProjectCoverUrl(value) {
  if (!value) return ''
  const cover = String(value).split(',')[0]
  return isExternal(cover) ? cover : import.meta.env.VITE_APP_BASE_API + cover
}
</script>

<style scoped>
.project-rail { display: flex; min-height: 0; flex-direction: column; padding: 17px; border: 1px solid var(--studio-border); border-radius: 18px 10px 10px 18px; background: rgb(20 27 36 / 88%); box-shadow: inset 0 1px rgb(255 255 255 / 4%), 0 24px 70px rgb(0 0 0 / 22%); backdrop-filter: blur(18px); }
.rail-heading { padding: 3px 3px 16px; border-bottom: 1px solid var(--studio-border-soft); }
.rail-heading > div { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; }
.rail-heading span { font-size: 15px; font-weight: 700; }
.rail-heading strong { color: var(--studio-accent); font-size: 22px; font-variant-numeric: tabular-nums; }
.rail-heading small { display: block; margin-top: 3px; color: #748193; font-size: 11px; }
.toolbar { display: grid; gap: 9px; padding: 14px 0; }
.toolbar-row { display: grid; grid-template-columns: minmax(0, 1fr) 40px; gap: 8px; }
.toolbar :deep(.el-input), .toolbar :deep(.el-select) { width: 100%; }
.project-list { display: grid; min-height: 180px; max-height: calc(100dvh - 390px); flex: 1; align-content: start; gap: 7px; padding-right: 3px; overflow-y: auto; }
.project-list-item { display: grid; grid-template-columns: 45px minmax(0, 1fr); align-items: center; gap: 10px; width: 100%; padding: 10px; border: 1px solid transparent; border-radius: 10px; color: #dce2e9; background: transparent; font: inherit; text-align: left; cursor: pointer; transition: transform .2s ease, border-color .2s ease, background-color .2s ease; }
.project-list-item:hover { border-color: #303b4a; background: #19212c; transform: translateX(2px); }
.project-list-item.active { border-color: #4b3b2f; background: linear-gradient(90deg, rgb(229 144 74 / 14%), rgb(25 33 44 / 86%) 58%); box-shadow: inset 3px 0 var(--studio-accent); }
.project-monogram { display: grid; width: 45px; height: 52px; place-items: center; border-radius: 7px 14px 7px 7px; color: #f4ece5; background: radial-gradient(circle at 80% 85%, rgb(229 144 74 / 24%), transparent 45%), #242d39; font-size: 19px; font-weight: 750; }
.project-monogram img { width: 100%; height: 100%; border-radius: inherit; object-fit: cover; }
.project-list-copy { display: grid; min-width: 0; gap: 3px; }
.project-list-copy > strong, .project-list-copy > small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.project-list-copy > strong { font-size: 13px; }
.project-list-copy > small { color: #758295; font-size: 10px; }
.project-list-meta { display: flex; align-items: center; gap: 5px; color: #9aa5b3; font-size: 10px; font-variant-numeric: tabular-nums; }
.project-list-meta i { display: inline-block; width: 6px; height: 6px; border-radius: 50%; background: #7d8997; box-shadow: 0 0 0 3px rgb(125 137 151 / 10%); }
.project-list-meta i.status-active { background: #7aaa91; box-shadow: 0 0 0 3px rgb(122 170 145 / 12%); }
.project-list-meta i.status-paused { background: var(--studio-accent); box-shadow: 0 0 0 3px rgb(229 144 74 / 12%); }
.rail-empty { display: grid; place-items: center; gap: 4px; padding: 34px 12px; color: #a3adba; text-align: center; }
.rail-empty small { color: #697687; line-height: 1.5; }
@media (max-width: 820px) { .project-rail { border-radius: 15px; } .project-list { grid-template-columns: repeat(2, minmax(0, 1fr)); max-height: 300px; } }
@media (max-width: 700px) { .project-rail { padding: 14px; } .project-list { grid-template-columns: 1fr; max-height: 330px; } }
</style>
