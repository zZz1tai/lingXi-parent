<template>
  <p class="openui-text">{{ section.text }}</p>
</template>

<script setup>
import { clampText } from './helpers';

defineProps({
  section: {
    type: Object,
    required: true
  }
});
</script>

<style scoped lang="scss">
.openui-text {
  margin: 0;
  color: var(--lx-text, #172033);
  font-size: 14px;
  line-height: 1.7;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
</style>
