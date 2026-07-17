<template>
  <div class="chart">
    <div id="chart" class="monitorContainer" :class="chartOption.xAxisData.length > 0 ? 'show' : 'hidden'" />
    <empty-data-chart :is-empty="chartOption.xAxisData.length === 0" />
  </div>
</template>
<style lang="scss" scoped>
.chart {
  position: relative;
  display: inline-block;
  width: 50%;
  height: 100%;
  min-width: 0; // 防止 flex 子元素溢出
}

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

.show {
  visibility: visible;
}

.hidden {
  visibility: hidden;
}
</style>
<script setup>
import * as echarts from 'echarts';
import { onMounted, onUnmounted, nextTick, ref, watch } from 'vue';
import EmptyDataChart from '@/components/empty-data-chart/index.vue';
const props = defineProps({
  chartOption: {
    type: Object,
    default: () => { },
  },
});
// 定义变量
let myChart = null;
// 坐标轴颜色
const axisStyleColor1 = ref('#D9D9D9');
const axisStyleColor2 = ref('#999999');
// 线的颜色
const lineStyleColor = ref('#FF5757');
// 渐变的颜色
const areaStyleColor = new echarts.graphic.LinearGradient(0, 0, 0, 1, [
  { offset: 0, color: 'rgba(255, 87, 87, 0.5)' },
  { offset: 0.3, color: 'rgba(255, 169, 169, 0.3)' },
  { offset: 1, color: 'rgba(255, 169, 169, 0)' },
]);

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
  const chartDom = document.getElementById('chart');
  if (!chartDom) return;
  if (!myChart) {
    myChart = echarts.init(chartDom);
  }
  let option = null;
  const grid = {
    left: '30',
    right: '33',
    top: '60',
    bottom: '5',
    containLabel: true,
  };
  nextTick(() => {
    setTimeout(() => {
      option = {
        title: {
          text: '销售额趋势图',
          left: 'center',
          top: '10',
          textStyle: {
            color: '#333333',
            fontWeight: 'bolder',
            fontSize: 14,
          },
        },
        grid: grid,
        xAxis: {
          type: 'category',
          data: props.chartOption.xAxisData,
          axisLine: {
            lineStyle: {
              color: axisStyleColor1.value,
            },
          },
          axisTick: {
            show: true,
            lineStyle: {
              color: axisStyleColor1.value,
            },
          },
          axisLabel: {
            align: 'center',
            color: axisStyleColor2.value,
          },
          boundaryGap: false,
        },
        yAxis: {
          type: 'value',
          name: props.chartOption.yAxisName,
          nameTextStyle: {
            color: axisStyleColor2.value,
          },
          axisLine: {
            show: false,
          },
          axisTick: {
            show: false,
          },
          axisLabel: {
            color: axisStyleColor2.value,
          },
          splitLine: {
            lineStyle: {
              color: axisStyleColor1.value,
            },
          },
        },
        series: getSeriesOption(),
        // TODO：细节调整
        tooltip: {
          trigger: 'axis',
          axisPointer: {
            lineStyle: {
              color: axisStyleColor1.value,
              width: 2,
            },
          },
          formatter: '销售额：{c}<br />{b}',
          backgroundColor: '#FFFFFF',
          borderColor: '#eef3f8',
          borderWidth: 1,
          textStyle: {
            color: '#8C8C8C',
          },
          padding: 12,
        },
      };
      myChart.setOption(option);
    }, 10);
  });
};
const getSeriesOption = () => {
  // 将分单位转换为元单位
  const formattedData = (props.chartOption.seriesData || []).map(value => value / 100);
  
  return [
    {
      type: 'line',
      data: formattedData,
      smooth: true,
      lineStyle: {
        color: lineStyleColor.value,
        width: 3,
        shadowColor: 'rgba(222, 115, 127, 0.23)',
        shadowBlur: 10,
        shadowOffsetY: 8,
      },
      areaStyle: {
        color: areaStyleColor,
      },
    },
  ];
};
</script>