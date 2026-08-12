<template>
  <section class="openui-renderer" aria-label="可视化结果">
    <component
      v-for="(section, index) in knownSections"
      :key="`${section.type}-${index}`"
      :is="rendererFor(section.type)"
      :section="section"
    />
  </section>
</template>

<script setup>
import TextBlock from './TextBlock.vue';
import MarkdownBlock from './MarkdownBlock.vue';
import NoticeBlock from './NoticeBlock.vue';
import MetricGridBlock from './MetricGridBlock.vue';
import DataTableBlock from './DataTableBlock.vue';
import ChartBlock from './ChartBlock.vue';
import DeviceStatusCardBlock from './DeviceStatusCardBlock.vue';
import MaintenanceTaskCardBlock from './MaintenanceTaskCardBlock.vue';
import MediaBlock from './MediaBlock.vue';

const props = defineProps({
  sections: {
    type: Array,
    default: () => []
  }
});

const RENDERERS = {
  Text: TextBlock,
  Markdown: MarkdownBlock,
  Notice: NoticeBlock,
  MetricGrid: MetricGridBlock,
  DataTable: DataTableBlock,
  LineChart: ChartBlock,
  BarChart: ChartBlock,
  PieChart: ChartBlock,
  DeviceStatusCard: DeviceStatusCardBlock,
  MaintenanceTaskCard: MaintenanceTaskCardBlock,
  ImageResult: MediaBlock,
  VideoResult: MediaBlock
};

const rendererFor = type => RENDERERS[type] || null;

const knownSections = props.sections.filter(section => (
  section && typeof section === 'object' && RENDERERS[section.type]
));
</script>

<style scoped lang="scss">
.openui-renderer {
  display: grid;
  gap: 10px;
  margin: 10px 0 4px;
}
</style>
