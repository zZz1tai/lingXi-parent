<template>
  <section class="task-card">
    <div class="task-card-head">
      <span class="task-code">{{ section.task_code || '未知工单' }}</span>
      <span class="task-badge" :class="`is-${priorityTone}`">{{ section.priority || '普通' }}</span>
    </div>
    <p class="task-device">{{ section.device_name || '未指定设备' }}</p>
    <dl class="task-facts">
      <div>
        <dt>类型</dt>
        <dd>{{ section.type || '常规' }}</dd>
      </div>
      <div>
        <dt>状态</dt>
        <dd>{{ section.status || '待处理' }}</dd>
      </div>
      <div v-if="section.notes">
        <dt>备注</dt>
        <dd>{{ section.notes }}</dd>
      </div>
    </dl>
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

const priorityTone = computed(() => {
  const priority = String(props.section.priority || '');
  if (['高', '紧急', 'urgent', 'high'].includes(priority)) return 'high';
  if (['中', 'medium'].includes(priority)) return 'medium';
  return 'low';
});
</script>

<style scoped lang="scss">
.task-card {
  padding: 12px 14px;
  border: 1px solid var(--lx-border-soft, #e2e8f0);
  border-radius: 12px;
  background: #fbfcfd;
}

.task-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.task-code {
  color: var(--lx-text, #172033);
  font-size: 14px;
  font-weight: 700;
}

.task-badge {
  flex: none;
  padding: 2px 8px;
  border-radius: 999px;
  background: #eef1f6;
  color: #5b6577;
  font-size: 11px;
  font-weight: 600;

  &.is-high {
    background: #fde8eb;
    color: #a12b38;
  }

  &.is-medium {
    background: #fdf3df;
    color: #8a5a10;
  }
}

.task-device {
  margin: 8px 0 0;
  color: var(--lx-text, #172033);
  font-size: 13px;
  font-weight: 600;
}

.task-facts {
  display: grid;
  gap: 6px;
  margin: 10px 0 0;

  div {
    display: grid;
    grid-template-columns: 48px minmax(0, 1fr);
    gap: 8px;
    font-size: 12px;
    line-height: 1.5;
  }

  dt {
    color: var(--lx-text-muted, #7b8798);
  }

  dd {
    margin: 0;
    color: var(--lx-text, #172033);
    overflow-wrap: anywhere;
  }
}
</style>
