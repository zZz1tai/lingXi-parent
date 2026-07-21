<template>
  <aside class="chapter-rail">
    <button type="button" class="back-button" @click="$emit('back')">
      <el-icon><ArrowLeft /></el-icon><span>返回项目工作台</span>
    </button>

    <div class="project-heading">
      <span class="project-mark">{{ project?.projectName?.slice(0, 1) || '项' }}</span>
      <div><small>当前项目</small><h2>{{ project?.projectName || '正在读取项目' }}</h2></div>
    </div>

    <el-button class="add-chapter-button" :icon="Plus" @click="$emit('add')">添加新章节</el-button>

    <div class="chapter-heading">
      <div><span>章节目录</span><strong>{{ chapters.length }}</strong></div>
      <small>选择章节查看素材和视频版本</small>
    </div>

    <div v-if="loading" class="chapter-skeleton" aria-label="正在加载章节">
      <span v-for="index in 4" :key="index" />
    </div>

    <div v-else-if="error" class="rail-state error-state">
      <strong>章节读取失败</strong><span>{{ error }}</span>
      <el-button size="small" @click="$emit('retry')">重新加载</el-button>
    </div>

    <nav v-else-if="chapters.length" class="chapter-list" aria-label="章节列表">
      <button
        v-for="chapter in chapters"
        :key="chapter.chapterId"
        type="button"
        :class="{ active: String(chapter.chapterId) === String(activeId) }"
        @click="$emit('select', chapter)"
      >
        <span class="chapter-no">{{ String(chapter.chapterNo || 0).padStart(2, '0') }}</span>
        <span class="chapter-copy">
          <strong>{{ chapter.chapterTitle || `第 ${chapter.chapterNo} 章` }}</strong>
          <small>{{ chapter.wordCount || 0 }} 字 · {{ pipelineLabel(chapter.pipelineStatus) }}</small>
        </span>
        <i :class="statusClass(chapter)" />
      </button>
    </nav>

    <div v-else class="rail-state">
      <strong>还没有章节</strong><span>返回项目工作台导入小说原文。</span>
    </div>
  </aside>
</template>

<script setup>
import { ArrowLeft, Plus } from '@element-plus/icons-vue'

defineProps({
  project: { type: Object, default: null },
  chapters: { type: Array, default: () => [] },
  activeId: { type: [Number, String], default: null },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' }
})

defineEmits(['back', 'add', 'select', 'retry'])

function pipelineLabel(status) {
  return { IMPORTED: '待解析', PARSING: '解析中', SCRIPT_READY: '素材已准备', VIDEO_READY: '视频已准备' }[status] || status || '待处理'
}

function statusClass(chapter) {
  if (chapter.parseStatus === 'FAILED') return 'is-error'
  if (chapter.parseStatus === 'RUNNING') return 'is-running'
  if (chapter.pipelineStatus === 'SCRIPT_READY' || chapter.pipelineStatus === 'VIDEO_READY') return 'is-ready'
  return ''
}
</script>

<style scoped>
.chapter-rail { display: flex; min-height: calc(100dvh - 128px); flex-direction: column; padding: 18px; border: 1px solid #273241; border-radius: 18px 10px 10px 18px; background: rgb(18 25 34 / 92%); box-shadow: inset 0 1px rgb(255 255 255 / 4%), 0 24px 70px rgb(0 0 0 / 24%); }
.back-button { display: flex; align-items: center; gap: 8px; padding: 7px 4px 14px; border: 0; color: #8e9aaa; background: transparent; font: inherit; font-size: 12px; cursor: pointer; }
.back-button:hover { color: #f0a15d; }
.project-heading { display: flex; align-items: center; gap: 12px; padding: 16px 0 20px; border-top: 1px solid #202a36; border-bottom: 1px solid #202a36; }
.project-mark { display: grid; width: 46px; height: 52px; flex: 0 0 auto; place-items: center; border-radius: 7px 15px 7px 7px; color: #f5eee8; background: radial-gradient(circle at 75% 80%, rgb(229 144 74 / 28%), transparent 44%), #283240; font-size: 20px; font-weight: 750; }
.project-heading div { min-width: 0; }
.project-heading small { color: #e5904a; font-size: 9px; font-weight: 700; letter-spacing: .08em; }
.project-heading h2 { overflow: hidden; margin: 3px 0 0; color: #edf1f5; font-size: 15px; text-overflow: ellipsis; white-space: nowrap; }
.add-chapter-button { width: 100%; margin-top: 18px; border-color: #59422f; color: #efaa6d; background: rgb(229 144 74 / 7%); }
.add-chapter-button:hover, .add-chapter-button:focus { border-color: #e5904a; color: #17120e; background: #e5904a; }
.chapter-heading { padding: 20px 2px 13px; }
.chapter-heading > div { display: flex; align-items: baseline; justify-content: space-between; }
.chapter-heading span { color: #dce2e9; font-size: 14px; font-weight: 700; }
.chapter-heading strong { color: #e5904a; font-size: 18px; }
.chapter-heading small { color: #687587; font-size: 10px; }
.chapter-list { display: grid; max-height: calc(100dvh - 390px); align-content: start; gap: 6px; padding-right: 3px; overflow-y: auto; }
.chapter-list button { display: grid; grid-template-columns: 35px minmax(0, 1fr) 8px; align-items: center; gap: 10px; padding: 11px 10px; border: 1px solid transparent; border-radius: 9px; color: #dbe1e8; background: transparent; font: inherit; text-align: left; cursor: pointer; }
.chapter-list button:hover { background: #18212b; }
.chapter-list button.active { border-color: #4d3b2d; background: linear-gradient(90deg, rgb(229 144 74 / 13%), #18202a); box-shadow: inset 3px 0 #e5904a; }
.chapter-no { color: #e5904a; font-family: Consolas, monospace; font-size: 13px; font-weight: 700; }
.chapter-copy { display: grid; min-width: 0; gap: 3px; }
.chapter-copy strong, .chapter-copy small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.chapter-copy strong { font-size: 12px; }
.chapter-copy small { color: #748193; font-size: 9px; }
.chapter-list i { width: 6px; height: 6px; border-radius: 50%; background: #677383; }
.chapter-list i.is-ready { background: #77a68c; }
.chapter-list i.is-running { background: #e5904a; box-shadow: 0 0 0 4px rgb(229 144 74 / 10%); }
.chapter-list i.is-error { background: #d56d6d; }
.chapter-skeleton { display: grid; gap: 8px; }
.chapter-skeleton span { height: 58px; border-radius: 9px; background: linear-gradient(90deg, #17202a 25%, #202a36 37%, #17202a 63%); background-size: 400% 100%; animation: shimmer 1.4s ease infinite; }
.rail-state { display: grid; place-items: center; gap: 6px; padding: 36px 12px; color: #aab3bf; text-align: center; }
.rail-state span { color: #6f7b8c; font-size: 11px; line-height: 1.55; }
.error-state strong { color: #e59a9a; }
@keyframes shimmer { 0% { background-position: 100% 0; } 100% { background-position: 0 0; } }
@media (max-width: 900px) { .chapter-rail { min-height: auto; border-radius: 15px; } .chapter-list { grid-template-columns: repeat(2, minmax(0, 1fr)); max-height: 280px; } }
@media (max-width: 620px) { .chapter-list { grid-template-columns: 1fr; max-height: 260px; } }
</style>
