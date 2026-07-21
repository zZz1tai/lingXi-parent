<template>
  <section class="asset-panel" :aria-label="title">
    <div class="panel-heading">
      <div><small>{{ eyebrow }}</small><h2>{{ title }}</h2><p>{{ description }}</p></div>
      <span class="asset-count">{{ assets.length }}</span>
    </div>

    <div v-if="loading" class="asset-grid" aria-label="正在加载素材">
      <article v-for="index in 4" :key="index" class="asset-card skeleton-card"><span /><div /></article>
    </div>

    <div v-else-if="error" class="panel-state error-state">
      <strong>素材读取失败</strong><p>{{ error }}</p><el-button @click="$emit('retry-load')">重新加载</el-button>
    </div>

    <div v-else-if="!assets.length" class="panel-state empty-state">
      <div class="empty-mark">{{ emptyMark }}</div>
      <strong>{{ emptyTitle }}</strong><p>{{ emptyDescription }}</p>
    </div>

    <div v-else class="asset-grid">
      <article v-for="asset in assets" :key="asset.assetId" class="asset-card">
        <div class="asset-preview">
          <el-image
            v-if="asset.previewObjectKey || asset.objectKey"
            :src="asset.previewObjectKey || asset.objectKey"
            fit="cover"
            :preview-src-list="[asset.objectKey || asset.previewObjectKey]"
            preview-teleported
          />
          <div v-else class="asset-placeholder"><span>{{ placeholder(asset) }}</span></div>
          <span class="status-badge" :class="`status-${String(asset.status || '').toLowerCase()}`">{{ statusLabel(asset) }}</span>
          <span v-if="taskByAssetId[asset.assetId]" class="task-progress">{{ taskLabel(taskByAssetId[asset.assetId]) }}</span>
        </div>
        <div class="asset-body">
          <div class="asset-kicker">{{ assetTypeLabel(asset.assetType) }} · {{ dimensionLabel(asset) }}</div>
          <h3>{{ asset.assetName || `素材 #${asset.assetId}` }}</h3>
          <p>{{ promptPreview(asset) }}</p>
          <div class="asset-actions">
            <el-button v-if="asset.status === 'DRAFT'" type="primary" size="small" @click="$emit('edit', asset)">查看提示词</el-button>
            <el-button v-if="asset.status === 'GENERATED'" type="success" size="small" @click="$emit('approve', asset)">确认使用</el-button>
            <el-button v-if="asset.status === 'REJECTED'" type="warning" size="small" @click="$emit('retry', asset)">重新生成</el-button>
            <el-button v-if="asset.status === 'APPROVED'" text type="primary" size="small" @click="$emit('edit', asset)">查看详情</el-button>
            <el-button v-if="showBinding && asset.assetType === 'SHOT_KEYFRAME'" text type="warning" size="small" @click="$emit('bind', asset)">参考绑定</el-button>
            <el-button text type="danger" size="small" @click="$emit('delete', asset)">删除</el-button>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  eyebrow: { type: String, default: 'Chapter assets' },
  description: { type: String, default: '' },
  assets: { type: Array, default: () => [] },
  taskByAssetId: { type: Object, default: () => ({}) },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  showBinding: { type: Boolean, default: false },
  emptyMark: { type: String, default: '○' },
  emptyTitle: { type: String, default: '还没有素材' },
  emptyDescription: { type: String, default: '完成章节解析后，素材会显示在这里。' }
})

defineEmits(['retry-load', 'edit', 'approve', 'retry', 'delete', 'bind'])

function statusLabel(asset) {
  if (asset.status === 'APPROVED') return '已确认'
  return { DRAFT: '待确认提示词', GENERATING: '生成中', GENERATED: '待确认', REJECTED: '生成失败' }[asset.status] || asset.status || '未知状态'
}

function placeholder(asset) {
  if (asset.status === 'DRAFT') return '提示词待确认'
  if (asset.status === 'GENERATING') return '正在等待图片生成结果…'
  if (asset.status === 'REJECTED') return '生成失败，可重新尝试'
  return '暂无预览'
}

function taskLabel(task) {
  if (task.progressPercent !== undefined && task.progressPercent !== null) return `${task.stageLabel || '处理中'} ${task.progressPercent}%`
  return task.stageLabel || task.status || '处理中'
}

