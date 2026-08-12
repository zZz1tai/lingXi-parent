<template>
  <section class="chart-block">
    <h4 v-if="section.title" class="chart-title">{{ section.title }}</h4>
    <div ref="chartEl" class="chart-canvas"></div>
    <p v-if="!hasData" class="chart-empty">暂无图表数据</p>
  </section>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import * as echarts from 'echarts';
import { clampText } from './helpers';

const props = defineProps({
  section: {
    type: Object,
    required: true
  }
});

const chartEl = ref(null);
let chartInstance = null;
let resizeObserver = null;

const chartType = computed(() => props.section.type || 'LineChart');

const series = computed(() => {
  if (!Array.isArray(props.section.series)) return [];
  return props.section.series.slice(0, 6);
});

const hasData = computed(() => (
  series.value.some(item => Array.isArray(item?.data) && item.data.length)
));

const chartOption = computed(() => {
  const title = clampText(props.section.title, 200);
  const labels = (Array.isArray(props.section.labels) ? props.section.labels : [])
    .slice(0, 90)
    .map(item => clampText(item, 256));
  const xLabel = clampText(props.section.x_label, 256);
  const yLabel = clampText(props.section.y_label, 256);
  const colorPalette = ['#2f6fed', '#0f766e', '#d97706', '#7c3aed', '#c24150', '#0891b2'];

  const isPie = chartType.value === 'PieChart';
  const isBar = chartType.value === 'BarChart';

  const buildSeries = () => series.value.map((item, index) => {
    const name = clampText(item.name, 256);
    if (isPie) {
      return {
        name,
        type: 'pie',
        radius: ['38%', '68%'],
        center: ['50%', '52%'],
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: {
          color: '#5b6577',
          fontSize: 12,
          formatter: '{b}: {d}%'
        },
        data: (Array.isArray(item.data) ? item.data : [])
          .slice(0, 90)
          .map(slice => ({
            name: clampText(slice?.name, 256),
            value: slice?.value
          }))
      };
    }
    return {
      name,
      type: isBar ? 'bar' : 'line',
      smooth: !isBar,
      symbolSize: 6,
      barMaxWidth: 26,
      lineStyle: { width: 2.4 },
      itemStyle: { color: colorPalette[index % colorPalette.length] },
      data: (Array.isArray(item.data) ? item.data : [])
        .slice(0, 90)
        .map(value => value)
    };
  });

  return {
    color: colorPalette,
    tooltip: {
      trigger: isPie ? 'item' : 'axis',
      confine: true
    },
    legend: {
      type: 'scroll',
      top: 0,
      textStyle: { color: '#5b6577', fontSize: 12 }
    },
    grid: isPie
      ? {}
      : { left: 12, right: 20, top: 34, bottom: 8, containLabel: true },
    xAxis: isPie
      ? undefined
      : {
          type: 'category',
          name: xLabel || undefined,
          data: labels,
          axisLabel: { color: '#7b8798', fontSize: 12 },
          axisLine: { lineStyle: { color: '#d9dee7' } }
        },
    yAxis: isPie
      ? undefined
      : {
          type: 'value',
          name: yLabel || undefined,
          axisLabel: { color: '#7b8798', fontSize: 12 },
          splitLine: { lineStyle: { color: '#eef1f6' } }
        },
    series: buildSeries()
  };
});

const renderChart = () => {
  if (!chartEl.value || !hasData.value) return;
  if (!chartInstance) {
    chartInstance = echarts.init(chartEl.value);
  }
  chartInstance.setOption(chartOption.value, true);
};

const disposeChart = () => {
  if (resizeObserver) {
    resizeObserver.disconnect();
    resizeObserver = null;
  }
  if (chartInstance) {
    chartInstance.dispose();
    chartInstance = null;
  }
};

watch(() => props.section, async () => {
  await nextTick();
  renderChart();
}, { deep: true });

onMounted(async () => {
  await nextTick();
  renderChart();
  if (chartEl.value) {
    resizeObserver = new ResizeObserver(() => chartInstance?.resize());
    resizeObserver.observe(chartEl.value);
  }
});

onUnmounted(disposeChart);
</script>

<style scoped lang="scss">
.chart-block {
  margin: 6px 0;
}

.chart-title {
  margin: 0 0 6px;
  color: var(--lx-text, #172033);
  font-size: 13px;
  font-weight: 650;
  line-height: 1.4;
}

.chart-canvas {
  width: 100%;
  height: 260px;
}

.chart-empty {
  margin: 0;
  padding: 24px 0;
  color: var(--lx-text-muted, #7b8798);
  font-size: 13px;
  text-align: center;
}
</style>
