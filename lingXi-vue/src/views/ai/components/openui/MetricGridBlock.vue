<template>
  <section class="metric-grid">
    <h4 v-if="section.title" class="metric-grid-title">{{ section.title }}</h4>
    <div
      class="metric-grid-cards"
      :class="`cols-${Math.min(section.columns || cards.length || 1, 6)}`"
    >
      <article
        v-for="(card, index) in cards"
        :key="index"
        class="metric-card"
        :class="`is-${toneClass(card.tone)}`"
      >
        <span class="metric-card-label">{{ card.label }}</span>
        <span class="metric-card-value">
          {{ card.value }}<small v-if="card.unit">{{ card.unit }}</small>
        </span>
      </article>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue';
import { clampText, noticeToneClass } from './helpers';

const props = defineProps({
  section: {
    type: Object,
    required: true
  }
});

const cards = computed(() => {
  if (!Array.isArray(props.section.cards)) return [];
  return props.section.cards.slice(0, 12).map(card => ({
    label: clampText(card.label, 256),
    value: clampText(card.value, 256),
    unit: clampText(card.unit, 256),
    tone: card.tone
  }));
});

const toneClass = tone => noticeToneClass(tone);
</script>

<style scoped lang="scss">
.metric-grid {
  margin: 6px 0;
}

.metric-grid-title {
  margin: 0 0 8px;
  color: var(--lx-text, #172033);
  font-size: 13px;
  font-weight: 650;
  line-height: 1.4;
}

.metric-grid-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 8px;

  &.cols-2 {
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  }

  &.cols-1 {
    grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  }
}

.metric-card {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  border: 1px solid var(--lx-border-soft, #e2e8f0);
  border-radius: 10px;
  background: #fbfcfd;

  &.is-success {
    background: #f0fbf3;
  }

  &.is-warning {
    background: #fdf8ec;
  }

  &.is-error {
    background: #fef1f2;
  }
}

.metric-card-label {
  color: var(--lx-text-muted, #7b8798);
  font-size: 12px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-card-value {
  color: var(--lx-text, #172033);
  font-size: 18px;
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;

  small {
    margin-left: 4px;
    color: var(--lx-text-muted, #7b8798);
    font-size: 12px;
    font-weight: 500;
  }
}
</style>
