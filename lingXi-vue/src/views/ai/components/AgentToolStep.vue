<template>
  <div class="agent-tool-step" :class="`is-${normalizedStatus}`">
    <span class="tool-icon" aria-hidden="true">
      <svg v-if="iconType === 'search'" viewBox="0 0 24 24">
        <circle cx="10.8" cy="10.8" r="5.8" />
        <path d="m15.2 15.2 4.1 4.1" />
      </svg>
      <svg v-else-if="iconType === 'data'" viewBox="0 0 24 24">
        <ellipse cx="12" cy="6" rx="7" ry="3" />
        <path d="M5 6v6c0 1.7 3.1 3 7 3s7-1.3 7-3V6" />
        <path d="M5 12v6c0 1.7 3.1 3 7 3s7-1.3 7-3v-6" />
      </svg>
      <svg v-else-if="iconType === 'action'" viewBox="0 0 24 24">
        <path d="M14.7 6.3a4 4 0 0 0-5-5L7.4 3.6l3 3L12.7 4a2 2 0 0 1 2 2.7l-8.9 8.9a3.2 3.2 0 1 0 2.6 2.6l8.9-8.9a4 4 0 0 0-2.6-3Z" />
        <circle cx="6" cy="18" r="1" />
      </svg>
      <svg v-else-if="iconType === 'document'" viewBox="0 0 24 24">
        <path d="M7 3h7l4 4v14H7z" />
        <path d="M14 3v5h4M10 12h5M10 16h5" />
      </svg>
      <svg v-else viewBox="0 0 24 24">
        <path d="M12 3v3M12 18v3M3 12h3M18 12h3" />
        <path d="m5.6 5.6 2.1 2.1M16.3 16.3l2.1 2.1M18.4 5.6l-2.1 2.1M7.7 16.3l-2.1 2.1" />
        <circle cx="12" cy="12" r="3.5" />
      </svg>
    </span>

    <div class="tool-copy">
      <span class="tool-label">{{ activity.label || '执行工具' }}</span>
      <span v-if="hasInputSummary" class="tool-summary">{{ activity.inputSummary }}</span>
      <span v-else class="tool-name">{{ readableToolName }}</span>
    </div>

    <div class="tool-meta">
      <span v-if="hasResultCount" class="result-count">
        {{ activity.resultCount }} 项结果
      </span>
      <span v-if="elapsedText" class="elapsed-time">{{ elapsedText }}</span>
      <span class="status-text">
        <span class="status-dot" aria-hidden="true"></span>
        {{ statusText }}
      </span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { normalizeAgentActivityStatus } from '@/utils/agentExecutionTrace';

const props = defineProps({
  activity: {
    type: Object,
    required: true
  }
});

const normalizedStatus = computed(() => normalizeAgentActivityStatus(props.activity?.status));

const statusText = computed(() => ({
  running: '进行中',
  completed: '已完成',
  error: '未完成',
  pending: '处理中'
}[normalizedStatus.value]));

const hasResultCount = computed(() => (
  props.activity?.resultCount !== null
  && props.activity?.resultCount !== undefined
));

const hasInputSummary = computed(() => (
  typeof props.activity?.inputSummary === 'string'
  && props.activity.inputSummary.trim() !== ''
));

const elapsedText = computed(() => {
  const ms = Number(props.activity?.elapsedMs);
  if (!Number.isInteger(ms) || ms < 0 || normalizedStatus.value === 'running') return '';
  return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(1)} 秒`;
});

const readableToolName = computed(() => {
  const tool = String(props.activity?.tool || '').trim();
  if (!tool || tool === props.activity?.label) return 'Agent 工具';
  return tool.replaceAll('_', ' ');
});

const iconType = computed(() => {
  const tool = `${props.activity?.tool || ''} ${props.activity?.label || ''}`.toLowerCase();
  if (/search|query|find|recommend|搜索|查询|检索|推荐/.test(tool)) return 'search';
  if (/data|sql|database|analysis|stat|order|sales|库存|订单|数据|统计/.test(tool)) return 'data';
  if (/create|update|delete|repair|maintain|action|创建|修改|删除|维修|执行/.test(tool)) return 'action';
  if (/file|document|knowledge|read|文档|知识|文件/.test(tool)) return 'document';
  return 'default';
});
</script>

<style scoped lang="scss">
.agent-tool-step {
  --step-color: #64748b;
  --step-soft: #f1f5f9;
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  min-height: 52px;
  padding: 8px 10px;
  border: 1px solid transparent;
  border-radius: 10px;
  color: var(--lx-text, #172033);
  transition: border-color 160ms ease, background-color 160ms ease;

  &:hover {
    border-color: var(--lx-border-soft, #e2e8f0);
    background: rgb(248 250 252 / 78%);
  }

  &.is-running {
    --step-color: #0f766e;
    --step-soft: #e8f5f3;
  }

  &.is-completed {
    --step-color: #15803d;
    --step-soft: #edf7ef;
  }

  &.is-error {
    --step-color: #c24150;
    --step-soft: #fff0f2;
  }
}

.tool-icon {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: 9px;
  color: var(--step-color);
  background: var(--step-soft);

  svg {
    width: 17px;
    height: 17px;
    fill: none;
    stroke: currentColor;
    stroke-linecap: round;
    stroke-linejoin: round;
    stroke-width: 1.7;
  }
}

.tool-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.tool-label {
  overflow: hidden;
  color: var(--lx-text, #172033);
  font-size: 13px;
  font-weight: 650;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tool-name {
  overflow: hidden;
  color: var(--lx-text-muted, #8491a3);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 10px;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tool-summary {
  overflow: hidden;
  color: var(--lx-text-muted, #64748b);
  font-size: 11px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tool-meta {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  color: var(--lx-text-muted, #64748b);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.result-count {
  color: #64748b;
}

.elapsed-time {
  color: var(--lx-text-muted, #64748b);
}

.result-count,
.elapsed-time {
  font-variant-numeric: tabular-nums;
}

.status-text {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--step-color);
  font-weight: 600;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.is-running .status-dot {
  animation: agent-step-pulse 1.5s ease-in-out infinite;
  box-shadow: 0 0 0 3px rgb(15 118 110 / 11%);
}

@keyframes agent-step-pulse {
  0%, 100% { opacity: 0.55; transform: scale(0.85); }
  50% { opacity: 1; transform: scale(1); }
}

@media (max-width: 640px) {
  .agent-tool-step {
    grid-template-columns: 32px minmax(0, 1fr);
    align-items: start;
    padding: 9px 8px;
  }

  .tool-icon {
    width: 32px;
    height: 32px;
  }

  .tool-meta {
    grid-column: 2;
    justify-content: flex-start;
    margin-top: -1px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .agent-tool-step {
    transition: none;
  }

  .is-running .status-dot {
    animation: none;
  }
}
</style>
