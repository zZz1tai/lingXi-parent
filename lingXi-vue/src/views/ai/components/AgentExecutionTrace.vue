<template>
  <section class="execution-trace" :class="`is-${traceStatus}`">
    <button
      type="button"
      class="trace-toggle"
      :aria-expanded="expanded"
      :aria-controls="panelId"
      @click="toggleExpanded"
    >
      <span class="trace-mark" aria-hidden="true">
        <svg viewBox="0 0 24 24">
          <path d="M8 5.5 4.5 9 8 12.5M16 11.5 19.5 15 16 18.5" />
          <path d="M10.5 18.5 13.5 5.5" />
        </svg>
      </span>
      <span class="trace-copy">
        <span class="trace-eyebrow">Agent 执行轨迹</span>
        <span class="trace-summary" aria-live="polite">{{ summaryText }}</span>
      </span>
      <span class="trace-count">{{ activities.length }} 个步骤</span>
      <svg class="trace-chevron" viewBox="0 0 20 20" aria-hidden="true">
        <path d="m6 8 4 4 4-4" />
      </svg>
    </button>

    <div v-show="expanded" :id="panelId" class="trace-panel">
      <ol class="trace-list">
        <li v-for="(activity, index) in activities" :key="activityKey(activity, index)">
          <AgentToolStep :activity="activity" />
        </li>
      </ol>
    </div>
  </section>
</template>

<script setup>
import { computed, ref, useId, watch } from 'vue';
import { getAgentExecutionTraceState } from '@/utils/agentExecutionTrace';
import AgentToolStep from './AgentToolStep.vue';

const props = defineProps({
  activities: {
    type: Array,
    default: () => []
  }
});

const panelId = `agent-trace-${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`;

const traceState = computed(() => getAgentExecutionTraceState(props.activities));
const traceStatus = computed(() => traceState.value.status);
const summaryText = computed(() => traceState.value.summaryText);
const expanded = ref(traceState.value.expandedByDefault);

watch(() => traceState.value.stateKey, () => {
  expanded.value = traceState.value.expandedByDefault;
});

const toggleExpanded = () => {
  expanded.value = !expanded.value;
};

const activityKey = (activity, index) => (
  activity?.callId || `${activity?.tool || 'tool'}-${index}`
);
</script>

<style scoped lang="scss">
.execution-trace {
  --trace-color: #64748b;
  --trace-soft: #f1f5f9;
  margin: 14px 0 16px;
  overflow: hidden;
  border: 1px solid var(--lx-border-soft, #e2e8f0);
  border-radius: 12px;
  background: #fbfcfd;

  &.is-running {
    --trace-color: #0f766e;
    --trace-soft: #e8f5f3;
  }

  &.is-completed {
    --trace-color: #15803d;
    --trace-soft: #edf7ef;
  }

  &.is-error {
    --trace-color: #c24150;
    --trace-soft: #fff0f2;
  }
}

.trace-toggle {
  display: grid;
  width: 100%;
  grid-template-columns: 34px minmax(0, 1fr) auto 18px;
  align-items: center;
  gap: 10px;
  padding: 11px 12px;
  border: 0;
  color: inherit;
  background: transparent;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: background-color 160ms ease;

  &:hover {
    background: #f6f8fa;
  }

  &:focus-visible {
    position: relative;
    z-index: 1;
    border-radius: 10px;
    outline: 3px solid rgb(15 118 110 / 22%);
    outline-offset: -3px;
  }
}

.trace-mark {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: 9px;
  color: var(--trace-color);
  background: var(--trace-soft);

  svg {
    width: 18px;
    height: 18px;
    fill: none;
    stroke: currentColor;
    stroke-linecap: round;
    stroke-linejoin: round;
    stroke-width: 1.7;
  }
}

.trace-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.trace-eyebrow {
  color: var(--lx-text-muted, #7b8798);
  font-size: 10px;
  font-weight: 650;
  letter-spacing: 0.08em;
  line-height: 1.3;
  text-transform: uppercase;
}

.trace-summary {
  overflow: hidden;
  color: var(--lx-text, #172033);
  font-size: 13px;
  font-weight: 650;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-count {
  color: var(--trace-color);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  white-space: nowrap;
}

.trace-chevron {
  width: 18px;
  height: 18px;
  fill: none;
  stroke: #8491a3;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.8;
  transition: transform 180ms ease;
}

.trace-toggle[aria-expanded='true'] .trace-chevron {
  transform: rotate(180deg);
}

.trace-panel {
  border-top: 1px solid var(--lx-border-soft, #e2e8f0);
  background: #fff;
}

.trace-list {
  display: grid;
  gap: 2px;
  margin: 0;
  padding: 6px;
  list-style: none;
}

@media (max-width: 640px) {
  .trace-toggle {
    grid-template-columns: 32px minmax(0, 1fr) 18px;
    gap: 9px;
    padding: 10px;
  }

  .trace-mark {
    width: 32px;
    height: 32px;
  }

  .trace-count {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .trace-toggle,
  .trace-chevron {
    transition: none;
  }
}
</style>
