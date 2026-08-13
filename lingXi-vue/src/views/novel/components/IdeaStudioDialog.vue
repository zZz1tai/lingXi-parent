<template>
  <el-dialog
    v-model="visible"
    width="min(1040px, 94vw)"
    append-to-body
    destroy-on-close
    class="nk-dialog-paper nk-idea-dialog"
    :close-on-click-modal="!streaming"
    :close-on-press-escape="!streaming"
    :show-close="!streaming"
    @closed="handleClosed"
  >
    <template #header>
      <div class="nk-idea-heading">
        <span class="nk-idea-kicker">灵感案卷 · IDEA DOSSIER</span>
        <strong>把一个念头，问成一本书</strong>
        <span>每轮只聊一两个关键问题；信息足够时，编辑会整理出可开书的构思文档。</span>
      </div>
    </template>

    <div class="nk-idea-layout">
      <section class="nk-idea-conversation">
        <div ref="conversationRef" class="nk-idea-thread" aria-live="polite">
          <div class="nk-idea-note is-editor">
            <span class="nk-idea-speaker">构思编辑</span>
            <p>先给我一个故事种子。可以只有一句话，例如“会下雨的沙漠”或“末世里只在夜间营业的食堂”。</p>
          </div>

          <div
            v-for="(entry, index) in messages"
            :key="index"
            class="nk-idea-note"
            :class="entry.role === 'user' ? 'is-author' : 'is-editor'"
          >
            <span class="nk-idea-speaker">{{ entry.role === 'user' ? '我' : '构思编辑' }}</span>
            <p>{{ entry.content }}</p>
            <ul v-if="entry.questions?.length" class="nk-idea-questions">
              <li v-for="(item, questionIndex) in entry.questions" :key="questionIndex">
                <strong>{{ item.question }}</strong>
                <span v-if="item.hint">{{ item.hint }}</span>
              </li>
            </ul>
          </div>

          <div v-if="streaming" class="nk-idea-working">
            <span></span><span></span><span></span>
            编辑正在梳理线索
          </div>
        </div>

        <div class="nk-idea-compose">
          <el-input
            v-model="input"
            type="textarea"
            :rows="3"
            maxlength="2000"
            show-word-limit
            resize="none"
            :disabled="streaming || Boolean(ideaDoc)"
            :placeholder="inputPlaceholder"
            @keydown.ctrl.enter.prevent="sendIdea"
          />
          <div class="nk-idea-compose-foot">
            <span>Ctrl + Enter 发送</span>
            <el-button v-if="messages.length" link :disabled="streaming" @click="restart">
              换一个故事种子
            </el-button>
            <el-button
              type="primary"
              :loading="streaming"
              :disabled="!input.trim() || Boolean(ideaDoc)"
              @click="sendIdea"
            >
              {{ messages.length ? '回答编辑' : '开始构思' }}
            </el-button>
          </div>
        </div>
      </section>

      <aside class="nk-idea-dossier" :class="{ 'is-ready': ideaDoc }">
        <div v-if="!ideaDoc" class="nk-idea-dossier-empty">
          <span class="nk-idea-file-tab">待整理</span>
          <div class="nk-idea-red-thread" aria-hidden="true"></div>
          <strong>构思文档会在这里成形</strong>
          <p>题材、人物、冲突、世界规则与关键场景将被整理成一份可以直接开书的案卷。</p>
          <ol>
            <li :class="{ done: messages.length >= 1 }">捕捉故事种子</li>
            <li :class="{ done: messages.length >= 3 }">补全人物与冲突</li>
            <li :class="{ done: Boolean(ideaDoc) }">形成开书方案</li>
          </ol>
        </div>

        <template v-else>
          <div class="nk-idea-doc-head">
            <span class="nk-idea-file-tab">构思完成</span>
            <el-tag effect="plain">{{ ideaDoc.genre }}</el-tag>
          </div>
          <el-input v-model="ideaDoc.work_name" maxlength="128" class="nk-idea-title-input" />
          <p class="nk-idea-one-liner">{{ ideaDoc.one_liner || ideaDoc.logline }}</p>

          <div class="nk-idea-doc-grid">
            <article>
              <span>核心冲突</span>
              <p>{{ ideaDoc.core_conflict || '—' }}</p>
            </article>
            <article>
              <span>故事基调</span>
              <p>{{ ideaDoc.tone || '—' }}</p>
            </article>
          </div>

          <div class="nk-idea-doc-section">
            <span>主要人物</span>
            <div class="nk-idea-people">
              <div v-for="person in ideaDoc.protagonists" :key="person.name">
                <strong>{{ person.name }}</strong>
                <small>{{ person.role || person.trait || '主角' }}</small>
                <p>{{ person.goal ? `目标：${person.goal}` : person.trait }}</p>
              </div>
            </div>
          </div>

          <div class="nk-idea-doc-section">
            <span>世界与规则</span>
            <p>{{ worldSummary }}</p>
          </div>

          <div v-if="ideaDoc.selling_points?.length" class="nk-idea-selling-points">
            <span v-for="point in ideaDoc.selling_points" :key="point">{{ point }}</span>
          </div>

          <el-button
            type="primary"
            size="large"
            class="nk-idea-create"
            :loading="creating"
            :disabled="!ideaDoc.work_name?.trim()"
            @click="createWork"
          >
            采用这份构思并开书
          </el-button>
          <p class="nk-idea-create-note">将创建长篇作品，并自动归档人物、世界观、故事骨架与卖点设定卡。</p>
        </template>
      </aside>
    </div>
  </el-dialog>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createNovelWorkFromIdea, deleteNovelIdeaThread, streamNovelIdea } from '@/api/novel/novel'
