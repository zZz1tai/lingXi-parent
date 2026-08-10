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
      <div class="nk-manuscript-fields" :class="{ 'has-heading': headingLevel }">
        <input
          v-if="headingLevel"
          v-model="heading"
          class="nk-manuscript-heading"
          type="text"
          aria-label="正文标题"
          spellcheck="false"
          @input="handleInput"
          @keydown.enter.prevent="bodyRef?.focus()"
        />
        <textarea
          ref="bodyRef"
          v-model="body"
          class="nk-manuscript"
          :placeholder="placeholder"
          spellcheck="false"
          aria-label="正文内容"
          @input="handleInput"
        />
      </div>
    </div>
  </section>
</template>

<script setup>
import { ref, watch } from 'vue'
import { Document, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { joinMarkdownHeading, splitLeadingMarkdownHeading } from '../novelMarkdown'
import { countNovelCharacters } from '../novelWordCount'

const props = defineProps({
  modelValue: { type: String, default: '' },
  title: { type: String, default: '手稿' },
  placeholder: { type: String, default: '提笔，写下属于你的故事…' },
  wordCount: { type: Number, default: 0 }
})

const emit = defineEmits(['update:modelValue', 'input'])

const draft = ref('')
const headingLevel = ref(0)
const heading = ref('')
const body = ref('')
const bodyRef = ref(null)

function applyValue(value) {
  const parsed = splitLeadingMarkdownHeading(value)
  draft.value = value || ''
  headingLevel.value = parsed.level
  heading.value = parsed.title
  body.value = parsed.body
}

applyValue(props.modelValue)

watch(
  () => props.modelValue,
  value => {
    if (value !== draft.value) applyValue(value)
  }
)

function handleInput() {
  draft.value = joinMarkdownHeading({
    level: headingLevel.value,
    title: heading.value,
    body: body.value
  })
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
      headingLevel.value = 0
      heading.value = ''
      body.value = ''
      handleInput()
      ElMessage.success('手稿已清空')
    })
    .catch(() => {})
}

function handleCount() {
  const chars = countNovelCharacters(draft.value)
  ElMessage.success(`当前正文 ${chars} 字（不含空白）`)
}
</script>
