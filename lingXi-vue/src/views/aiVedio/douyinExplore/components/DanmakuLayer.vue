<template>
  <div ref="layer" class="danmaku-layer" aria-hidden="true">
    <span
      v-for="item in flying"
      :key="item.id"
      class="danmaku-item"
      :style="{
        top: item.top + '%',
        animationDuration: item.duration + 's',
        animationDelay: -item.elapsedMs + 'ms',
        animationPlayState: playing && !seeking ? 'running' : 'paused'
      }"
      @animationstart="syncAnimationRate($event.currentTarget)"
      @animationend="removeItem(item.id)"
    >{{ item.text }}</span>
  </div>
</template>

<script setup name="DanmakuLayer">
import { collectDueDanmaku } from '@/utils/douyinVideo'

const props = defineProps({
  /** 弹幕列表（按 offsetMs 升序） */
  danmaku: { type: Array, default: () => [] },
  /** 当前播放时刻（秒） */
  currentTime: { type: Number, default: 0 },
  /** 是否开启弹幕 */
  enabled: { type: Boolean, default: true },
  playing: { type: Boolean, default: false },
  seeking: { type: Boolean, default: false },
  seekVersion: { type: Number, default: 0 },
  playbackRate: { type: Number, default: 1 }
})

const layer = ref(null)
const flying = ref([])
let pointer = 0
let uid = 0

watch(
  [() => props.currentTime, () => props.danmaku, () => props.enabled, () => props.seekVersion, () => props.seeking],
  ([time, danmaku, enabled, seekVersion, seeking], [, previousDanmaku, previousEnabled, previousSeekVersion]) => {
    const currentMs = (Number(time) || 0) * 1000
    if (danmaku !== previousDanmaku || enabled !== previousEnabled || seekVersion !== previousSeekVersion) {
      pointer = 0
      flying.value = []
    }
    if (!enabled || seeking) return

    // 跳播或数据晚到时，跳过已经错过的弹幕。
    const { due, nextPointer } = collectDueDanmaku(danmaku, pointer, currentMs, currentMs - 1000)
    pointer = nextPointer
    for (const item of due.slice(-6)) {
      uid += 1
      flying.value.push({
        id: uid,
        text: item.text,
        top: 6 + Math.round(Math.random() * 70),
        duration: 6 + Math.round(Math.random() * 3),
        elapsedMs: currentMs - (Number(item.offsetMs) || 0)
      })
    }
    // 同屏上限，防止弹幕过密
    if (flying.value.length > 40) {
      flying.value.splice(0, flying.value.length - 40)
    }
  },
  { immediate: true }
)

watch(() => props.playbackRate, () => syncAnimationRate(layer.value, true), { flush: 'post' })

function syncAnimationRate(element, subtree = false) {
  for (const animation of element?.getAnimations?.({ subtree }) ?? []) {
    animation.updatePlaybackRate(props.playbackRate)
  }
}

function removeItem(id) {
  const index = flying.value.findIndex((item) => item.id === id)
  if (index >= 0) {
    flying.value.splice(index, 1)
  }
}
</script>

<style scoped>
.danmaku-layer {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
  z-index: 5;
}

.danmaku-item {
  position: absolute;
  left: 100%;
  white-space: nowrap;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.85);
  animation-name: danmaku-fly;
  animation-timing-function: linear;
  animation-fill-mode: forwards;
}

@keyframes danmaku-fly {
  from {
    left: 100%;
  }
  to {
    left: -100%;
  }
}
</style>
