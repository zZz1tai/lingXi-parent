<template>
  <section class="video-panel">
    <div class="panel-heading">
      <div><small>Video output</small><h2>视频版本</h2><p>按镜头查看草稿、生成状态和已完成的视频版本。</p></div>
      <span>{{ assets.length }}</span>
    </div>

    <div
      v-loading="loading"
      :aria-busy="loading"
      element-loading-text="正在加载视频版本"
      element-loading-background="rgba(12, 17, 24, 0.72)"
      class="video-panel-body"
    >
      <div v-if="error" class="panel-state error-state">
        <strong>视频版本读取失败</strong><p>{{ error }}</p><el-button @click="$emit('retry-load')">重新加载</el-button>
      </div>
      <div v-else-if="!assets.length" class="panel-state">
        <div class="empty-mark">▶</div><strong>还没有视频版本</strong>
        <p>先确认分镜关键帧，再准备视频提示词和生成任务。</p>
        <el-button type="primary" :loading="preparing" @click="$emit('prepare')">准备视频草稿</el-button>
      </div>
      <div v-else class="video-list">
        <article v-for="asset in assets" :key="asset.assetId" class="video-card">
          <div class="video-preview">
            <video v-if="asset.objectKey || asset.previewObjectKey" :src="asset.objectKey || asset.previewObjectKey" controls preload="metadata" />
            <div v-else class="video-placeholder"><span>▶</span><small>{{ placeholder(asset) }}</small></div>
          </div>
          <div class="video-body">
            <div class="video-topline"><span>{{ statusLabel(asset.status) }}</span><small>{{ durationLabel(asset) }}</small></div>
            <h3>{{ asset.assetName || `视频版本 #${asset.assetId}` }}</h3>
            <p>{{ asset.promptText || '视频提示词尚未填写。' }}</p>
            <div v-if="taskByAssetId[asset.assetId]" class="task-line">{{ taskLabel(taskByAssetId[asset.assetId]) }}</div>
            <div class="video-actions">
              <el-button v-if="asset.status === 'DRAFT'" type="primary" size="small" @click="$emit('edit', asset)">确认提示词</el-button>
              <el-button v-if="asset.status === 'REJECTED'" type="warning" size="small" @click="$emit('edit', asset)">修改并重试</el-button>
              <el-button text type="danger" size="small" @click="$emit('delete', asset)">删除版本</el-button>
            </div>
          </div>
        </article>
      </div>
    </div>
  </section>
</template>

<script setup>
defineProps({
  assets: { type: Array, default: () => [] },
  taskByAssetId: { type: Object, default: () => ({}) },
  loading: { type: Boolean, default: false },
  preparing: { type: Boolean, default: false },
  error: { type: String, default: '' }
})

defineEmits(['retry-load', 'prepare', 'edit', 'delete'])

function statusLabel(status) {
  return { DRAFT: '待确认提示词', GENERATING: '生成中', GENERATED: '已生成', APPROVED: '已完成', REJECTED: '生成失败' }[status] || status || '未知状态'
}

function placeholder(asset) {
  if (asset.status === 'DRAFT') return '等待确认视频提示词'
  if (asset.status === 'GENERATING') return '视频正在生成'
  if (asset.status === 'REJECTED') return '生成失败，可修改后重试'
  return '暂无视频文件'
}

function durationLabel(asset) {
  if (!asset.durationMs) return '时长待定'
  return `${(asset.durationMs / 1000).toFixed(asset.durationMs % 1000 ? 1 : 0)} 秒`
}

function taskLabel(task) {
  const progress = task.progressPercent !== undefined && task.progressPercent !== null ? ` · ${task.progressPercent}%` : ''
  return `${task.stageLabel || task.status || '处理中'}${progress}`
}
</script>

<style scoped>
.panel-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; margin-bottom: 18px; }
.panel-heading small { color: #e5904a; font-size: 9px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
.panel-heading h2 { margin: 5px 0 6px; color: #eff2f5; font-size: 23px; letter-spacing: -.025em; }
.panel-heading p { margin: 0; color: #7f8b9b; font-size: 12px; }
.panel-heading > span { color: #e5904a; font-family: Consolas, monospace; font-size: 28px; }
.video-panel-body { min-height: 390px; }
.video-list { display: grid; gap: 14px; }
.video-card { display: grid; grid-template-columns: minmax(260px, 38%) minmax(0, 1fr); min-height: 210px; overflow: hidden; border: 1px solid #263140; border-radius: 12px; background: #131b24; }
.video-preview { display: grid; min-height: 210px; place-items: center; background: #0c1219; }
.video-preview video { width: 100%; height: 100%; object-fit: cover; }
.video-placeholder { display: grid; place-items: center; gap: 9px; color: #687587; text-align: center; }
.video-placeholder span { display: grid; width: 48px; height: 48px; place-items: center; border: 1px solid #4b3b2f; border-radius: 14px; color: #e5904a; background: rgb(229 144 74 / 8%); }
.video-placeholder small { font-size: 10px; }
.video-body { display: flex; min-width: 0; flex-direction: column; padding: 18px; }
.video-topline { display: flex; justify-content: space-between; gap: 12px; color: #d19460; font-size: 10px; }
.video-topline small { color: #7c8999; }
.video-body h3 { margin: 14px 0 8px; color: #e8ecf0; font-size: 15px; }
.video-body > p { display: -webkit-box; overflow: hidden; margin: 0; color: #778495; font-size: 11px; line-height: 1.7; -webkit-box-orient: vertical; -webkit-line-clamp: 3; }
.task-line { margin-top: 12px; padding: 7px 9px; border-radius: 6px; color: #d69a68; background: rgb(229 144 74 / 7%); font-size: 10px; }
.video-actions { display: flex; align-items: center; gap: 6px; margin-top: auto; padding-top: 16px; }
.video-actions :deep(.el-button + .el-button) { margin-left: 0; }
.video-actions :deep(.el-button:last-child) { margin-left: auto; }
.panel-state { display: grid; min-height: 390px; place-items: center; align-content: center; padding: 36px; border: 1px dashed #2c3745; border-radius: 12px; color: #aeb7c2; text-align: center; }
.panel-state p { margin: 7px 0 18px; color: #6f7c8d; font-size: 11px; }
.empty-mark { display: grid; width: 62px; height: 62px; place-items: center; margin-bottom: 16px; border: 1px solid #4a3a2e; border-radius: 15px 27px 15px 15px; color: #e5904a; background: rgb(229 144 74 / 7%); }
.error-state strong { color: #e39393; }
@media (max-width: 720px) { .video-card { grid-template-columns: 1fr; } .video-preview { min-height: 190px; } }
</style>
