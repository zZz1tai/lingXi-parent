<template>
  <div class="app-task-detail">
    <el-card shadow="hover" class="detail-card">
      <template #header>
        <div class="card-header">
          <el-icon><Document /></el-icon>
          <span>工单详情</span>
          <el-button type="primary" link @click="goBack" class="back-button">
            <el-icon><ArrowLeft /></el-icon>
            返回列表
          </el-button>
        </div>
      </template>
      
      <!-- 工单基本信息 -->
      <el-card class="basic-info-card" shadow="hover" v-if="taskInfo">
        <template #header>
          <div class="sub-header">
            <span>基本信息</span>
          </div>
        </template>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="工单编号">{{ taskInfo.taskCode }}</el-descriptions-item>
          <el-descriptions-item label="设备编号">{{ taskInfo.innerCode }}</el-descriptions-item>
          <el-descriptions-item label="工单类型">{{ taskInfo.taskType?.typeName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusType(taskInfo.taskStatus)">
              {{ getStatusText(taskInfo.taskStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="是否维修">{{ taskInfo.isRepair ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ taskInfo.createTime }}</el-descriptions-item>
          <el-descriptions-item label="接受时间">{{ taskInfo.acceptTime || '未接受' }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ taskInfo.completeTime || '未完成' }}</el-descriptions-item>
          <el-descriptions-item label="所属区域">{{ taskInfo.regionName || '未知' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>
      
      <!-- 工单详情列表 -->
      <el-card class="details-list-card" shadow="hover">
        <template #header>
          <div class="sub-header">
            <span>工单详情列表</span>
          </div>
        </template>
        <el-table v-loading="loading" :data="taskDetails" border style="width: 100%">
          <el-table-column prop="skuName" label="商品名称" min-width="180" />
          <el-table-column prop="skuCode" label="商品编码" min-width="150" />
          <el-table-column prop="channelCode" label="货道编码" min-width="120" />
          <el-table-column prop="channelName" label="货道名称" min-width="120" />
          <el-table-column prop="targetCount" label="目标数量" min-width="100" />
          <el-table-column prop="currentCount" label="当前数量" min-width="100" />
          <el-table-column prop="diffCount" label="差异数量" min-width="100" />
          <el-table-column prop="remark" label="备注" min-width="150" />
        </el-table>
        <div v-if="taskDetails.length === 0 && !loading" class="empty-state">
          <el-empty description="暂无工单详情" />
        </div>
      </el-card>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeMount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Document, ArrowLeft } from '@element-plus/icons-vue'
import { getTaskDetails } from '@/api/app/task'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()

// 工单详情数据
const taskDetails = ref([])
const loading = ref(false)
const taskInfo = ref(null)

// 状态映射
const statusMap = {
  1: { text: '待处理', type: 'warning' },
  2: { text: '处理中', type: 'primary' },
  4: { text: '已完成', type: 'success' },
  3: { text: '已取消', type: 'danger' }
}

// 获取状态文本
const getStatusText = (status) => {
  return statusMap[status]?.text || '未知状态'
}

// 获取状态类型
const getStatusType = (status) => {
  return statusMap[status]?.type || 'info'
}

// 获取工单详情
const fetchTaskDetails = async (taskId) => {
  loading.value = true
  try {
    const response = await getTaskDetails(taskId)
    taskDetails.value = response
    // 从详情中提取工单基本信息（假设详情中包含工单信息）
    if (response.length > 0) {
      taskInfo.value = response[0].task || null
    }
  } catch (error) {
    console.error('获取工单详情失败:', error)
    ElMessage.error('获取工单详情失败')
  } finally {
    loading.value = false
  }
}

// 返回列表
const goBack = () => {
  router.push('/app/task')
}

// 监听路由参数变化
onBeforeMount(() => {
  const taskId = route.params.id
  if (taskId) {
    fetchTaskDetails(taskId)
  }
})

onMounted(() => {
  const taskId = route.params.id
  if (taskId) {
    fetchTaskDetails(taskId)
  }
})
</script>

<style scoped>
.app-task-detail {
  padding: 20px;
  background-color: #f5f7fa;
  min-height: 100vh;
}

.detail-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
}

.sub-header {
  font-size: 14px;
  font-weight: 600;
}

.basic-info-card {
  margin-bottom: 20px;
}

.details-list-card {
  margin-bottom: 20px;
}

.back-button {
  margin-left: auto;
}

.empty-state {
  margin-top: 20px;
  text-align: center;
}
</style>
