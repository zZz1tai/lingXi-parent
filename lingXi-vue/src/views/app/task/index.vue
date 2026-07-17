<template>
  <div class="app-task-index">
    <!-- 顶部标题和排名 -->
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">
          <el-icon class="title-icon"><Ticket /></el-icon>
          工单处理中心
        </h1>
        <p class="page-subtitle">高效处理您的工单任务</p>
      </div>
      <!-- <el-card class="rank-widget" shadow="hover">
        <div class="rank-widget-content">
          <el-icon class="rank-icon"><Trophy /></el-icon>
          <div class="rank-info">
            <div class="rank-label">我的排名</div>
            <div class="rank-value" v-if="rankInfo">
              <span class="rank-number">{{ rankInfo.rank }}</span>
              <span class="rank-divider">/</span>
              <span class="rank-total">{{ rankInfo.total }}</span>
            </div>
            <div v-else class="rank-loading">
              <el-skeleton-item variant="text" style="width: 60px" />
            </div>
          </div>
        </div>
      </el-card> -->
    </div>

    <!-- 主内容区域 -->
    <el-card shadow="never" class="main-card">
      <!-- 搜索和筛选区域 -->
      <div class="filter-section">
        <div class="filter-header">
          <el-icon><Filter /></el-icon>
          <span>筛选条件</span>
        </div>
        <el-form :inline="true" :model="searchForm" class="search-form">
          <div class="form-row">
            <el-form-item label="设备编号" class="form-item">
              <el-input
                v-model="searchForm.innerCode"
                placeholder="请输入设备编号"
                clearable
                class="input-with-icon"
              >
                <template #prefix>
                  <el-icon><Monitor /></el-icon>
                </template>
              </el-input>
            </el-form-item>
            
            <el-form-item label="工单编号" class="form-item">
              <el-input
                v-model="searchForm.taskCode"
                placeholder="请输入工单编号"
                clearable
                class="input-with-icon"
              >
                <template #prefix>
                  <el-icon><Document /></el-icon>
                </template>
              </el-input>
            </el-form-item>
            
            <el-form-item label="工单状态" class="form-item">
              <el-select
                v-model="searchForm.status"
                placeholder="请选择状态"
                clearable
                class="status-select"
              >
                <el-option label="待处理" value="1">
                  <span class="status-option">
                    <span class="status-dot dot-warning"></span>
                    待处理
                  </span>
                </el-option>
                <el-option label="处理中" value="2">
                  <span class="status-option">
                    <span class="status-dot dot-primary"></span>
                    处理中
                  </span>
                </el-option>
                <el-option label="已取消" value="3">
                  <span class="status-option">
                    <span class="status-dot dot-danger"></span>
                    已取消
                  </span>
                </el-option>
                <el-option label="已完成" value="4">
                  <span class="status-option">
                    <span class="status-dot dot-success"></span>
                    已完成
                  </span>
                </el-option>
              </el-select>
            </el-form-item>
          </div>
          
          <div class="form-row">
            <el-form-item label="工单类型" class="form-item">
              <el-select
                v-model="searchForm.productTypeId"
                placeholder="请选择"
                clearable
              >
                <el-option label="投放工单" value="1" />
                <el-option label="补货工单" value="2" />
                <el-option label="维修工单" value="3" />
                <el-option label="撤机工单" value="4" />
              </el-select>
            </el-form-item>
            
            <el-form-item label="创建时间" class="form-item date-range">
              <el-date-picker
                v-model="searchForm.timeRange"
                type="daterange"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                @change="handleDateChange"
                clearable
              />
            </el-form-item>
            
            <div class="form-actions">
              <el-button
                type="primary"
                @click="handleSearch"
                class="search-btn"
              >
                <el-icon><Search /></el-icon>
                搜索
              </el-button>
              <el-button
                @click="resetForm"
                class="reset-btn"
              >
                <el-icon><Refresh /></el-icon>
                重置
              </el-button>
            </div>
          </div>
        </el-form>
      </div>

      <!-- 工单列表 -->
      <div class="table-section">
        <div class="table-header">
          <div class="table-title">
            <el-icon><List /></el-icon>
            <span>工单列表</span>
            <el-tag v-if="pagination.total" type="info" size="small">
              共 {{ pagination.total }} 条记录
            </el-tag>
          </div>
          <div class="table-actions">
            <el-button
              type="text"
              @click="refreshData"
              :loading="loading"
            >
              <el-icon><RefreshRight /></el-icon>
              刷新
            </el-button>
          </div>
        </div>
        
        <el-table
          v-loading="loading"
          :data="taskList"
          style="width: 100%"
          :header-cell-style="{ background: '#f8f9fa', color: '#606266' }"

          class="task-table"
        >
          <el-table-column prop="taskCode" label="工单编号" min-width="180">
            <template #default="scope">
              <div class="task-code">
                <el-icon><DocumentCopy /></el-icon>
                {{ scope.row.taskCode }}
              </div>
            </template>
          </el-table-column>
          
          <el-table-column prop="innerCode" label="设备编号" min-width="150">
            <template #default="scope">
              <div class="inner-code">
                <el-icon><Cpu /></el-icon>
                {{ scope.row.innerCode }}
              </div>
            </template>
          </el-table-column>
          
          <el-table-column label="工单类型" min-width="120">
            <template #default="scope">
              {{ scope.row.taskType?.typeName || '-' }}
            </template>
          </el-table-column>
          
          <el-table-column label="状态" min-width="120">
            <template #default="scope">
              <el-tag
                :type="getStatusType(scope.row.taskStatus)"
                size="small"
                :class="`status-tag status-${scope.row.taskStatus}`"
              >
                <el-icon v-if="scope.row.taskStatus === 1"><Clock /></el-icon>
                <el-icon v-if="scope.row.taskStatus === 2"><Loading /></el-icon>
                <el-icon v-if="scope.row.taskStatus === 4"><CircleCheck /></el-icon>
                <el-icon v-if="scope.row.taskStatus === 3"><CircleClose /></el-icon>
                {{ getStatusText(scope.row.taskStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          
          <el-table-column label="时间信息" min-width="280">
            <template #default="scope">
              <div class="time-info">
                <div class="time-item">
                  <span class="time-label">创建：</span>
                  <span class="time-value">{{ formatTime(scope.row.createTime) }}</span>
                </div>
                <div class="time-item" v-if="scope.row.acceptTime">
                  <span class="time-label">接受：</span>
                  <span class="time-value">{{ formatTime(scope.row.acceptTime) }}</span>
                </div>
                <div class="time-item" v-if="scope.row.completeTime">
                  <span class="time-label">完成：</span>
                  <span class="time-value">{{ formatTime(scope.row.completeTime) }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          
          <el-table-column label="操作" min-width="200" fixed="right">
            <template #default="scope">
              <div class="action-buttons" @click.stop>
                <el-button
                  v-if="scope.row.taskStatus === 1"
                  type="warning"
                  size="small"
                  @click="handleAccept(scope.row.taskId)"
                  class="action-btn"
                >
                  <el-icon><Check /></el-icon>
                  接受
                </el-button>
                
                <el-button
                  v-if="scope.row.taskStatus === 2"
                  type="success"
                  size="small"
                  @click="handleComplete(scope.row.taskId)"
                  class="action-btn"
                >
                  <el-icon><Finished /></el-icon>
                  完成
                </el-button>
                
                <el-button
                  v-if="scope.row.taskStatus === 1 || scope.row.taskStatus === 2"
                  type="danger"
                  size="small"
                  @click="handleCancel(scope.row.taskId)"
                  class="action-btn"
                >
                  <el-icon><Close /></el-icon>
                  取消
                </el-button>
                
                <el-button
                  type="info"
                  size="small"
                  @click="handleDetail(scope.row.taskId)"
                  class="action-btn"
                >
                  <el-icon><View /></el-icon>
                  详情
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
        
        <!-- 分页 -->
        <div class="pagination-container">
          <el-pagination
            v-model:current-page="pagination.pageIndex"
            v-model:page-size="pagination.pageSize"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            :total="pagination.total"
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
            class="custom-pagination"
          />
        </div>
      </div>
    </el-card>
    
    <!-- 取消工单弹窗 -->
    <el-dialog
      v-model="cancelDialogVisible"
      title="取消工单"
      width="500px"
      class="cancel-dialog"
    >
      <div class="dialog-content">
        <el-alert
          type="warning"
          :closable="false"
          class="alert-warning"
        >
          <template #title>
            确定要取消该工单吗？此操作无法撤销。
          </template>
        </el-alert>
        
        <el-form :model="cancelForm" label-width="80px" class="cancel-form">
          <el-form-item label="取消原因" required>
            <el-input
              v-model="cancelForm.reason"
              type="textarea"
              :rows="4"
              placeholder="请详细说明取消原因，至少输入10个字符"
              maxlength="500"
              show-word-limit
              class="reason-textarea"
            />
          </el-form-item>
        </el-form>
      </div>
      
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="cancelDialogVisible = false" class="cancel-btn">
            放弃取消
          </el-button>
          <el-button
            type="primary"
            @click="confirmCancel"
            :disabled="!cancelForm.reason.trim() || cancelForm.reason.trim().length < 10"
            class="confirm-btn"
          >
            确认取消
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { 
  Ticket, Search, Refresh, Filter, Monitor, Document,
  List, DocumentCopy, Cpu, Clock, Loading, CircleCheck,
  CircleClose, Check, Finished, Close, View, RefreshRight,
  Trophy
} from '@element-plus/icons-vue'
import { 
  searchTasks, getUserRank, acceptTask, cancelTask, completeTask 
} from '@/api/app/task'
import useUserStore from '@/store/modules/user'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()

// 搜索表单
const searchForm = reactive({
  innerCode: '',
  taskCode: '',
  status: '',
  productTypeId: '',
  timeRange: [],
  start: '',
  end: ''
})

// 分页信息
const pagination = reactive({
  pageIndex: 1,
  pageSize: 10,
  total: 0
})

// 列表数据
const taskList = ref([])
const loading = ref(false)

// 排名信息
const rankInfo = ref(null)

// 取消工单弹窗
const cancelDialogVisible = ref(false)
const currentTaskId = ref(null)
const cancelForm = reactive({
  reason: ''
})

// 状态映射
const statusMap = {
  1: { text: '待处理', type: 'warning', icon: 'Clock' },
  2: { text: '处理中', type: 'primary', icon: 'Loading' },
  4: { text: '已完成', type: 'success', icon: 'CircleCheck' },
  3: { text: '已取消', type: 'danger', icon: 'CircleClose' }
}

// 获取状态文本
const getStatusText = (status) => {
  return statusMap[status]?.text || '未知状态'
}

// 获取状态类型
const getStatusType = (status) => {
  return statusMap[status]?.type || 'info'
}

// 格式化时间
const formatTime = (time) => {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).replace(/\//g, '-')
}

// 处理日期变化
const handleDateChange = (dates) => {
  if (dates && dates.length === 2) {
    searchForm.start = dates[0].toISOString().split('T')[0]
    searchForm.end = dates[1].toISOString().split('T')[0]
  } else {
    searchForm.start = ''
    searchForm.end = ''
  }
}

// 刷新数据
const refreshData = () => {
  fetchTasks()
  fetchUserRank()
}

// 搜索工单
const handleSearch = () => {
  pagination.pageIndex = 1
  fetchTasks()
}

// 重置表单
const resetForm = () => {
  Object.assign(searchForm, {
    innerCode: '',
    taskCode: '',
    status: '',
    productTypeId: '',
    timeRange: [],
    start: '',
    end: ''
  })
  pagination.pageIndex = 1
  fetchTasks()
}

// 获取工单列表
const fetchTasks = async () => {
  loading.value = true
  try {
    const params = {
      ...searchForm,
      pageIndex: pagination.pageIndex,
      pageSize: pagination.pageSize
    }
    // 移除空的参数
    Object.keys(params).forEach(key => {
      if (params[key] === '' || params[key] === null || params[key] === undefined) {
        delete params[key]
      }
    })
    
    const response = await searchTasks(params)
    taskList.value = response.list || []
    pagination.total = Number(response.total) || 0
  } catch (error) {
    console.error('获取工单列表失败:', error)
    ElMessage.error('获取工单列表失败')
  } finally {
    loading.value = false
  }
}

// 获取用户排名
const fetchUserRank = async () => {
  try {
    const userId = userStore.id
    if (!userId) {
      console.error('获取用户排名失败: 用户ID无效')
      return
    }
    const response = await getUserRank(userId)
    rankInfo.value = response
  } catch (error) {
    console.error('获取用户排名失败:', error)
  }
}

// 接受工单
const handleAccept = async (taskId) => {
  try {
    const result = await acceptTask(taskId)
    if (result) {
      ElMessage.success('接受工单成功')
      fetchTasks()
    } else {
      ElMessage.error('接受工单失败')
    }
  } catch (error) {
    console.error('接受工单失败:', error)
    //ElMessage.error('接受工单失败')
  }
}

// 取消工单
const handleCancel = (taskId) => {
  currentTaskId.value = taskId
  cancelForm.reason = ''
  cancelDialogVisible.value = true
}

// 确认取消
const confirmCancel = async () => {
  if (!cancelForm.reason.trim()) {
    ElMessage.warning('请输入取消原因')
    return
  }
  
  if (cancelForm.reason.trim().length < 10) {
    ElMessage.warning('取消原因至少需要10个字符')
    return
  }
  
  try {
    const result = await cancelTask(currentTaskId.value, cancelForm)
    if (result) {
      ElMessage.success('取消工单成功')
      cancelDialogVisible.value = false
      fetchTasks()
    } else {
      ElMessage.error('取消工单失败')
    }
  } catch (error) {
    console.error('取消工单失败:', error)
    ElMessage.error('取消工单失败')
  }
}

// 完成工单
const handleComplete = async (taskId) => {
  try {
    const result = await completeTask(taskId)
    if (result) {
      ElMessage.success('完成工单成功')
      fetchTasks()
    } else {
      ElMessage.error('完成工单失败')
    }
  } catch (error) {
    console.error('完成工单失败:', error)
    ElMessage.error('完成工单失败')
  }
}

// 查看详情
const handleDetail = (taskId) => {
  router.push(`/app/task/detail/${taskId}`)
}

// 分页大小变化
const handleSizeChange = (size) => {
  pagination.pageSize = size
  fetchTasks()
}

// 当前页变化
const handleCurrentChange = (current) => {
  pagination.pageIndex = current
  fetchTasks()
}

// 初始化数据
onMounted(() => {
  fetchTasks()
  fetchUserRank()
})
</script>

<style scoped lang="scss">
.app-task-index {
  padding: 24px;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4e7ed 100%);
  min-height: 100vh;
}

// 页面头部
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
  
  .header-left {
    .page-title {
      display: flex;
      align-items: center;
      gap: 12px;
      margin: 0 0 8px 0;
      font-size: 28px;
      font-weight: 600;
      color: #303133;
      
      .title-icon {
        font-size: 32px;
        color: #409eff;
      }
    }
    
    .page-subtitle {
      margin: 0;
      font-size: 14px;
      color: #909399;
    }
  }
  
  .rank-widget {
    width: 220px;
    border: none;
    border-radius: 12px;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    
    .rank-widget-content {
      display: flex;
      align-items: center;
      gap: 16px;
      color: white;
      
      .rank-icon {
        font-size: 32px;
      }
      
      .rank-info {
        .rank-label {
          font-size: 14px;
          opacity: 0.9;
          margin-bottom: 4px;
        }
        
        .rank-value {
          display: flex;
          align-items: baseline;
          gap: 4px;
          
          .rank-number {
            font-size: 32px;
            font-weight: 700;
          }
          
          .rank-divider {
            font-size: 18px;
            opacity: 0.7;
          }
          
          .rank-total {
            font-size: 16px;
            opacity: 0.7;
          }
        }
      }
    }
  }
}

// 主卡片
.main-card {
  border: none;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
  background: white;
  
  // 筛选区域
  .filter-section {
    margin-bottom: 24px;
    padding-bottom: 24px;
    border-bottom: 1px solid #f0f0f0;
    
    .filter-header {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 20px;
      font-size: 16px;
      font-weight: 600;
      color: #303133;
      
      .el-icon {
        color: #409eff;
      }
    }
    
    .search-form {
      .form-row {
        display: flex;
        flex-wrap: wrap;
        gap: 20px;
        margin-bottom: 20px;
        
        &:last-child {
          margin-bottom: 0;
        }
        
        .form-item {
          margin: 0;
          
          :deep(.el-form-item__label) {
            font-weight: 500;
            color: #606266;
          }
          
          &.date-range {
            flex: 1;
            min-width: 300px;
          }
        }
        
        .form-actions {
          display: flex;
          align-items: flex-end;
          gap: 12px;
          margin-left: auto;
          
          .search-btn {
            padding: 10px 24px;
            border-radius: 8px;
          }
          
          .reset-btn {
            padding: 10px 24px;
            border-radius: 8px;
            color: #606266;
            border-color: #dcdfe6;
          }
        }
      }
      
      .input-with-icon {
        :deep(.el-input__prefix) {
          display: flex;
          align-items: center;
          padding-left: 8px;
        }
      }
      
      .status-select {
        :deep(.status-option) {
          display: flex;
          align-items: center;
          gap: 8px;
          
          .status-dot {
            width: 8px;
            height: 8px;
            border-radius: 50%;
            
            &.dot-warning {
              background: #e6a23c;
            }
            
            &.dot-primary {
              background: #409eff;
            }
            
            &.dot-success {
              background: #67c23a;
            }
            
            &.dot-danger {
              background: #f56c6c;
            }
          }
        }
      }
    }
  }
  
  // 表格区域
  .table-section {
    .table-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 20px;
      
      .table-title {
        display: flex;
        align-items: center;
        gap: 12px;
        font-size: 18px;
        font-weight: 600;
        color: #303133;
        
        .el-icon {
          color: #409eff;
        }
      }
      
      .table-actions {
        .el-button {
          color: #409eff;
          
          &:hover {
            background: #f5f7fa;
          }
        }
      }
    }
    
    .task-table {
      border-radius: 8px;
      overflow: hidden;
      
      :deep(.el-table__header) {
        th {
          font-weight: 600;
        }
      }
      
      :deep(.el-table__row) {
        &:hover {
          background-color: #fafafa;
        }
        
        td {
          padding: 16px 0;
        }
      }
      
      .task-code, .inner-code {
        display: flex;
        align-items: center;
        gap: 8px;
        
        .el-icon {
          color: #909399;
        }
      }
      
      .status-tag {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        padding: 4px 12px;
        border-radius: 20px;
        font-weight: 500;
        
        &.status-0 {
          background: #fdf6ec;
          border-color: #f5dab1;
        }
        
        &.status-1 {
          background: #ecf5ff;
          border-color: #b3d8ff;
        }
        
        &.status-2 {
          background: #f0f9eb;
          border-color: #c2e7b0;
        }
        
        &.status-3 {
          background: #fef0f0;
          border-color: #fbc4c4;
        }
      }
      
      .time-info {
        .time-item {
          display: flex;
          gap: 8px;
          margin-bottom: 4px;
          font-size: 13px;
          
          &:last-child {
            margin-bottom: 0;
          }
          
          .time-label {
            color: #909399;
            min-width: 40px;
          }
          
          .time-value {
            color: #606266;
            flex: 1;
          }
        }
      }
      
      .action-buttons {
        display: flex;
        gap: 8px;
        flex-wrap: wrap;
        
        .action-btn {
          display: inline-flex;
          align-items: center;
          gap: 4px;
          border-radius: 6px;
          padding: 6px 12px;
          font-size: 13px;
          
          .el-icon {
            font-size: 14px;
          }
        }
      }
    }
    
    // 分页
    .pagination-container {
      margin-top: 24px;
      display: flex;
      justify-content: flex-end;
      
      .custom-pagination {
        :deep(.el-pagination__total),
        :deep(.el-pagination__jump) {
          color: #606266;
        }
        
        :deep(.el-pagination__sizes) {
          margin-right: 16px;
        }
      }
    }
  }
}

// 取消工单弹窗
.cancel-dialog {
  :deep(.el-dialog) {
    border-radius: 12px;
    
    .el-dialog__header {
      padding: 20px 24px;
      border-bottom: 1px solid #f0f0f0;
      margin: 0;
      
      .el-dialog__title {
        font-weight: 600;
        color: #303133;
      }
    }
    
    .el-dialog__body {
      padding: 24px;
    }
    
    .el-dialog__footer {
      padding: 20px 24px;
      border-top: 1px solid #f0f0f0;
    }
  }
  
  .dialog-content {
    .alert-warning {
      margin-bottom: 20px;
      border-radius: 8px;
      border: none;
      background: #fdf6ec;
    }
    
    .cancel-form {
      :deep(.el-form-item__label) {
        font-weight: 500;
        color: #606266;
      }
      
      .reason-textarea {
        :deep(.el-textarea__inner) {
          border-radius: 8px;
          border: 1px solid #e4e7ed;
          transition: all 0.3s;
          
          &:focus {
            border-color: #409eff;
            box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.1);
          }
        }
      }
    }
  }
  
  .dialog-footer {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
    
    .cancel-btn {
      padding: 10px 24px;
      border-radius: 8px;
      color: #606266;
      border-color: #dcdfe6;
    }
    
    .confirm-btn {
      padding: 10px 24px;
      border-radius: 8px;
      
      &:disabled {
        opacity: 0.6;
        cursor: not-allowed;
      }
    }
  }
}

// 响应式设计
@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    gap: 16px;
    
    .rank-widget {
      width: 100%;
    }
  }
  
  .search-form {
    .form-row {
      .form-item {
        width: 100%;
        
        &.date-range {
          min-width: 100% !important;
        }
      }
      
      .form-actions {
        width: 100%;
        justify-content: flex-start;
        margin-top: 12px;
      }
    }
  }
  
  .action-buttons {
    flex-direction: column;
    width: 100%;
    
    .action-btn {
      width: 100%;
      justify-content: center;
    }
  }
}
</style>