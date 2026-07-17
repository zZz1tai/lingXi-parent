<template>
  <div class="box sku-sale-collect">
    <div class="header">
      <div class="title">
        销售数据<span class="sub-title">{{ datePickerFormat[0] }} ~ {{ datePickerFormat[1] }}</span>
      </div>
      <common-week-month-year @handleChange="handleRadioGroupSelChange" />
    </div>
    <div class="charts">
      <el-skeleton v-if="loading" :rows="2" animated style="width: 100%; height: 100%" />
      <div v-else class="chart-container">
        <sku-sale-collect-line-chart id="amount-collect" title="销售额趋势图" :chart-option="lineChartOption" />
        <sku-sale-collect-bar-chart id="region-collect" title="销售额分布" :chart-option="barChartOption" />
      </div>
    </div>
  </div>
</template>
<script setup>
import { ref, onMounted } from 'vue';
import dayjs from 'dayjs';
import CommonWeekMonthYear from '@/components/week-month-year/index.vue';
import SkuSaleCollectLineChart from './sku-sale-collect-line-chart.vue';
import SkuSaleCollectBarChart from './sku-sale-collect-bar-chart.vue';
import { getSkuSaleCollect } from '@/api/manage/dashboard';

// 定义变量
const datePickerSel = ref([]);
const datePickerFormat = ref([]);
const radioGroupSel = ref('week');
const userTaskStatus = ref([]);
const loading = ref(true);
const collectType = ref(1); // 统计时间类型，1:按日统计，2:按月统计

// 图表配置
const lineChartOption = ref({
  xAxisData: [],
  seriesData: [],
  yAxisName: '单位：元',
});

const barChartOption = ref({
  xAxisData: [],
  seriesData: [],
  yAxisName: '单位：元',
});

// 获取销售汇总数据
const fetchSkuSaleCollect = async () => {
  try {
    loading.value = true;
    const start = dayjs().startOf(radioGroupSel.value).format('YYYY-MM-DD HH:mm:ss');
    const end = dayjs().endOf('day').format('YYYY-MM-DD HH:mm:ss');
    
    const response = await getSkuSaleCollect({ start, end, collectType: collectType.value });
    const data = response.data || {};
    
    // 更新折线图数据
    lineChartOption.value = {
      xAxisData: data.lineXAxisData || [],
      seriesData: data.lineSeriesData || [],
      yAxisName: '单位：元',
    };
    
    // 更新柱状图数据
    barChartOption.value = {
      xAxisData: data.barXAxisData || [],
      seriesData: data.barSeriesData || [],
      yAxisName: '单位：元',
    };
  } catch (error) {
    console.error('获取销售汇总数据失败:', error);
    // 重置图表数据
    lineChartOption.value = {
      xAxisData: [],
      seriesData: [],
      yAxisName: '单位：元',
    };
    barChartOption.value = {
      xAxisData: [],
      seriesData: [],
      yAxisName: '单位：元',
    };
  } finally {
    loading.value = false;
  }
};

// 处理时间范围变化
const handleRadioGroupSelChange = (radioGroup) => {
  radioGroupSel.value = radioGroup;
  const startFormat = dayjs()
    .startOf(radioGroupSel.value)
    .format('YYYY.MM.DD');
  const endFormat = dayjs()
    .endOf('day')
    .format('YYYY.MM.DD');
  datePickerFormat.value = [startFormat, endFormat];
  
  // 根据时间范围设置统计类型
  if (radioGroup === 'year') {
    collectType.value = 2; // 按月统计
  } else {
    collectType.value = 1; // 按日统计
  }
  
  // 重新获取数据
  fetchSkuSaleCollect();
};

// 组件挂载时初始化
onMounted(() => {
  handleRadioGroupSelChange(radioGroupSel.value);
});
</script>
<style lang="scss" scoped>
.sku-sale-collect {
  display: flex;
  flex-direction: column;
  height: calc((100vh - 120px) * 0.6 - 20px);
  min-height: 352px;
  margin-top: 20px;
  background: #FFFFFF;
  border-radius: 20px;

  .charts {
    flex: 1;
    display: flex;
    width: 100%;
    overflow: hidden;
  }

  .chart-container {
    display: flex;
    width: 100%;
    height: 100%;
  }
}
</style>