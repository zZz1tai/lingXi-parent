<template>
  <section class="nk-paper">
    <div class="nk-paper-head">
      <span class="nk-paper-title">{{ title }}</span>
      <span v-if="wordCount" class="nk-chat-hint">{{ wordCount }} 字</span>
      <div class="nk-paper-tools">
        <button class="nk-btn is-quiet" type="button" title="统计字数" @click="handleCount">
          <el-icon><Document /></el-icon>
        </button>
        <button class="nk-btn is-quiet" type="button" title="清空正文" @click="handleClear">
          <el-icon><Delete /></el-icon>
        </button>
      </div>
    </div>

    <div class="nk-paper-scroll">
      <span class="nk-clip is-top-right" aria-hidden="true" />
      <textarea
        v-model="draft"
        class="nk-manuscript"
        :placeholder="placeholder"
        spellcheck="false"
        @input="handleInput"
      />
    </div>
  </section>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { Document, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const props = defineProps({
  modelValue: { type: String, default: '' },
  title: { type: String, default: '手稿' },
  placeholder: { type: String, default: '提笔，写下属于你的故事…' },
  wordCount: { type: Number, default: 0 }
})

const emit = defineEmits(['update:modelValue', 'input'])

const draft = ref(props.modelValue)

watch(
  () => props.modelValue,
  value => {
    if (value !== draft.value) draft.value = value
  }
)

function handleInput() {
  emit('update:modelValue', draft.value)
  emit('input', draft.value)
}

function handleClear() {
  if (!draft.value) return
  ElMessageBox.confirm('确定要清空这篇手稿吗？清空后无法找回。', '焚毁手稿', {
    confirmButtonText: '清空',
    cancelButtonText: '再想想',
    type: 'warning'
  })
    .then(() => {
      draft.value = ''
      handleInput()
      ElMessage.success('手稿已清空')
    })
    .catch(() => {})
}

function handleCount() {
  const chars = (draft.value || '').replace(/\s/g, '').length
  ElMessage.success(`当前正文 ${chars} 字（不含空白）`)
}

onMounted(() => {
  if (!draft.value && props.modelValue) draft.value = props.modelValue
})
</script>