import { cleanNovelIdeaDisplayText } from '@/utils/novelIdeaProtocol'

const visible = defineModel({ type: Boolean, default: false })
const emit = defineEmits(['created'])

const input = ref('')
const messages = ref([])
const ideaDoc = ref(null)
const streaming = ref(false)
const creating = ref(false)
const conversationRef = ref(null)
let sessionId = createSessionId()
let abortController = null

const inputPlaceholder = computed(() => {
  if (ideaDoc.value) return '构思已经完成，可以在右侧确认后开书'
  return messages.value.length ? '回答上方问题，也可以说“你来决定”跳过…' : '写下你脑海里的故事种子…'
})

const worldSummary = computed(() => {
  const setting = ideaDoc.value?.setting || {}
  return [setting.world_building, setting.time_period, setting.location, ideaDoc.value?.magic_system]
    .filter(Boolean)
    .join(' · ') || '尚未展开'
})

function createSessionId() {
  const suffix = globalThis.crypto?.randomUUID?.().replaceAll('-', '') || `${Date.now()}${Math.random().toString(36).slice(2)}`
  return `novel-idea-${suffix}`
}

async function scrollToBottom() {
  await nextTick()
  if (conversationRef.value) conversationRef.value.scrollTop = conversationRef.value.scrollHeight
}

async function sendIdea() {
  const content = input.value.trim()
  if (!content || streaming.value || ideaDoc.value) return
  messages.value.push({ role: 'user', content })
  input.value = ''
  streaming.value = true
  abortController = new AbortController()
  let tokenText = ''
  let receivedTurnResponse = false
  await scrollToBottom()
  try {
    await streamNovelIdea(
      { message: content, sessionId },
      {
        signal: abortController.signal,
        onChunk: chunk => { tokenText += chunk },
        onEvent: event => {
          if (event.type === 'clarification' && event.data?.questions?.length) {
            receivedTurnResponse = true
            messages.value.push({
              role: 'assistant',
              content: cleanNovelIdeaDisplayText(event.content) || '再确认几个关键细节。',
              questions: event.data.questions
            })
            scrollToBottom()
          } else if (event.type === 'idea_doc' && event.data?.doc) {
            receivedTurnResponse = true
            ideaDoc.value = structuredClone(event.data.doc)
            messages.value.push({
              role: 'assistant',
              content: cleanNovelIdeaDisplayText(event.content) || '构思文档已经整理完成。'
            })
            scrollToBottom()
          }
        }
      }
    )
    const displayText = cleanNovelIdeaDisplayText(tokenText)
    if (displayText && !receivedTurnResponse) {
      receivedTurnResponse = true
      messages.value.push({ role: 'assistant', content: displayText })
    }
    if (!receivedTurnResponse) {
      throw new Error('构思编辑没有返回有效内容，请重试')
    }
  } catch (error) {
    if (error?.name !== 'AbortError' && !abortController?.signal?.aborted) {
      ElMessage.error(error?.message || '构思失败，请稍后重试')
    }
  } finally {
    streaming.value = false
    abortController = null
    scrollToBottom()
  }
}

