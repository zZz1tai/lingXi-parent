<template>
  <div class="app-emp-profile">
    <el-card class="profile-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <el-icon><User /></el-icon>
          <span>员工信息</span>
        </div>
      </template>
      <div class="profile-content" v-if="empInfo">
        <div class="profile-avatar">
          <el-avatar :size="120" :src="empInfo.image || defaultAvatar" />
        </div>
        <div class="profile-info">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="用户名">{{ empInfo.userName }}</el-descriptions-item>
            <el-descriptions-item label="登录名">{{ empInfo.loginName }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ empInfo.mobile }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="empInfo.status === 1 ? 'success' : 'danger'">
                {{ empInfo.status === 1 ? '启用' : '禁用' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="角色名称">{{ empInfo.roleName }}</el-descriptions-item>
            <el-descriptions-item label="角色代码">{{ empInfo.roleCode }}</el-descriptions-item>
            <el-descriptions-item label="区域ID">{{ empInfo.regionId }}</el-descriptions-item>
            <el-descriptions-item label="区域名称">{{ empInfo.regionName }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </div>
      <div v-else class="empty-state">
        <el-empty description="暂无员工信息" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { User } from '@element-plus/icons-vue'
import { getEmpInfo } from '@/api/app/emp'
import useUserStore from '@/store/modules/user'

const userStore = useUserStore()
const empInfo = ref(null)
const defaultAvatar = 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'

const fetchEmpInfo = async () => {
  try {
    const userId = userStore.userId
    const response = await getEmpInfo(userId)
    empInfo.value = response
  } catch (error) {
    console.error('获取员工信息失败:', error)
  }
}

onMounted(() => {
  fetchEmpInfo()
})
</script>

<style scoped>
.app-emp-profile {
  padding: 20px;
  background-color: #f5f7fa;
  min-height: 100vh;
}

.profile-card {
  max-width: 800px;
  margin: 0 auto;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
}

.profile-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 30px;
}

.profile-avatar {
  margin-top: 20px;
}

.profile-info {
  width: 100%;
}

.empty-state {
  text-align: center;
  padding: 50px 0;
}

@media (min-width: 768px) {
  .profile-content {
    flex-direction: row;
    align-items: flex-start;
  }
  
  .profile-avatar {
    margin-top: 0;
  }
}
</style>
