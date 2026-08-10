<template>
  <div class="task-index">
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">
          <el-icon class="title-icon"><List /></el-icon>
          生成队列
        </h1>
        <p class="page-subtitle">查看章节解析、图片与视频生成任务的实时进度、失败原因与重试状态。</p>
      </div>
      <div class="header-actions">
        <el-button :icon="Refresh" :loading="loading" @click="handleQuery">刷新</el-button>
        <el-button :type="autoRefresh ? 'primary' : 'default'" @click="toggleAutoRefresh">
          {{ autoRefresh ? '自动刷新中' : '自动刷新关闭' }}
        </el-button>
      </div>
    </div>

    <el-card shadow="never" class="main-card">
      <div class="filter-section">
        <el-form :inline="true" :model="queryParams" class="search-form">
          <el-form-item label="任务类型">
            <el-select v-model="queryParams.taskType" clearable placeholder="全部任务类型" style="width: 150px">
              <el-option label="章节分析" value="STORY_BIBLE" />
              <el-option label="图片生成" value="IMAGE" />
              <el-option label="视频生成" value="VIDEO" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="queryParams.status" clearable placeholder="全部状态" style="width: 150px">
              <el-option label="排队中" value="QUEUED" />
              <el-option label="运行中" value="RUNNING" />
              <el-option label="等待回调" value="WAITING_CALLBACK" />
              <el-option label="重试中" value="RETRYING" />
              <el-option label="待审核" value="NEEDS_REVIEW" />
              <el-option label="已暂停" value="PAUSED" />
              <el-option label="成功" value="SUCCEEDED" />
              <el-option label="失败" value="FAILED" />
              <el-option label="已取消" value="CANCELED" />
            </el-select>
          </el-form-item>
          <el-form-item label="项目">
            <el-select v-model="queryParams.projectId" clearable filterable placeholder="全部项目" style="width: 220px">
              <el-option v-for="project in projectOptions" :key="project.projectId" :label="project.projectName" :value="project.projectId" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="handleQuery">查询</el-button>
            <el-button :icon="RefreshLeft" @click="resetQuery">重置</el-button>
          </el-form-item>
          <span class="toolbar-hint">进行中的任务每 5 秒自动刷新</span>
        </el-form>
      </div>

      <el-table
        v-loading="loading"
        :data="taskList"
        row-key="taskId"
        empty-text="暂无符合条件的生成任务"
      >
        <el-table-column label="任务ID" prop="taskId" width="90" align="center">
          <template #default="{ row }">
            <span class="mono">#{{ row.taskId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="任务名称" prop="taskName" min-width="220" show-overflow-tooltip />
        <el-table-column label="类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ taskTypeLabel(row.taskType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="130" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.status)" effect="light">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="170">
          <template #default="{ row }">
            <div class="progress-cell">
              <el-progress :percentage="progressValue(row)" :stroke-width="6" :color="progressColor(row.status)" />
              <span v-if="isActiveStatus(row.status)" class="mono">{{ progressValue(row) }}%</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="供应商" width="110" align="center">
          <template #default="{ row }">
            <span class="muted">{{ providerLabel(row.providerCode) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="错误码" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.errorCode" class="error-code">{{ row.errorCode }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160" align="center">
          <template #default="{ row }">
            <span class="muted">{{ parseTime(row.createTime, '{y}-{m}-{d} {h}:{i}:{s}') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="canRetry(row)"
              size="small"
              type="danger"
              plain
              :loading="retryingTaskId === row.taskId"
              @click="retryTask(row)"
            >重试</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        background
        class="pagination-wrap"
        @size-change="handlePageChange"
        @current-change="handlePageChange"
      />
    </el-card>

    <el-dialog v-model="detailDialog.open" title="任务详情" width="720px" append-to-body>
      <el-descriptions v-if="detailDialog.task" :column="2" border class="task-detail">
        <el-descriptions-item label="任务ID">
          <span class="mono">#{{ detailDialog.task.taskId }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag size="small" :type="statusTagType(detailDialog.task.status)" effect="light">{{ statusLabel(detailDialog.task.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="任务名称" :span="2">{{ detailDialog.task.taskName }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ taskTypeLabel(detailDialog.task.taskType) }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ providerLabel(detailDialog.task.providerCode) }} / {{ detailDialog.task.modelCode || '—' }}</el-descriptions-item>
        <el-descriptions-item label="进度">{{ progressValue(detailDialog.task) }}%</el-descriptions-item>
        <el-descriptions-item label="重试">
          <template v-if="detailDialog.task.taskType === 'IMAGE' || detailDialog.task.taskType === 'VIDEO'">
            {{ detailDialog.task.retryCount || 0 }} / {{ detailDialog.task.maxRetry || 0 }}
            <span v-if="detailDialog.task.nextRetryTime" class="muted">（{{ parseTime(detailDialog.task.nextRetryTime, '{y}-{m}-{d} {h}:{i}:{s}') }}）</span>
          </template>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item v-if="detailDialog.task.providerTaskId" label="供应商任务ID" :span="2">
          <span class="mono">{{ detailDialog.task.providerTaskId }}</span>
        </el-descriptions-item>
        <el-descriptions-item v-if="detailDialog.task.errorCode" label="错误码" :span="2">
          <span class="error-code">{{ detailDialog.task.errorCode }}</span>
        </el-descriptions-item>
        <el-descriptions-item v-if="detailDialog.task.errorMessage" label="错误信息" :span="2">
          <span class="error-message">{{ detailDialog.task.errorMessage }}</span>
        </el-descriptions-item>
      </el-descriptions>
      <div v-if="detailDialog.requestText" class="request-section">
        <div class="section-heading"><h3>请求参数</h3><span>生成时脱敏后的参数快照</span></div>
        <pre>{{ detailDialog.requestText }}</pre>
      </div>
      <template #footer>
        <el-button @click="detailDialog.open = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="AiVedioTaskQueue">
import { Refresh, RefreshLeft, Search, List } from '@element-plus/icons-vue'
import { pageAiVideoTask } from '@/api/aiVedio/task'
import { listAiVideoProject, retryAiVideoAssetImage } from '@/api/aiVedio/project'

const { proxy } = getCurrentInstance()
const loading = ref(false)
const taskList = ref([])
const total = ref(0)
const autoRefresh = ref(true)
const retryingTaskId = ref(null)
const projectOptions = ref([])
const detailDialog = reactive({ open: false, task: null, requestText: '' })

const queryParams = reactive({ pageNum: 1, pageSize: 20, taskType: '', status: '', projectId: null })

const TASK_TYPE_LABELS = {
  PARSE: '文本解析',
  STORY_BIBLE: '章节分析',
  ASSET_PLAN: '资产规划',
  IMAGE: '图片生成',
  VIDEO: '视频生成',
  VOICE: '配音',
  QUALITY: '质检',
  RENDER: '渲染'
}

const STATUS_META = {
  PENDING: { label: '待处理', type: 'info' },
  QUEUED: { label: '排队中', type: 'info' },
  RUNNING: { label: '运行中', type: 'primary' },
  WAITING_CALLBACK: { label: '等待回调', type: 'warning' },
  QUALITY_CHECK: { label: '质检中', type: 'primary' },
  RETRYING: { label: '重试中', type: 'warning' },
  NEEDS_REVIEW: { label: '待审核', type: 'danger' },
  SUBMITTED: { label: '已提交', type: 'primary' },
  PROCESSING: { label: '处理中', type: 'primary' },
  VALIDATING: { label: '校验中', type: 'primary' },
  SUCCEEDED: { label: '成功', type: 'success' },
  FAILED: { label: '失败', type: 'danger' },
  CANCELED: { label: '已取消', type: 'info' },
  PAUSED: { label: '已暂停', type: 'info' }
}

const ACTIVE_STATUSES = new Set([
  'PENDING', 'QUEUED', 'RUNNING', 'WAITING_CALLBACK', 'QUALITY_CHECK',
  'RETRYING', 'SUBMITTED', 'PROCESSING', 'VALIDATING'
])

let refreshTimer = null

function taskTypeLabel(type) {
  return TASK_TYPE_LABELS[type] || type || '—'
}

function statusLabel(status) {
  return STATUS_META[status]?.label || status || '—'
}

function statusTagType(status) {
  return STATUS_META[status]?.type || 'info'
}

function providerLabel(provider) {
  if (!provider) return '—'
  const labels = { happyhorse: 'HappyHorse', dashscope: 'DashScope' }
  return labels[provider] || provider
}

function progressValue(row) {
  const value = Number(row?.progress)
  return Number.isFinite(value) ? Math.max(0, Math.min(100, value)) : 0
}

function progressColor(status) {
  if (status === 'FAILED' || status === 'NEEDS_REVIEW') return '#f56c6c'
  if (status === 'SUCCEEDED') return '#67c23a'
  return '#e6a23c'
}

function isActiveStatus(status) {
  return ACTIVE_STATUSES.has(status)
}

function canRetry(row) {
  return row.taskType === 'IMAGE' && row.status === 'FAILED' && row.assetId
}

function hasActiveTask() {
  return taskList.value.some(task => isActiveStatus(task.status))
}

function loadProjects() {
  listAiVideoProject({ pageNum: 1, pageSize: 200 }).then(response => {
    projectOptions.value = response.rows || []
  }).catch(() => {})
}

function getTaskList() {
  loading.value = true
  pageAiVideoTask(queryParams).then(response => {
    taskList.value = response.rows || []
    total.value = Number(response.total) || 0
    scheduleAutoRefresh()
  }).finally(() => { loading.value = false })
}

function handleQuery() {
  queryParams.pageNum = 1
  getTaskList()
}

function handlePageChange() {
  getTaskList()
}

function resetQuery() {
  queryParams.taskType = ''
  queryParams.status = ''
  queryParams.projectId = null
  queryParams.pageNum = 1
  getTaskList()
}

function toggleAutoRefresh() {
  autoRefresh.value = !autoRefresh.value
  if (autoRefresh.value) {
    scheduleAutoRefresh()
  } else {
    stopAutoRefresh()
  }
}

function scheduleAutoRefresh() {
  stopAutoRefresh()
  if (autoRefresh.value && hasActiveTask()) {
    refreshTimer = setTimeout(() => getTaskList(), 5000)
  }
}

function stopAutoRefresh() {
  if (refreshTimer) {
    clearTimeout(refreshTimer)
    refreshTimer = null
  }
}

function openDetail(row) {
  let requestText = ''
  if (row.requestJson) {
    try {
      requestText = JSON.stringify(typeof row.requestJson === 'string' ? JSON.parse(row.requestJson) : row.requestJson, null, 2)
    } catch (error) {
      requestText = String(row.requestJson)
    }
  }
  detailDialog.task = row
  detailDialog.requestText = requestText
  detailDialog.open = true
}

function retryTask(row) {
  proxy.$modal.confirm(`确认重试图片生成任务“${row.taskName}”吗？将重新调用图片模型。`).then(() => {
    retryingTaskId.value = row.taskId
    return retryAiVideoAssetImage(row.assetId)
  }).then(() => {
    proxy.$modal.msgSuccess('已重新提交图片生成')
    handleQuery()
  }).catch(() => {}).finally(() => {
    retryingTaskId.value = null
  })
}

loadProjects()
getTaskList()
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 16px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.page-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}
.title-icon {
  color: #409eff;
  font-size: 20px;
}
.page-subtitle {
  margin: 2px 0 0;
  font-size: 13px;
  color: #909399;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.filter-section {
  margin-bottom: 16px;
}
.search-form .el-form-item {
  margin-bottom: 12px;
}
.toolbar-hint {
  margin-left: auto;
  font-size: 12px;
  color: #909399;
}
.progress-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.progress-cell .el-progress {
  flex: 1;
}
.mono {
  font-family: "SFMono-Regular", Consolas, monospace;
  font-variant-numeric: tabular-nums;
}
.muted {
  color: #909399;
}
.error-code {
  color: #f56c6c;
  font-size: 12px;
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.error-message {
  color: #f56c6c;
  word-break: break-all;
  line-height: 1.55;
}
.request-section {
  margin-top: 18px;
}
.section-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}
.section-heading h3 {
  margin: 0;
  font-size: 14px;
}
.section-heading span {
  color: #909399;
  font-size: 12px;
}
.request-section pre {
  max-height: 340px;
  margin: 0;
  padding: 14px;
  overflow: auto;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  color: #303133;
  background: #f8f9fb;
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
