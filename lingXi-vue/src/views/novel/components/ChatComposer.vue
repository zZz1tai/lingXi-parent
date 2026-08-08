<template>
  <section class="nk-paper">
    <div class="nk-paper-head">
      <span class="nk-paper-title">对话创作</span>
      <span class="nk-stamp">AI 执笔</span>
      <div class="nk-paper-tools">
        <button v-if="messages.length" class="nk-btn is-quiet" type="button" title="清空本作品的创作对话" @click="handleClearChat">
          <el-icon><Delete /></el-icon>
        </button>
      </div>
    </div>

    <div class="nk-chat-scroll" ref="scrollRef">
      <div v-if="!messages.length && !streaming" class="nk-chat-empty">
        <el-icon class="nk-quill-big"><EditPen /></el-icon>
        <h3>让 AI 为你执笔</h3>
        <p>告诉它你想写什么：续写、润色、拟章名、扩写场景……<br />生成后点击「采纳为正文」即可落入手稿。</p>
        <div class="nk-prompt-cards">
          <button
            v-for="(prompt, index) in suggestionPrompts"
            :key="index"
            type="button"
            class="nk-prompt-card"
            @click="send(prompt, false)"
          >
            {{ prompt }}
          </button>
        </div>
      </div>

      <template v-else>
        <article
          v-for="message in messages"
          :key="message.id"
          class="nk-msg"
          :class="[message.role === 'user' ? 'is-user' : 'is-ai', message.streaming ? 'is-cursor' : '']"
        >
          <span class="nk-msg-role" :class="message.role === 'user' ? 'is-user' : 'is-ai'">
            <el-icon v-if="message.role === 'user'"><User /></el-icon>
            <el-icon v-else><MagicStick /></el-icon>
          </span>
          <div class="nk-msg-bubble">
            <template v-if="message.role === 'user'">
              {{ message.content }}
            </template>
            <template v-else>
              {{ message.content }}
              <span v-if="message.streaming" class="nk-typing-cursor" />
              <template v-if="message.role === 'ai' && !message.streaming && message.content">
                <div class="nk-msg-actions">
                  <button class="nk-chip" type="button" @click="emitInsert(message.content)">
                    <el-icon><Download /></el-icon>采纳为正文
                  </button>
                  <button class="nk-chip" type="button" @click="handleRegenerate(message)">
                    <el-icon><RefreshRight /></el-icon>再写一次
                  </button>
                </div>
              </template>
              <div v-if="message.error" class="nk-msg-error">{{ message.error }}</div>
            </template>
          </div>
        </article>
      </template>
    </div>

    <div class="nk-chat-input">
      <div v-if="!streaming" class="nk-chip-bar">
        <button
          v-for="(chip, index) in quickChips"
          :key="index"
          type="button"
          class="nk-chip"
          @click="send(chip, true)"
        >
          {{ chip }}
        </button>
      </div>
      <textarea
        v-model="draftText"
        rows="2"
        :placeholder="props.work?.workType === 'novel' ? '向 AI 下达写作指令，例如：以悬疑的笔调续写本章下一段……' : '向 AI 下达写作指令，例如：扩写这个开头，让画面更有冲击力……'"
        @keydown.enter.exact.prevent="send(draftText, false)"
      />
      <div class="nk-chat-tools">
        <span class="nk-chat-hint">Enter 发送 · Shift+Enter 换行</span>
        <button v-if="streaming" class="nk-btn" type="button" @click="stopStream">
          <el-icon><VideoPause /></el-icon>停止
        </button>
        <button
          v-else
          class="nk-btn is-primary nk-send"
          type="button"
          :disabled="!canSend"
          @click="send(draftText, false)"
        >
          <el-icon><Promotion /></el-icon>落笔
        </button>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import {
  Delete, Download, EditPen, MagicStick, Promotion, RefreshRight, User, VideoPause
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getChatHistory } from '@/api/ai'
import { streamNovelWrite } from '@/api/novel/novel'

const props = defineProps({
  work: { type: Object, default: null },
  chapter: { type: Object, default: null },
  manuscript: { type: String, default: '' },
  userId: { type: [String, Number], default: '' },
  userName: { type: String, default: '' }
})

const emit = defineEmits(['insert'])

const messages = ref([])
const draftText = ref('')
const streaming = ref(false)
const scrollRef = ref(null)
let abortController = null

