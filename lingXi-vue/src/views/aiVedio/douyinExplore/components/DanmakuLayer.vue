<template>
  <div class="danmaku-layer" aria-hidden="true">
    <span
      v-for="item in flying"
      :key="item.id"
      class="danmaku-item"
      :style="{ top: item.top + '%', animationDuration: item.duration + 's' }"
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
  enabled: { type: Boolean, default: true }
})

const flying = ref([])
const pointer = ref(0)
const lastTimeMs = ref(0)
let uid = 0

watch(
  () => props.currentTime,
  (time) => {
    const currentMs = (Number(time) || 0) * 1000
    // 进度回退（拖动进度条）时重新开始发射
    if (currentMs + 1000 < lastTimeMs.value) {
      pointer.value = 0
      flying.value = []
    }
    lastTimeMs.value = currentMs
    if (!props.enabled) return
    const { due, nextPointer } = collectDueDanmaku(props.danmaku, pointer.value, currentMs)
    pointer.value = nextPointer
    for (const item of due.slice(0, 6)) {
      uid += 1
      flying.value.push({
        id: uid,
        text: item.text,
        top: 6 + Math.round(Math.random() * 70),
        duration: 6 + Math.round(Math.random() * 3)
      })
    }
    // 同屏上限，防止弹幕过密
    if (flying.value.length > 40) {
      flying.value.splice(0, flying.value.length - 40)
    }
  }
)

watch(
  () => props.enabled,
  (enabled) => {
    if (!enabled) {
      flying.value = []
    }
  }
)

// 切换视频时重置
watch(
  () => props.danmaku,
  () => {
    pointer.value = 0
    flying.value = []
    lastTimeMs.value = 0
  }
)

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
