<template>
  <div id="chartTop" ref="EcharRef" class="monitorContainer"></div>
</template>
<script setup>
import * as echarts from 'echarts';
import { onMounted, onUnmounted, nextTick, ref, watch } from 'vue';
const width = ref('100%');
const height = ref('300px');
const chart = ref('chart');
const EcharRef = ref(null);
// 定义变量
let myChart = null;
// 获取父组件数据
const props = defineProps({
  chartOption: {
    type: Object,
    default: () => ({}),
  },
});

// 窗口大小变化时调整图表
const handleResize = () => {
  if (myChart) {
    myChart.resize();
  }
};

onMounted(() => {
  setOption();
  window.addEventListener('resize', handleResize);
});

onUnmounted(() => {
  window.removeEventListener('resize', handleResize);
  if (myChart) {
    myChart.dispose();
    myChart = null;
  }
});

// 监听数据变化，更新图表
watch(() => props.chartOption, () => {
  if (myChart) {
    setOption();
  }
}, { deep: true });
const setOption = () => {
  const chartDom = document.getElementById('chartTop');
  if (!chartDom) return;
  if (!myChart) {
    myChart = echarts.init(chartDom);
  }
  let option = null;
  nextTick(() => {
    setTimeout(() => {
      option = {
        // TODO：细节调整，小圈，阴影，线的长度...
        tooltip: {
          trigger: 'item',
          formatter: '{b}<br />总占比：{d}%',
          backgroundColor: '#FFFFFF',
          borderColor: '#eef3f8',
          borderWidth: 1,
          textStyle: {
            color: '#8C8C8C',
          },
          padding: 12,
        },
        label: {
          formatter: ['{name|{b}}', '{percentage|{d}%}'].join('\n'),
          rich: {
            name: {
              color: '#333333',
              fontWeight: 'bolder',
              align: 'left',
            },
            percentage: {
              color: '#000000',
              lineHeight: 15,
              align: 'left',
            },
          },
        },
        labelLine: {
          lineStyle: {
            color: '#BFBFBF',
          },
        },
        series: [
          {
            type: 'pie',
            roseType: 'radius',
            radius: [33, 100],
            center: ['50%', '50%'],
            data: props.chartOption.seriesData,
            animationEasing: 'cubicInOut',
            animationDuration: 2600,
          },
        ],
        color: ['#85A7FF', '#99EBBD', '#FFB18B', '#C6EBFF', '#BECCE6'],
      };
      myChart.setOption(option);
    }, 10);
  });
};
</script>
<style lang="scss" scoped>
.monitorContainer {
  width: 100%;
  height: 100%;
  min-height: 250px;
  min-width: 0; // 防止溢出

  & > div {
    &:first-child {
      width: 100% !important;

      & > canvas {
        width: 100% !important;
        height: 100% !important;
        max-width: 100%; // 确保不超出容器
      }
    }
  }
}
</style>