function assetTypeLabel(type) {
  return { SCENE_REFERENCE: '场景参考', CHARACTER_REFERENCE: '人物参考', SHOT_KEYFRAME: '分镜关键帧' }[type] || type
}

function dimensionLabel(asset) {
  return asset.width && asset.height ? `${asset.width} × ${asset.height}` : '尚无尺寸'
}

function promptPreview(asset) {
  return asset.promptText || asset.negativePromptText || '暂未提供提示词内容。'
}
</script>

<style scoped>
.asset-panel { min-width: 0; }
.panel-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; margin-bottom: 18px; }
.panel-heading small { color: #e5904a; font-size: 9px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
.panel-heading h2 { margin: 5px 0 6px; color: #eff2f5; font-size: 23px; letter-spacing: -.025em; }
.panel-heading p { max-width: 60ch; margin: 0; color: #7f8b9b; font-size: 12px; line-height: 1.6; }
.asset-count { color: #e5904a; font-family: Consolas, monospace; font-size: 28px; }
.asset-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.asset-card { min-width: 0; overflow: hidden; border: 1px solid #263140; border-radius: 12px; background: #131b24; transition: border-color .2s ease, transform .2s ease; }
.asset-card:hover { border-color: #3a4656; transform: translateY(-2px); }
.asset-preview { position: relative; display: grid; height: 230px; place-items: center; overflow: hidden; background: radial-gradient(circle at 72% 80%, rgb(229 144 74 / 12%), transparent 45%), #0f161f; }
.asset-preview :deep(.el-image) { width: 100%; height: 100%; }
.asset-placeholder { display: grid; width: 100%; height: 100%; place-items: center; padding: 24px; color: #687587; font-size: 11px; text-align: center; }
.status-badge, .task-progress { position: absolute; top: 10px; padding: 4px 7px; border: 1px solid #394555; border-radius: 5px; color: #c7ced7; background: rgb(15 22 31 / 84%); font-size: 9px; backdrop-filter: blur(8px); }
.status-badge { left: 10px; }
.task-progress { right: 10px; max-width: 55%; overflow: hidden; color: #e9a367; text-overflow: ellipsis; white-space: nowrap; }
.status-approved { color: #94c0a5; }
.status-rejected { color: #e49090; }
.asset-body { padding: 14px; }
.asset-kicker { color: #b67742; font-size: 9px; font-weight: 700; letter-spacing: .05em; }
.asset-body h3 { overflow: hidden; margin: 7px 0 6px; color: #e7ebef; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.asset-body p { display: -webkit-box; min-height: 35px; overflow: hidden; margin: 0; color: #768394; font-size: 10px; line-height: 1.7; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.asset-actions { display: flex; align-items: center; gap: 5px; margin-top: 13px; }
.asset-actions :deep(.el-button + .el-button) { margin-left: 0; }
.asset-actions :deep(.el-button:last-child) { margin-left: auto; }
.panel-state { display: grid; min-height: 360px; place-items: center; align-content: center; padding: 36px; border: 1px dashed #2c3745; border-radius: 12px; color: #aeb7c2; text-align: center; }
.panel-state p { max-width: 34rem; margin: 7px 0 18px; color: #6f7c8d; font-size: 11px; line-height: 1.6; }
.empty-mark { display: grid; width: 62px; height: 62px; place-items: center; margin-bottom: 16px; border: 1px solid #4a3a2e; border-radius: 15px 27px 15px 15px; color: #e5904a; background: rgb(229 144 74 / 7%); font-size: 24px; }
.error-state strong { color: #e39393; }
.skeleton-card span { height: 230px; background: linear-gradient(90deg, #111923 25%, #202a36 38%, #111923 63%); background-size: 400% 100%; animation: shimmer 1.4s ease infinite; }
.skeleton-card div { height: 110px; }
@keyframes shimmer { 0% { background-position: 100% 0; } 100% { background-position: 0 0; } }
@media (max-width: 680px) { .asset-grid { grid-template-columns: 1fr; } .asset-preview { height: 210px; } .panel-heading { align-items: flex-start; } }
</style>
