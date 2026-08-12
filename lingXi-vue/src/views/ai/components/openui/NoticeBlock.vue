<template>
  <div
    class="openui-notice"
    :class="`is-${toneClass}`"
    role="status"
  >
    <span class="notice-icon" aria-hidden="true">
      <svg viewBox="0 0 24 24">
        <path d="M12 3.5 21 19.5H3L12 3.5Z" />
        <path d="M12 9.5v4.5" />
        <path d="M12 17h.01" />
      </svg>
    </span>
    <span class="notice-text">{{ section.text }}</span>
  </div>
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

const toneClass = computed(() => noticeToneClass(props.section.tone));
</script>

<style scoped lang="scss">
.openui-notice {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid #bcd9ff;
  border-radius: 10px;
  color: #1d4f9c;
  background: #f0f7ff;
  font-size: 13px;
  line-height: 1.6;

  &.is-success {
    border-color: #b9e4c2;
    color: #1c6b33;
    background: #f0fbf3;
  }

  &.is-warning {
    border-color: #f3d9a4;
    color: #8a5a10;
    background: #fdf8ec;
  }

  &.is-error {
    border-color: #f2b8bd;
    color: #a12b38;
    background: #fef1f2;
  }
}

.notice-icon {
  flex: none;
  display: grid;
  width: 18px;
  height: 18px;
  margin-top: 1px;
  place-items: center;

  svg {
    width: 100%;
    height: 100%;
    fill: none;
    stroke: currentColor;
    stroke-linecap: round;
    stroke-linejoin: round;
    stroke-width: 1.6;
  }
}

.notice-text {
  min-width: 0;
  overflow-wrap: anywhere;
}
</style>
