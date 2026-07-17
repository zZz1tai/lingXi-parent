<template>
  <div class="box abnormal-equipment">
    <div class="header">
      <div class="title">异常设备监控</div>
      <el-icon @click="handleMore">
        <MoreFilled />
      </el-icon>
    </div>
    <el-skeleton v-if="loading" :rows="4" animated style="margin-top: 20px" />
    <div v-else-if="listData.length" class="table-container body">
      <el-table :data="listData" fit highlight-current-row style="width: 100%" :header-cell-style="{
        padding: '7px 0 6px',
        background: '#EFF0F2',
        'font-weight': '400',
        'text-align': 'left',
        color: '#333333',
      }" :cell-style="{
          padding: '15px 0',
          'text-align': 'left',
          color: '#999999',
        }">
        <el-table-column label="故障时间" min-width="160">
          <template #default="scope">
            <span>{{ scope.row.updateTime }}</span>
          </template>
        </el-table-column>
        <el-table-column label="设备地址" show-overflow-tooltip min-width="200">
          <template #default="scope">
            <span>{{ scope.row.addr }}</span>
          </template>
        </el-table-column>
        <el-table-column label="设备编号" min-width="120">
          <template #default="scope">
            <span>{{ scope.row.innerCode }}</span>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <!-- TODO：一开始显示加载中 -->
    <div v-else class="empty">
      <img src="@/assets/images/empty.png" />
      <div class="tips">暂无数据</div>
    </div>
  </div>
</template>
<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { MoreFilled } from '@element-plus/icons-vue';
import { getAbnormalEquipment } from '@/api/manage/dashboard';

// 定义变量
const router = useRouter();
const listData = ref([]);
const loading = ref(true);

// 获取异常设备列表
const fetchAbnormalEquipment = async () => {
  try {
    loading.value = true;
    const response = await getAbnormalEquipment();
    listData.value = response.data || [];
  } catch (error) {
    console.error('获取异常设备列表失败:', error);
    listData.value = [];
  } finally {
    loading.value = false;
  }
};

// 点击更多
const handleMore = () => {
  router.push({ path: 'vm/machine' });
};

// 组件挂载时获取数据
onMounted(() => {
  fetchAbnormalEquipment();
});
</script>
<style lang="scss" scoped>
@import '@/assets/styles/variables.module.scss';

.abnormal-equipment {
  display: flex;
  flex-direction: column;
  height: calc((100vh - 120px) * 0.4);
  min-height: 353px;
  background: #ffffff;
  border-radius: 20px;

  .more {
    color: $--color-primary;
    cursor: pointer;
  }

  .empty {
    flex: 1;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;

    .tips {
      margin-top: 25px;
      color: #20232a;
      font-size: 14px;
    }
  }

  .body {
    flex: 1;
    margin-top: 20px;
    overflow: auto;
  }

  .table-container {
    height: 100%;
    overflow: auto;
    min-width: 0; // 防止 flex 子元素溢出

    :deep(.el-table) {
      height: 100%;
      display: flex;
      flex-direction: column;
      min-width: 480px; // 设置表格最小宽度，防止过度压缩

      .el-table__header-wrapper {
        flex-shrink: 0;
      }

      .el-table__body-wrapper {
        flex: 1;
        overflow: auto;
      }

      .el-table__header,
      .el-table__body {
        width: 100% !important;
      }

      .el-table__header th,
      .el-table__body td {
        white-space: nowrap;
      }
    }
  }
}
</style>