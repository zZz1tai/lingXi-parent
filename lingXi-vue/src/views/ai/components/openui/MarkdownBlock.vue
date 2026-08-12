<template>
  <div
    class="openui-markdown markdown-content"
    v-html="html"
  ></div>
</template>

<script setup>
import { computed } from 'vue';
import { renderOpenUiMarkdown } from './safeMarkdown';
import { clampText } from './helpers';

const props = defineProps({
  section: {
    type: Object,
    required: true
  }
});

const html = computed(() => renderOpenUiMarkdown(clampText(props.section.text)));
</script>

<style scoped lang="scss">
.openui-markdown {
  color: var(--lx-text, #172033);
  font-size: 14px;
  line-height: 1.7;
  overflow-wrap: anywhere;

  :deep(img) {
    max-width: 100%;
    border-radius: 8px;
  }

  :deep(a) {
    color: var(--lx-brand, #2f6fed);
    text-decoration: underline;
  }

  :deep(pre) {
    overflow-x: auto;
    padding: 10px 12px;
    border-radius: 8px;
    background: #f6f8fa;
    font-size: 13px;
  }

  :deep(blockquote) {
    margin: 8px 0;
    padding-left: 12px;
    border-left: 3px solid var(--lx-border-soft, #e2e8f0);
    color: var(--lx-text-muted, #7b8798);
  }

  :deep(table) {
    border-collapse: collapse;
    font-size: 13px;
  }

  :deep(th), :deep(td) {
    padding: 6px 10px;
    border: 1px solid var(--lx-border-soft, #e2e8f0);
  }
}
</style>
