<template>
  <section class="data-table-block">
    <h4 v-if="section.title" class="data-table-title">{{ section.title }}</h4>
    <el-table
      v-if="tableData.length"
      :data="tableData"
      border
      size="small"
      class="data-table"
    >
      <el-table-column
        v-for="(column, index) in columns"
        :key="`${column}-${index}`"
        :prop="`c${index}`"
        :label="column"
        show-overflow-tooltip
      />
    </el-table>
  </section>
</template>

<script setup>
import { computed } from 'vue';
import { clampText } from './helpers';

const props = defineProps({
  section: {
    type: Object,
    required: true
  }
});

const columns = computed(() => {
  if (!Array.isArray(props.section.columns)) return [];
  return props.section.columns.slice(0, 8).map(item => clampText(item, 128));
});

const tableData = computed(() => {
  if (!Array.isArray(props.section.rows)) return [];
  return props.section.rows.slice(0, 60).map(row => {
    const record = {};
    (Array.isArray(row) ? row : []).slice(0, 8).forEach((cell, index) => {
      record[`c${index}`] = String(cell ?? '');
    });
    return record;
  });
});
</script>

<style scoped lang="scss">
.data-table-block {
  margin: 6px 0;
}

.data-table-title {
  margin: 0 0 8px;
  color: var(--lx-text, #172033);
  font-size: 13px;
  font-weight: 650;
  line-height: 1.4;
}

.data-table {
  :deep(.el-table__header th) {
    color: var(--lx-text-muted, #7b8798);
    font-size: 12px;
    font-weight: 600;
  }
}
</style>