async function createWork() {
  if (!ideaDoc.value?.work_name?.trim() || creating.value) return
  creating.value = true
  try {
    const result = await createNovelWorkFromIdea(ideaDoc.value)
    ElMessage.success('构思已归档，作品和首批设定卡已创建')
    visible.value = false
    emit('created', result?.data)
  } catch (error) {
    ElMessage.error(error?.message || '开书失败，请检查后端服务')
  } finally {
    creating.value = false
  }
}

async function discardSession() {
  const discardedSessionId = sessionId
  abortController?.abort()
  try {
    await deleteNovelIdeaThread(discardedSessionId)
  } catch {
    // 清理失败不阻断用户关闭或重新构思；服务端会记录可诊断日志。
  }
}

async function restart() {
  await discardSession()
  sessionId = createSessionId()
  input.value = ''
  messages.value = []
  ideaDoc.value = null
  streaming.value = false
}

async function handleClosed() {
  await restart()
}
</script>

<style lang="scss">
.nk-idea-dialog {
  .el-dialog__body { padding-top: 8px; }
  .nk-idea-heading { display: flex; flex-direction: column; gap: 3px; color: var(--nk-ink, #35291e); }
  .nk-idea-heading strong { font-size: 24px; letter-spacing: 2px; }
  .nk-idea-heading > span:last-child { color: var(--nk-ink-soft, #756451); font-size: 13px; }
  .nk-idea-kicker { color: var(--nk-sienna, #9a5730); font: 700 11px/1.4 Consolas, monospace; letter-spacing: 1.5px; }
}

.nk-idea-layout { display: grid; grid-template-columns: minmax(0, 1.05fr) minmax(330px, .95fr); gap: 18px; min-height: 560px; }
.nk-idea-conversation { display: flex; min-width: 0; flex-direction: column; border-right: 1px dashed rgba(110, 72, 34, .3); padding-right: 18px; }
.nk-idea-thread { flex: 1; min-height: 360px; max-height: 470px; overflow-y: auto; padding: 8px 10px 18px 4px; }
.nk-idea-note { width: 88%; margin-bottom: 16px; padding: 12px 14px; border: 1px solid rgba(110, 72, 34, .22); border-radius: 3px 12px 12px; background: rgba(255, 250, 235, .72); box-shadow: 0 4px 12px rgba(72, 44, 20, .07); }
.nk-idea-note.is-author { margin-left: auto; border-radius: 12px 3px 12px 12px; background: rgba(167, 89, 45, .1); }
.nk-idea-note p { margin: 4px 0 0; color: var(--nk-ink, #35291e); font-size: 14px; line-height: 1.75; white-space: pre-wrap; }
.nk-idea-speaker { color: var(--nk-sienna, #9a5730); font-size: 11px; font-weight: 700; letter-spacing: 1px; }
.nk-idea-questions { list-style: none; margin: 10px 0 0; padding: 0; }
.nk-idea-questions li { padding: 8px 0; border-top: 1px dashed rgba(110, 72, 34, .22); }
.nk-idea-questions strong, .nk-idea-questions span { display: block; }
.nk-idea-questions strong { font-size: 14px; line-height: 1.6; }
.nk-idea-questions span { margin-top: 2px; color: var(--nk-ink-faint, #8c7b68); font-size: 12px; }
.nk-idea-working { display: flex; align-items: center; gap: 5px; color: var(--nk-ink-faint, #8c7b68); font-size: 12px; }
.nk-idea-working span { width: 5px; height: 5px; border-radius: 50%; background: var(--nk-sienna, #9a5730); animation: nk-idea-pulse 1s infinite alternate; }
.nk-idea-working span:nth-child(2) { animation-delay: .18s; }.nk-idea-working span:nth-child(3) { animation-delay: .36s; }
@keyframes nk-idea-pulse { to { opacity: .22; transform: translateY(-3px); } }
.nk-idea-compose { padding-top: 12px; border-top: 1px dashed rgba(110, 72, 34, .3); }
.nk-idea-compose-foot { display: flex; align-items: center; gap: 8px; margin-top: 8px; }
.nk-idea-compose-foot > span { margin-right: auto; color: var(--nk-ink-faint, #8c7b68); font-size: 11px; }

.nk-idea-dossier { position: relative; min-width: 0; padding: 20px; border: 1px solid rgba(110, 72, 34, .3); background: linear-gradient(135deg, rgba(255, 250, 235, .92), rgba(232, 210, 169, .42)); box-shadow: 8px 9px 0 rgba(102, 69, 37, .08); overflow-y: auto; max-height: 560px; }
.nk-idea-file-tab { display: inline-block; padding: 4px 11px; color: #f7ecd4; background: var(--nk-sienna, #9a5730); font-size: 11px; letter-spacing: 2px; transform: rotate(-1deg); }
.nk-idea-dossier-empty { height: 100%; min-height: 420px; display: flex; align-items: center; flex-direction: column; justify-content: center; text-align: center; }
.nk-idea-dossier-empty strong { margin-top: 26px; font-size: 20px; letter-spacing: 2px; }
.nk-idea-dossier-empty p { max-width: 330px; color: var(--nk-ink-soft, #756451); font-size: 13px; line-height: 1.8; }
.nk-idea-dossier-empty ol { width: 230px; margin: 16px 0 0; padding: 0; list-style: none; text-align: left; counter-reset: step; }
.nk-idea-dossier-empty li { position: relative; padding: 7px 0 7px 30px; color: var(--nk-ink-faint, #8c7b68); font-size: 12px; }
.nk-idea-dossier-empty li::before { counter-increment: step; content: counter(step); position: absolute; left: 0; top: 5px; width: 20px; height: 20px; border: 1px solid rgba(110, 72, 34, .35); border-radius: 50%; text-align: center; line-height: 18px; }
.nk-idea-dossier-empty li.done { color: var(--nk-sienna, #9a5730); font-weight: 700; }
.nk-idea-red-thread { width: 120px; height: 36px; margin-top: 28px; border-top: 2px solid rgba(159, 55, 42, .72); border-radius: 50%; transform: rotate(-8deg); }
.nk-idea-doc-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.nk-idea-title-input .el-input__wrapper { padding-left: 0; border-radius: 0; background: transparent; box-shadow: 0 1px 0 rgba(110,72,34,.25); }
.nk-idea-title-input input { height: 46px; color: var(--nk-ink, #35291e); font-family: 'STKaiti', 'KaiTi', serif; font-size: 25px; font-weight: 700; letter-spacing: 2px; }
.nk-idea-one-liner { margin: 12px 0 18px; padding-left: 12px; border-left: 3px solid var(--nk-sienna, #9a5730); color: var(--nk-ink-soft, #756451); font-size: 13px; line-height: 1.7; }
.nk-idea-doc-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.nk-idea-doc-grid article, .nk-idea-doc-section { padding: 11px 12px; border: 1px dashed rgba(110,72,34,.28); background: rgba(255,255,255,.25); }
.nk-idea-doc-grid span, .nk-idea-doc-section > span { color: var(--nk-sienna, #9a5730); font-size: 11px; font-weight: 700; letter-spacing: 1px; }
.nk-idea-doc-grid p, .nk-idea-doc-section > p { margin: 5px 0 0; font-size: 13px; line-height: 1.65; }
.nk-idea-doc-section { margin-top: 10px; }
.nk-idea-people { display: grid; grid-template-columns: repeat(2, 1fr); gap: 7px; margin-top: 8px; }
.nk-idea-people > div { padding: 8px; background: rgba(128, 78, 37, .07); }
.nk-idea-people strong, .nk-idea-people small { display: block; }.nk-idea-people small { color: var(--nk-ink-faint, #8c7b68); }
.nk-idea-people p { margin: 5px 0 0; font-size: 11px; line-height: 1.5; }
.nk-idea-selling-points { display: flex; flex-wrap: wrap; gap: 6px; margin: 12px 0; }
.nk-idea-selling-points span { padding: 4px 8px; border: 1px solid rgba(159,55,42,.35); color: var(--nk-seal, #9f372a); font-size: 11px; }
.nk-idea-create { width: 100%; margin-top: 8px; }
.nk-idea-create-note { margin: 7px 0 0; color: var(--nk-ink-faint, #8c7b68); font-size: 11px; line-height: 1.5; text-align: center; }
@media (max-width: 820px) { .nk-idea-layout { grid-template-columns: 1fr; }.nk-idea-conversation { border-right: 0; padding-right: 0; }.nk-idea-dossier { max-height: none; }.nk-idea-thread { max-height: 330px; } }
@media (prefers-reduced-motion: reduce) { .nk-idea-working span { animation: none; } }
</style>
