<template>
  <div class="box partner-node-collect">
    <div class="header">
      <div class="title">合作商点位数Top5</div>
      <svg-icon name="more" class="more" @click="handleMoreClick" />
    </div>
    <el-row :gutter="20" type="flex" align="middle" class="body">
      <el-col :span="17">
        <el-skeleton v-if="loading" :rows="1" animated style="width: 100%; height: 250px" />
        <partner-node-collect-pie-chart v-else :chart-option="pieChartOption" />
      </el-col>
      <el-col :span="7">
        <el-skeleton v-if="loading" :rows="4" animated />
        <div v-else class="collect">
          <div class="count">
            {{ totalNodes }}
          </div>
          <div class="name">点位数</div>
          <div class="count count2">
            {{ totalPartners }}
          </div>
          <div class="name">合作商数</div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>
<script setup>
import { ref, onMounted } from 'vue';
import PartnerNodeCollectPieChart from './partner-node-collect-pie-chart.vue';
import { getPartnerNodeCollect } from '@/api/manage/dashboard';

// 定义变量
const loading = ref(true);
const totalNodes = ref(0);
const totalPartners = ref(0);
const pieChartOption = ref({
  seriesData: [],
});

// 获取合作商节点汇总数据
const fetchPartnerNodeCollect = async () => {
  try {
    loading.value = true;
    const response = await getPartnerNodeCollect();
    const data = response.data || {};
    
    pieChartOption.value = {
      seriesData: data.seriesData || [],
    };
    
    totalNodes.value = data.totalNodes || 0;
    totalPartners.value = data.totalPartners || 0;
  } catch (error) {
    console.error('获取合作商节点汇总数据失败:', error);
    pieChartOption.value = {
      seriesData: [],
    };
    totalNodes.value = 0;
    totalPartners.value = 0;
  } finally {
    loading.value = false;
  }
};

// 处理更多点击事件
const handleMoreClick = () => {
  // 这里可以添加跳转到更多详情页面的逻辑
  console.log('查看更多合作商节点数据');
};

// 组件挂载时获取数据
onMounted(() => {
  fetchPartnerNodeCollect();
});
</script>
<style scoped>
.partner-node-collect {
  display: -webkit-box;
  display: -ms-flexbox;
  display: flex;
  -webkit-box-orient: vertical;
  -webkit-box-direction: normal;
  -ms-flex-direction: column;
  flex-direction: column;
  height: calc(40vh - 48px);
  min-height: 353px;
  background: #fff;
  border-radius: 20px
}

.partner-node-collect .body {
  -webkit-box-flex: 1;
  -ms-flex: 1;
  flex: 1
}

.partner-node-collect .body .chart {
  padding-top: 16px
}

.partner-node-collect .body .collect {
  width: 154px;
  height: 230px;
  padding-top: 47px;
  padding-left: 38px;
  background: linear-gradient(135deg, transparent, #f8f8f9 0) 0 0, linear-gradient(-135deg, transparent 12px, #f8f8f9 0) 100% 0, linear-gradient(-45deg, transparent, #f8f8f9 0) 100% 100%, linear-gradient(45deg, transparent 12px, #f8f8f9 0) 0 100%;
  background-size: 50% 50%;
  background-repeat: no-repeat
}

.partner-node-collect .body .collect .count {
  height: 33px;
  font-size: 24px;
  font-family: PingFangSC-Semibold, PingFang SC;
  font-weight: 600;
  color: #072074;
  line-height: 33px
}

.partner-node-collect .body .collect .count2 {
  margin-top: 20px
}

.partner-node-collect .body .collect .name {
  height: 17px;
  margin-top: 6px;
  font-size: 12px;
  font-family: PingFangSC-Regular, PingFang SC;
  font-weight: 400;
  color: #000412;
  line-height: 17px
}

.partner-node-collect .more {
  color: #5f84ff;
  cursor: pointer
}
</style>