// ── 会话：每个作品一个独立 AI 会话 ─────────────────────
function workSessionId(work) {
  const key = `novel_session_${work.workId}`
  let sessionId = localStorage.getItem(key)
  if (!sessionId) {
    sessionId = `novel_${work.workId}_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
    localStorage.setItem(key, sessionId)
  }
  return sessionId
}

watch(
  () => props.work?.workId,
  async workId => {
    abortStream()
    messages.value = []
    draftText.value = ''
    if (!workId) return
    await loadHistory(workId)
  },
  { immediate: true }
)

  async function loadHistory(workId) {
  const work = props.work
  if (!work) return
  try {
    const result = await getChatHistory(workSessionId(work))
    const history = result?.data || result?.rows || []
    messages.value = (Array.isArray(history) ? history : []).map(item => ({
      id: `${item.id || Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
      role: item.messageType === 'user' ? 'user' : 'ai',
      content: item.content || '',
      streaming: false
    }))
  } catch {
    // 历史加载失败不阻塞创作；后端可用后自动恢复
  }
}

// ── 快捷指令 ──────────────────────────────────────────
const quickChips = computed(() => {
  const isNovel = props.work?.workType === 'novel'
  if (isNovel) {
    return [
      '续写下一段',
      '润色这段文字',
      '拟一个章节名',
      '设置悬念钩子',
      '扩写这个场景'
    ]
  }
  return [
    '续写故事',
    '润色全文',
    '拟一个题目',
    '增加人物冲突',
    '写一个反转结局'
  ]
})

const suggestionPrompts = computed(() => {
  const isNovel = props.work?.workType === 'novel'
  return isNovel
    ? [
        '按照当前章节的风格，续写下一段情节，保持人物口吻一致',
        '分析当前章节的节奏，指出问题并给出修改建议',
        '为整个长篇起草一个三幕式大纲'
      ]
    : [
        '根据我的故事灵感，扩写一个完整的短篇故事',
        '帮我把这个开头改写得更有画面感和悬念',
        '为短篇故事设计一个出人意料又合理的结局'
      ]
})

// ── 发送 ─────────────────────────────────────────────
const canSend = computed(() => !!(draftText.value.trim() || streaming.value) && !streaming.value)

async function send(rawText, asInstruction) {
  const text = (rawText || '').trim()
  if (!text || streaming.value) return

  let content = text
  if (asInstruction) {
    content = `【写作指令】${text}`
  }

  const userMessage = { id: `u_${Date.now()}`, role: 'user', content, streaming: false }
  const assistantMessage = { id: `a_${Date.now()}`, role: 'ai', content: '', streaming: true }
  messages.value.push(userMessage, assistantMessage)
  draftText.value = ''
  await scrollToBottom()

  streaming.value = true
  abortController = new AbortController()

  try {
    await streamNovelWrite(
      {
        message: content,
        sessionId: workSessionId(props.work),
        workId: props.work?.workId,
        chapterId: props.chapter?.chapterId || undefined
      },
      {
        signal: abortController.signal,
        onChunk: async chunk => {
          assistantMessage.content += chunk
          await scrollToBottom(true)
        }
      }
    )
    assistantMessage.streaming = false
  } catch (error) {
    assistantMessage.streaming = false
    if (error?.name === 'AbortError' || abortController?.signal?.aborted) {
      assistantMessage.content += assistantMessage.content ? '' : ''
    } else {
      assistantMessage.error = error?.message || '创作请求失败，请稍后再试'
      ElMessage.error(assistantMessage.error)
    }
  } finally {
    streaming.value = false
    abortController = null
  }
}

function handleRegenerate(message) {
  const index = messages.value.findIndex(item => item.id === message.id)
  if (index <= 0) return
  const previous = messages.value[index - 1]
  if (!previous) return
  messages.value.splice(index, 1)
  send(previous.content, false)
}

function stopStream() {
  abortStream()
  const last = messages.value[messages.value.length - 1]
  if (last) last.streaming = false
}

function abortStream() {
  if (abortController) {
    abortController.abort()
    abortController = null
  }
  streaming.value = false
}

function handleClearChat() {
  if (!messages.value.length) return
  ElMessageBox.confirm('清空后本作品的创作对话将不可恢复，确定吗？', '清空对话', {
    confirmButtonText: '清空',
    cancelButtonText: '保留',
    type: 'warning'
  })
    .then(() => {
      abortStream()
      messages.value = []
    })
    .catch(() => {})
}

function emitInsert(text) {
  emit('insert', text)
  ElMessage.success('已采纳为正文')
}

async function scrollToBottom(soft = false) {
  await nextTick()
  const el = scrollRef.value
  if (!el) return
  if (!soft || el.scrollHeight - el.scrollTop - el.clientHeight < 200) {
    el.scrollTop = el.scrollHeight
  }
}

// 父组件暴露作品变化时更新会话上下文
defineExpose({ workSessionId, send })
</script>

<style scoped>
.nk-msg-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed rgba(160, 86, 46, 0.35);
}

.nk-msg-error {
  margin-top: 8px;
  font-size: 12.5px;
  color: var(--nk-seal);
  border-left: 3px solid var(--nk-seal);
  padding-left: 8px;
}
</style>
