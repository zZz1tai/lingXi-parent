<template>
<div class="chart">
  <div id="chartId" ref="EcharRef" class="monitorContainer"></div>
</div>

</template>
<script setup>
import * as echarts from 'echarts';
import { onMounted, onUnmounted, nextTick, ref, watch } from 'vue';
const props = defineProps({
  chartOption: {
    type: Object,
    default: () => {},
  },
});
// 定义变量
let myChart = null;
// 坐标轴的颜色
const axisColor = ref('#D9D9D9');
const axisColor2 = ref('#999999');
// 柱条的颜色
const itemStyleColor = ref('#91B0FF');

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
  const chartDom = document.getElementById('chartId');
  if (!chartDom) return;
  if (!myChart) {
    myChart = echarts.init(chartDom);
  }
  let option = null;
  const grid = {
    left: '33',
    right: '30',
    top: '60',
    bottom: '5',
    containLabel: true,
  };
  nextTick(() => {
    setTimeout(() => {
      option = {
        title: {
          text: '销售额分布',
          left: 'center',
          top: '10',
          textStyle: {
            color: '#333333',
            fontWeight: 'bolder',
            fontSize: 14,
          },
        },
        grid: grid,
        legend: {
          bottom: 0,
          data: props.chartOption.legendData,
        },
        xAxis: {
          type: 'category',
          data: props.chartOption.xAxisData,
          axisLine: {
            lineStyle: {
              color: axisColor.value,
            },
          },
          axisTick: {
            show: true,
            lineStyle: {
              color: axisColor.value,
            },
          },
          axisLabel: {
            interval: 0,
            align: 'center',
            color: axisColor2.value,
            formatter: function (value) {
              return value.length > 3 ? value.substring(0, 3) + '\n...' : value;
            },
          },
        },
        yAxis: {
          type: 'value',
          name: props.chartOption.yAxisName,
          nameTextStyle: {
            color: axisColor2.value,
          },
          axisLine: {
            show: false,
          },
          axisTick: {
            show: false,
          },
          axisLabel: {
            color: axisColor2.value,
          },
          splitLine: {
            lineStyle: {
              color: axisColor.value,
            },
          },
        },
        series: getSeriesOption(),
        // TODO：细节调整
        tooltip: {
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
      myChart.setOption(option)
    }, 10);
  });
};
const getSeriesOption = () => {
  // 将分单位转换为元单位
  const formattedData = (props.chartOption.seriesData || []).map(value => value / 100);
  
  return [
    {
      type: 'bar',
      data: formattedData,
      itemStyle: {
        color: itemStyleColor.value,
        barBorderRadius: [4, 4, 0, 0],
      },
      barWidth: 14,
      barGap: '20%',
      barCategoryGap: '40%',
    },
  ];
};
</script>
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
