<template>
  <section class="device-card">
    <div class="device-card-head">
      <span class="device-code">{{ section.inner_code || '未知设备' }}</span>
      <span class="device-status" :class="`is-${statusTone}`">{{ section.status || '未知' }}</span>
    </div>
    <dl class="device-facts">
      <div>
        <dt>设备名称</dt>
        <dd>{{ section.name || '未命名' }}</dd>
      </div>
      <div>
        <dt>所属区域</dt>
        <dd>{{ section.region || '未知区域' }}</dd>
      </div>
      <div v-if="section.updated_at">
        <dt>更新时间</dt>
        <dd>{{ section.updated_at }}</dd>
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

const statusTone = computed(() => {
  const status = String(props.section.status || '').toLowerCase();
  if (['online', 'normal', 'running', '已上线', '正常', '运行中'].includes(status)) {
    return 'ok';
  }
  if (['offline', 'error', 'fault', '异常', '离线'].includes(status)) {
    return 'bad';
  }
  return 'muted';
});
</script>

<style scoped lang="scss">
.device-card {
  padding: 12px 14px;
  border: 1px solid var(--lx-border-soft, #e2e8f0);
  border-radius: 12px;
  background: #fbfcfd;
}

.device-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.device-code {
  font-variant-numeric: tabular-nums;
  color: var(--lx-text, #172033);
  font-size: 14px;
  font-weight: 700;
}

.device-status {
  flex: none;
  padding: 2px 8px;
  border-radius: 999px;
  background: #eef1f6;
  color: #5b6577;
  font-size: 11px;
  font-weight: 600;

  &.is-ok {
    background: #e4f4e9;
    color: #1c6b33;
  }

  &.is-bad {
    background: #fde8eb;
    color: #a12b38;
  }
}

.device-facts {
  display: grid;
  gap: 6px;
  margin: 10px 0 0;

  div {
    display: grid;
    grid-template-columns: 72px minmax(0, 1fr);
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
