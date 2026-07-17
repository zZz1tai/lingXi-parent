<template>
  <div class="app-container">
    <div class="page-header">
      <div class="title">
        <i class="el-icon-s-goods" />
        <span>智能推荐商品</span>
      </div>
      <div class="sub-title">基于5维度加权打分的混合推荐算法</div>
    </div>

    <!-- 筛选条件 -->
    <div class="card search-card">
      <div class="card-title">
        <i class="el-icon-filter" />
        <span>查询条件</span>
      </div>
      <el-form :model="queryParams" inline label-width="100px">
        <el-form-item label="用户名">
          <el-input v-model="queryParams.userName" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="设备编号">
          <el-input v-model="queryParams.innerCode" placeholder="请输入设备编号" />
        </el-form-item>
        <el-form-item label="区域ID">
          <el-input v-model="queryParams.regionId" placeholder="请输入区域ID" />
        </el-form-item>
        <el-form-item label="推荐数量">
          <el-input-number v-model="queryParams.limit" :min="1" :max="20" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchRecommendations">获取推荐</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 推荐商品列表 -->
    <div class="card table-card">
      <div class="table-header">
        <div class="left">
          <i class="el-icon-document" />
          <span class="title">推荐商品列表</span>
          <span class="count">共 {{ recommendations.length }} 个商品</span>
        </div>
      </div>

      <el-table v-loading="loading" :data="recommendations" border style="width: 100%">
        <el-table-column label="排名" type="index" width="80" align="center" />
        <el-table-column label="商品ID" prop="skuId" align="center" width="100" />
        <el-table-column label="商品名称" prop="skuName" align="center" show-overflow-tooltip />
        <el-table-column label="商品价格" align="center" width="120">
          <template #default="scope">
            ¥{{ (scope.row.price / 100).toFixed(2) }}
          </template>
        </el-table-column>
        <el-table-column label="商品图片" align="center" width="100">
          <template #default="scope">
            <el-image 
              v-if="scope.row.imageUrl" 
              :src="scope.row.imageUrl" 
              :preview-src-list="[scope.row.imageUrl]"
              fit="cover"
              style="width: 50px; height: 50px;"
            />
            <span v-else>暂无图片</span>
          </template>
        </el-table-column>
        <el-table-column label="推荐分数" prop="score" align="center" width="100" />
        <el-table-column label="推荐原因" align="center" show-overflow-tooltip>
          <template #default="scope">
            <el-tag v-for="(reason, index) in scope.row.reasons" :key="index" size="small" style="margin: 2px;">
              {{ reason }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="120">
          <template #default="scope">
            <el-button type="primary" link @click="handleAddToCart(scope.row)">
              加入购物车
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="!loading && recommendations.length === 0" class="empty-state">
        <el-empty description="暂无推荐商品" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getHybridRecommendations } from '@/api/manage/order'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const recommendations = ref([])

const queryParams = reactive({
  userName: '',
  innerCode: '',
  regionId: '',
  limit: 10
})

// 获取推荐商品
async function fetchRecommendations() {
  if (!queryParams.userName) {
    ElMessage.warning('请输入用户名')
    return
  }

  loading.value = true
  try {
    const res = await getHybridRecommendations(queryParams)
    recommendations.value = res.data || []
  } catch (error) {
    console.error('获取推荐失败:', error)
    ElMessage.error('获取推荐商品失败')
  } finally {
    loading.value = false
  }
}

// 重置查询
function resetQuery() {
  queryParams.userName = ''
  queryParams.innerCode = ''
  queryParams.regionId = ''
  queryParams.limit = 10
  recommendations.value = []
}

// 加入购物车
function handleAddToCart(item) {
  ElMessage.success(`已将 ${item.skuName} 加入购物车`)
}

// 页面加载时获取推荐
onMounted(() => {
  // 可以在这里根据登录用户自动获取推荐
  // fetchRecommendations()
})
</script>

<style scoped>
.app-container {
  padding: 20px;
}

.page-header {
  margin-bottom: 20px;
}

.page-header .title {
  font-size: 20px;
  font-weight: bold;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
}

.page-header .sub-title {
  font-size: 14px;
  color: #909399;
  margin-top: 5px;
}

.card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 20px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.card-title {
  font-size: 16px;
  font-weight: 500;
  margin-bottom: 15px;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.table-header .left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.table-header .count {
  color: #909399;
  font-size: 14px;
}

.empty-state {
  padding: 40px 0;
}
</style>