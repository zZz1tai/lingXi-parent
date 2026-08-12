<template>
  <figure class="media-block" :class="`is-${isVideo ? 'video' : 'image'}`">
    <video
      v-if="isVideo"
      :src="safeSrc"
      :poster="safePoster || undefined"
      controls
      preload="metadata"
    ></video>
    <img
      v-else
      :src="safeSrc"
      :alt="section.alt || 'AI 生成的图片'"
      loading="lazy"
    />
    <figcaption v-if="section.alt">{{ section.alt }}</figcaption>
  </figure>
</template>

<script setup>
import { computed } from 'vue';
import { clampText, isSafeMediaUrl } from './helpers';

const props = defineProps({
  section: {
    type: Object,
    required: true
  }
});

const isVideo = computed(() => props.section.type === 'VideoResult');

const safeSrc = computed(() => (
  isSafeMediaUrl(props.section.src) ? props.section.src : ''
));

const safePoster = computed(() => (
  props.section.poster && isSafeMediaUrl(props.section.poster)
    ? props.section.poster
    : ''
));
</script>

<style scoped lang="scss">
.media-block {
  margin: 6px 0;

  &.is-image img {
    max-width: 100%;
    max-height: 320px;
    border-radius: 10px;
  }

  &.is-video video {
    display: block;
    width: 100%;
    max-height: 320px;
    border-radius: 10px;
    background: #0f172a;
  }

  figcaption {
    margin-top: 6px;
    color: var(--lx-text-muted, #7b8798);
    font-size: 12px;
    line-height: 1.5;
  }
}
</style>
