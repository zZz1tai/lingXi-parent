<template>
  <div class="nk-page">
    <div class="nk-body">
      <WorksRail
        :works="works"
        :selected-id="selectedWork?.workId"
        :category="category"
        :loading="loadingWorks"
        :keyword="keyword"
        @update:category="category = $event"
        @update:keyword="keyword = $event"
        @select="selectWork"
        @new="openWorkDialog()"
        @edit="openWorkDialog($event)"
        @delete="handleDeleteWork"
      />

      <div class="nk-stage">
        <!-- 顶部作品信息 -->
        <header v-if="selectedWork" class="nk-header">
          <div class="nk-header-book">
            <div class="nk-header-cover">{{ selectedWork.workName.slice(0, 1) }}</div>
            <div class="nk-header-copy">
              <div class="nk-header-title" :title="'点击修改作品信息'" @click="openWorkDialog(selectedWork)">
                {{ selectedWork.workName }}
              </div>
              <div class="nk-header-sub">
                <span class="nk-worktype-badge" :class="selectedWork.workType === 'novel' ? 'is-novel' : 'is-short'">
                  {{ selectedWork.workType === 'novel' ? '长篇小说' : '短篇故事' }}
                </span>
                <span v-if="selectedWork.workType === 'novel' && chapters.length" class="nk-chapter-pos">
                  <template v-if="currentChapter">{{ chapterLabel(currentChapter) }}</template>
                  · 共 {{ chapters.length }} 章
                </span>
                <span v-if="selectedWork.genre">题材 · {{ selectedWork.genre }}</span>
                <span v-else>题材未定</span>
                <span class="nk-word-count">{{ formatWordCount(totalWordCount) }}</span>
                <span
                  class="nk-save-state"
                  :class="{ 'is-dirty': saveState === 'dirty', 'is-saving': saveState === 'saving', 'is-saved': saveState === 'saved' }"
                >
                  <el-icon><component :is="saveIcon" /></el-icon>
                  {{ saveStateText }}
                </span>
                <span v-if="!apiReady" class="nk-stamp">后端未就绪</span>
              </div>
            </div>
          </div>

          <div class="nk-header-actions">
            <button class="nk-btn" type="button" @click="handleExport">
              <el-icon><Download /></el-icon>导出手稿
            </button>
            <button class="nk-btn is-primary" type="button" @click="openWorkDialog(selectedWork)">
              <el-icon><EditPen /></el-icon>作品信息
            </button>
          </div>
        </header>

        <div v-else-if="!loadingWorks" class="nk-empty-stage">
          <el-icon class="nk-quill-big"><EditPen /></el-icon>
          <h2>书斋空无一人</h2>
          <p>{{ apiReady ? '新建一部作品，开始你的创作之旅。' : '后端服务未就绪，无法加载作品列表。请先启动 Java 服务并完成菜单授权。' }}</p>
          <button class="nk-btn is-primary" type="button" @click="openWorkDialog()">
            <el-icon><Plus /></el-icon>新建作品
          </button>
        </div>

        <!-- 工作区：对话 + 手稿 + （长篇）抽屉 -->
        <template v-if="selectedWork">
          <div class="nk-workspace">
            <div class="nk-chat-col">
              <ChatComposer
                ref="chatComposerRef"
                :work="selectedWork"
                :chapter="currentChapter"
                :manuscript="manuscript"
                :user-id="userStore.id"
                :user-name="userStore.name"
                @insert="handleInsert"
              />
            </div>

            <div class="nk-paper-col">
              <ManuscriptEditor
                v-model="manuscript"
                :title="currentChapter?.chapterTitle || selectedWork.workName"
                :word-count="manuscriptWordCount"
                :placeholder="selectedWork.workType === 'novel' ? '在右侧目录中新起一章，或让 AI 续写下一章…' : '提笔，写下属于你的故事…'"
              />
            </div>

            <aside v-if="selectedWork.workType === 'novel'" class="nk-drawer">
              <div class="nk-drawer-tabs">
                <button
                  type="button"
                  class="nk-drawer-tab"
                  :class="{ 'is-active': drawerTab === 'chapters' }"
                  @click="drawerTab = 'chapters'"
                >
                  目录
                </button>
                <button
                  type="button"
                  class="nk-drawer-tab"
                  :class="{ 'is-active': drawerTab === 'settings' }"
                  @click="drawerTab = 'settings'"
                >
                  设定集
                </button>
              </div>

              <div class="nk-drawer-body">
                <template v-if="drawerTab === 'chapters'">
                  <button
                    class="nk-btn nk-ai-next-chapter"
                    type="button"
                    :disabled="!chapters.length"
                    title="自动新起一章，并让 AI 衔接上一章结尾续写"
                    @click="handleAiNextChapter"
                  >
                    <el-icon><MagicStick /></el-icon>AI 续写下一章
                  </button>
                  <ChapterTree
                    :chapters="chapters"
                    :selected-id="currentChapter?.chapterId"
                    @select="selectChapter"
                    @add="handleAddChapter"
                    @rename="handleRenameChapter"
                    @delete="handleDeleteChapter"
                  />
                </template>

                <template v-else>
                  <div class="nk-settings-group">
                    <p class="nk-settings-label">人物</p>
                    <SettingNotebook
                      type="character"
                      :cards="settingCharacters"
                      @add="openSettingDialog"
                      @edit="openSettingDialog"
                      @delete="handleDeleteSetting"
                    />
                  </div>
                  <div class="nk-settings-group">
                    <p class="nk-settings-label">世界观</p>
                    <SettingNotebook
                      type="world"
                      :cards="settingWorlds"
                      @add="openSettingDialog"
                      @edit="openSettingDialog"
                      @delete="handleDeleteSetting"
                    />
                  </div>
                  <div class="nk-settings-group">
                    <p class="nk-settings-label">大纲</p>
                    <SettingNotebook
                      type="outline"
                      :cards="settingOutlines"
                      @add="openSettingDialog"
                      @edit="openSettingDialog"
                      @delete="handleDeleteSetting"
                    />
                  </div>
                </template>
              </div>
            </aside>

            <aside v-else class="nk-drawer">
              <div class="nk-drawer-tabs">
                <span class="nk-drawer-tab is-active">作品档案</span>
              </div>
              <div class="nk-drawer-body">
                <div class="nk-archive-card">
                  <p class="nk-settings-label">题材</p>
                  <p class="nk-archive-text">{{ selectedWork.genre || '未定' }}</p>
                </div>
                <div class="nk-archive-card">
                  <p class="nk-settings-label">故事梗概</p>
                  <p class="nk-archive-text">{{ selectedWork.synopsis || '尚未拟写梗概，可在「作品信息」中一键生成' }}</p>
                </div>
                <div class="nk-archive-card">
                  <p class="nk-settings-label">完稿进度</p>
                  <p class="nk-archive-text">{{ formatWordCount(manuscriptWordCount) }}<span v-if="manuscriptWordCount >= 3000" class="nk-archive-done"> · 已达短篇完稿量</span></p>
                </div>
                <div class="nk-archive-card">
                  <p class="nk-settings-label">创作提示</p>
                  <p class="nk-archive-text">短篇一次讲完一个完整故事，AI 会按你的指令直接续写正文，无需分章。</p>
                </div>
              </div>
            </aside>
          </div>
        </template>
      </div>
    </div>

    <!-- 作品新建/编辑 -->
    <el-dialog
      v-model="workDialog.open"
      :title="workDialog.isEdit ? '修改作品信息' : '开新书'"
      width="560px"
      append-to-body
      class="nk-dialog-paper"
    >
      <el-form ref="workFormRef" :model="workForm" :rules="workRules" label-position="top">
        <el-form-item label="书名 / 标题" prop="workName">
          <el-input v-model="workForm.workName" maxlength="64" show-word-limit placeholder="给作品起个名字" />
        </el-form-item>
        <el-form-item label="体裁" prop="workType">
          <el-radio-group v-model="workForm.workType" :disabled="workDialog.isEdit">
            <el-radio-button value="short">短篇故事</el-radio-button>
            <el-radio-button value="novel">长篇小说</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="题材">
          <el-input v-model="workForm.genre" maxlength="32" placeholder="例如：悬疑、科幻、都市言情、古风武侠…" />
        </el-form-item>
        <el-form-item label="故事梗概">
          <template #label>
            <span class="nk-synopsis-label">
              故事梗概
              <el-button
                link
                type="primary"
                size="small"
                class="nk-synopsis-btn"
                :loading="synopsisGenerating"
                :disabled="!workForm.workName.trim()"
                @click="handleGenerateSynopsis"
              >
                <el-icon><MagicStick /></el-icon>AI 拟梗概
              </el-button>
            </span>
          </template>
          <el-input
            v-model="workForm.synopsis"
            type="textarea"
            :rows="4"
            maxlength="2000"
            show-word-limit
            placeholder="一句话或一小段话交代故事的种子，AI 创作时会把它作为上下文。"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="workDialog.open = false">取消</el-button>
        <el-button type="primary" :loading="workDialog.submitting" @click="submitWork">落笔开书</el-button>
      </template>
    </el-dialog>

    <!-- 章节重命名 -->
    <el-dialog v-model="chapterDialog.open" title="章节更名" width="420px" append-to-body class="nk-dialog-paper">
      <el-form @submit.prevent="submitRenameChapter">
        <el-input v-model="chapterDialog.title" maxlength="64" placeholder="章节标题" @keyup.enter="submitRenameChapter" />
      </el-form>
      <template #footer>
        <el-button @click="chapterDialog.open = false">取消</el-button>
        <el-button type="primary" :loading="chapterDialog.submitting" @click="submitRenameChapter">保存</el-button>
      </template>
    </el-dialog>

    <!-- 设定卡编辑 -->
    <el-dialog
      v-model="settingDialog.open"
      :title="settingDialog.isEdit ? '修改设定卡' : `新增${settingTypeLabel}`"
      width="480px"
      append-to-body
      class="nk-dialog-paper"
    >
      <el-form label-position="top">
        <el-form-item :label="settingTypeLabel === '人物' ? '人物姓名' : '卡片名称'">
          <el-input v-model="settingForm.title" maxlength="32" :placeholder="`例如：${settingTypeLabel === '人物' ? '林晚晴' : '青铜城的三月'}`" />
        </el-form-item>
        <el-form-item :label="settingTypeLabel === '人物' ? '人物设定' : '设定内容'">
          <el-input v-model="settingForm.content" type="textarea" :rows="5" maxlength="1000" show-word-limit placeholder="写点什么，让故事更真实…" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="settingDialog.open = false">取消</el-button>
        <el-button type="primary" :loading="settingDialog.submitting" @click="submitSetting">保存卡片</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import {
  Delete, Download, EditPen, Loading, MagicStick, Plus, Check, CircleCheckFilled, WarningFilled
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { saveAs } from 'file-saver'
import useUserStore from '@/store/modules/user'
import {
  addNovelChapter, addNovelSetting, addNovelWork, delNovelChapter, delNovelSetting,
  delNovelWork, getNovelWork, listNovelChapter, listNovelSetting,
  listNovelWork, saveNovelManuscript, streamNovelSynopsis,
  updateNovelChapter, updateNovelSetting, updateNovelWork
} from '@/api/novel/novel'
import WorksRail from './components/WorksRail.vue'
import ChatComposer from './components/ChatComposer.vue'
import ManuscriptEditor from './components/ManuscriptEditor.vue'
import ChapterTree from './components/ChapterTree.vue'
import SettingNotebook from './components/SettingNotebook.vue'
import { countNovelCharacters } from './novelWordCount'
import './novel-kraft.scss'

defineOptions({ name: 'NovelWriting' })

const userStore = useUserStore()

// ── 作品列表 ─────────────────────────────────────────
const works = ref([])
const loadingWorks = ref(false)
const apiReady = ref(true)
const category = ref('short')
const keyword = ref('')
const selectedWork = ref(null)

onMounted(loadWorks)

async function loadWorks() {
  loadingWorks.value = true
  try {
    const result = await listNovelWork({ pageNum: 1, pageSize: 500 })
    works.value = result?.rows || []
    apiReady.value = true
    const keepId = selectedWork.value?.workId
    if (keepId) {
      const kept = works.value.find(work => work.workId === keepId)
      if (kept) selectedWork.value = { ...kept, ...selectedWork.value }
    }
  } catch (error) {
    apiReady.value = false
    works.value = []
    selectedWork.value = null
    ElMessage.error(error?.message || '作品列表加载失败')
  } finally {
    loadingWorks.value = false
  }
}

function selectWork(work) {
  if (selectedWork.value?.workId === work.workId) return
  selectedWork.value = { ...work, manuscript: '' }
  manuscript.value = ''
  chapters.value = []
  currentChapter.value = null
  settings.value = { character: [], world: [], outline: [] }
  if (work.workType === 'novel') {
    loadChapters(work.workId)
  } else {
    loadManuscript(work.workId)
  }
}

// ── 作品 CRUD ───────────────────────────────────────
const workDialog = reactive({ open: false, isEdit: false, submitting: false, workId: null })
const workForm = reactive({ workName: '', workType: 'short', genre: '', synopsis: '' })
const workFormRef = ref(null)
const workRules = {
  workName: [{ required: true, message: '请填写作品名称', trigger: 'blur' }]
}

const synopsisGenerating = ref(false)
let synopsisAbort = null

async function handleGenerateSynopsis() {
  const workName = workForm.workName.trim()
  if (!workName) {
    ElMessage.warning('先给作品起个名字，AI 才知道要写什么')
    return
  }
  if (synopsisGenerating.value) return
  synopsisGenerating.value = true
  workForm.synopsis = ''
  synopsisAbort = new AbortController()
  try {
    await streamNovelSynopsis(
      {
        workName,
        workType: workForm.workType,
        genre: workForm.genre
      },
      {
        signal: synopsisAbort.signal,
        onChunk: chunk => {
          workForm.synopsis += chunk
        }
      }
    )
    if (workForm.synopsis.trim()) {
      ElMessage.success('梗概已拟好，可以调整后再开书')
    } else {
      throw new Error('AI 没有返回梗概，请重试')
    }
  } catch (error) {
    if (error?.name === 'AbortError' || synopsisAbort?.signal?.aborted) {
      // 用户主动停止时保留已生成的部分
    } else {
      ElMessage.error(error?.message || 'AI 拟梗概失败，请稍后再试')
    }
  } finally {
    synopsisGenerating.value = false
    synopsisAbort = null
  }
}

function openWorkDialog(work = null) {
  if (work) {
    workDialog.isEdit = true
    workDialog.workId = work.workId
    Object.assign(workForm, {
      workName: work.workName || '',
      workType: work.workType || 'short',
      genre: work.genre || '',
      synopsis: work.synopsis || ''
    })
  } else {
    workDialog.isEdit = false
    workDialog.workId = null
    Object.assign(workForm, { workName: '', workType: category.value, genre: '', synopsis: '' })
  }
  workDialog.open = true
}

async function submitWork() {
  await workFormRef.value?.validate()
  workDialog.submitting = true
  try {
    const payload = {
      workName: workForm.workName,
      workType: workForm.workType,
      genre: workForm.genre,
      synopsis: workForm.synopsis
    }
    if (workDialog.isEdit) {
      await updateNovelWork({ ...payload, workId: workDialog.workId })
      ElMessage.success('作品信息已更新')
    } else {
      await addNovelWork(payload)
      ElMessage.success('开书成功，落笔吧')
    }
    workDialog.open = false
    await loadWorks()
  } catch (error) {
    ElMessage.error(error?.message || '保存失败，请检查后端服务是否就绪')
  } finally {
    workDialog.submitting = false
  }
}

function handleDeleteWork(work) {
  ElMessageBox.confirm(`确定要焚毁《${work.workName}》吗？删除后不可恢复。`, '焚毁手稿', {
    confirmButtonText: '焚毁',
    cancelButtonText: '再想想',
    type: 'warning'
  })
    .then(async () => {
      await delNovelWork(work.workId)
      if (selectedWork.value?.workId === work.workId) selectedWork.value = null
      ElMessage.success('手稿已焚毁')
      loadWorks()
    })
    .catch(() => {})
}

// ── 短篇正文 / 长篇章节 ─────────────────────────────
const manuscript = ref('')
const currentChapter = ref(null)
const chapters = ref([])

const manuscriptWordCount = computed(() =>
  countNovelCharacters(manuscript.value)
)

const totalWordCount = computed(() => {
  if (selectedWork.value?.workType === 'short') return manuscriptWordCount.value
  return chapters.value.reduce((sum, chapter) => {
    const isCurrent = chapter.chapterId === currentChapter.value?.chapterId
    const wordCount = isCurrent
      ? manuscriptWordCount.value
      : (chapter.content == null ? (chapter.wordCount || 0) : countNovelCharacters(chapter.content))
    return sum + wordCount
  }, 0)
})

const currentChapterWordCount = computed(() =>
  currentChapter.value ? manuscriptWordCount.value : 0
)

function syncSelectedWorkWordCount() {
  const work = selectedWork.value
  if (!work) return
  const wordCount = totalWordCount.value
  work.wordCount = wordCount
  const workInList = works.value.find(item => item.workId === work.workId)
  if (workInList) workInList.wordCount = wordCount
}

async function loadManuscript(workId) {
  try {
    const detail = await getNovelWork(workId)
    manuscript.value = detail?.data?.manuscript || detail?.data?.content || detail?.manuscript || ''
  } catch {
    manuscript.value = ''
  }
  syncSelectedWorkWordCount()
}

async function loadChapters(workId) {
  try {
    const result = await listNovelChapter(workId)
    chapters.value = result?.rows || []
    if (chapters.value.length) {
      await selectChapter(chapters.value[0])
    }
  } catch {
    chapters.value = []
  }
}

async function selectChapter(chapter) {
  currentChapter.value = chapter
  manuscript.value = chapter.content || ''
  markDirty()
}

function handleInsert(text) {
  const insertion = text.trim()
  if (!insertion) return
  manuscript.value = manuscript.value ? `${manuscript.value.replace(/\s+$/, '')}\n\n${insertion}` : insertion
  markDirty()
}

async function handleAddChapter() {
  const order = chapters.value.length + 1
  try {
    await addNovelChapter(selectedWork.value.workId, {
      chapterNo: order,
      chapterTitle: `第 ${order} 章`,
      content: ''
    })
    ElMessage.success(`已新起第 ${order} 章`)
    await loadChapters(selectedWork.value.workId)
  } catch (error) {
    ElMessage.error(error?.message || '新建章节失败，请检查后端服务')
  }
}

function chapterLabel(chapter) {
  const no = chapter.chapterNo || chapters.value.indexOf(chapter) + 1
  return chapter.chapterTitle || `第 ${no} 章`
}

async function handleAiNextChapter() {
  const work = selectedWork.value
  if (!work) return
  const lastChapter = chapters.value[chapters.value.length - 1]
  if (!lastChapter || !(lastChapter.content || '').trim()) {
    ElMessage.warning('先完成当前章节的正文，AI 才好接着往下写')
    return
  }
  const order = chapters.value.length + 1
  try {
    await addNovelChapter(work.workId, {
      chapterNo: order,
      chapterTitle: `第 ${order} 章`,
      content: ''
    })
    ElMessage.success(`已新起第 ${order} 章，AI 正在续写…`)
    await loadChapters(work.workId)
    chatComposerRef.value?.send(
      `续写下一章：衔接上一章《${lastChapter.chapterTitle || `第 ${order - 1} 章`}》的结尾，自然地开启新章节，保持人物口吻与叙事风格一致。`,
      true
    )
  } catch (error) {
    ElMessage.error(error?.message || '新起章节失败，请检查后端服务')
  }
}

const chapterDialog = reactive({ open: false, submitting: false, chapter: null, title: '' })

function handleRenameChapter(chapter) {
  chapterDialog.chapter = chapter
  chapterDialog.title = chapter.chapterTitle || ''
  chapterDialog.open = true
}

async function submitRenameChapter() {
  const title = chapterDialog.title.trim()
  if (!title) return
  chapterDialog.submitting = true
  try {
    await updateNovelChapter(selectedWork.value.workId, {
      chapterId: chapterDialog.chapter.chapterId,
      chapterTitle: title
    })
    ElMessage.success('章节已更名')
    chapterDialog.open = false
    await loadChapters(selectedWork.value.workId)
  } catch (error) {
    ElMessage.error(error?.message || '更名失败')
  } finally {
    chapterDialog.submitting = false
  }
}

function handleDeleteChapter(chapter) {
  ElMessageBox.confirm(`确定删除「${chapter.chapterTitle || '本章'}」吗？正文将一并删除。`, '删除章节', {
    confirmButtonText: '删除',
    cancelButtonText: '留着',
    type: 'warning'
  })
    .then(async () => {
      await delNovelChapter(selectedWork.value.workId, chapter.chapterId)
      ElMessage.success('章节已删除')
      await loadChapters(selectedWork.value.workId)
    })
    .catch(() => {})
}

// ── 设定集 ──────────────────────────────────────────
const settings = reactive({ character: [], world: [], outline: [] })

const settingCharacters = computed(() => settings.character)
const settingWorlds = computed(() => settings.world)
const settingOutlines = computed(() => settings.outline)

const settingDialog = reactive({ open: false, isEdit: false, submitting: false, type: 'character', settingId: null })
const settingForm = reactive({ title: '', content: '' })

const settingTypeLabel = computed(() => {
  const labels = { character: '人物', world: '世界观', outline: '大纲' }
  return labels[settingDialog.type] || '设定'
})

async function loadSettings(workId) {
  for (const type of ['character', 'world', 'outline']) {
    try {
      const result = await listNovelSetting(workId, type)
      settings[type] = result?.rows || []
    } catch {
      settings[type] = []
    }
  }
}

function openSettingDialog(type, card = null) {
  settingDialog.type = type
  if (card) {
    settingDialog.isEdit = true
    settingDialog.settingId = card.settingId
    Object.assign(settingForm, { title: card.title || '', content: card.content || '' })
  } else {
    settingDialog.isEdit = false
    settingDialog.settingId = null
    Object.assign(settingForm, { title: '', content: '' })
  }
  settingDialog.open = true
}

async function submitSetting() {
  if (!settingForm.title.trim()) {
    ElMessage.warning('请填写卡片名称')
    return
  }
  settingDialog.submitting = true
  try {
    const payload = {
      settingType: settingDialog.type,
      title: settingForm.title,
      content: settingForm.content
    }
    if (settingDialog.isEdit) {
      await updateNovelSetting(selectedWork.value.workId, { ...payload, settingId: settingDialog.settingId })
      ElMessage.success('设定卡已更新')
    } else {
      await addNovelSetting(selectedWork.value.workId, payload)
      ElMessage.success('设定卡已归档')
    }
    settingDialog.open = false
    await loadSettings(selectedWork.value.workId)
  } catch (error) {
    ElMessage.error(error?.message || '保存失败，请检查后端服务')
  } finally {
    settingDialog.submitting = false
  }
}

async function handleDeleteSetting(type, card) {
  try {
    await delNovelSetting(selectedWork.value.workId, card.settingId)
    ElMessage.success('卡片已撕掉')
    await loadSettings(selectedWork.value.workId)
  } catch (error) {
    ElMessage.error(error?.message || '删除失败')
  }
}

// ── 自动保存 ────────────────────────────────────────
const saveState = ref('saved') // dirty | saving | saved
const saveIcon = computed(() => {
  if (saveState.value === 'saving') return Loading
  if (saveState.value === 'dirty') return WarningFilled
  return CircleCheckFilled
})
const saveStateText = computed(() => {
  if (saveState.value === 'saving') return '正在落笔保存…'
  if (saveState.value === 'dirty') return '有未保存的修改'
  return '已保存'
})
let saveTimer = null

function markDirty() {
  saveState.value = 'dirty'
  clearTimeout(saveTimer)
  saveTimer = setTimeout(saveCurrent, 1500)
}

async function saveCurrent() {
  if (!selectedWork.value || saveState.value === 'saving') return
  const work = selectedWork.value
  saveState.value = 'saving'
  try {
    if (work.workType === 'short') {
      await saveNovelManuscript(work.workId, { content: manuscript.value })
    } else if (currentChapter.value) {
      const wordCount = currentChapterWordCount.value
      await updateNovelChapter(work.workId, {
        chapterId: currentChapter.value.chapterId,
        content: manuscript.value,
        wordCount
      })
      currentChapter.value.content = manuscript.value
      currentChapter.value.wordCount = wordCount
    }
    syncSelectedWorkWordCount()
    saveState.value = 'saved'
  } catch {
    saveState.value = 'dirty'
    ElMessage.warning('自动保存失败：后端接口未就绪')
  }
}

watch(manuscript, () => {
  if (selectedWork.value) {
    syncSelectedWorkWordCount()
    markDirty()
  }
})

// ── 导出 ────────────────────────────────────────────
async function handleExport() {
  const work = selectedWork.value
  if (!work) return
  let text = ''
  if (work.workType === 'novel') {
    const rows = chapters.value
    if (!rows.length) {
      ElMessage.warning('暂无章节可导出')
      return
    }
    text = rows
      .map((chapter, index) => `${chapter.chapterTitle || `第 ${index + 1} 章`}\n\n${chapter.content || ''}`)
      .join('\n\n')
  } else {
    text = manuscript.value
  }
  if (!text.trim()) {
    ElMessage.warning('手稿还是空的')
    return
  }
  const blob = new Blob([`《${work.workName}》\n\n${text}`], { type: 'text/plain;charset=utf-8' })
  saveAs(blob, `${work.workName}.txt`)
  ElMessage.success('手稿已导出')
}

// ── 杂项 ────────────────────────────────────────────
const drawerTab = ref('chapters')

function formatWordCount(count) {
  if (!count) return '0 字'
  if (count < 10000) return `${count} 字`
  return `${(count / 10000).toFixed(1)} 万字`
}

watch(
  () => selectedWork.value?.workId,
  async () => {
    drawerTab.value = 'chapters'
    if (selectedWork.value?.workType === 'novel') {
      await loadSettings(selectedWork.value.workId)
    }
  }
)

onBeforeUnmount(() => clearTimeout(saveTimer))
</script>

<style scoped>
.nk-chat-col {
  flex: 3;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.nk-paper-col {
  flex: 4;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.nk-settings-group {
  margin-bottom: 14px;

  .nk-settings-label {
    font-size: 12px;
    letter-spacing: 3px;
    color: var(--nk-sienna);
    font-weight: 700;
    margin-bottom: 6px;
    display: flex;
    align-items: center;
    gap: 6px;

    &::after {
      content: '';
      flex: 1;
      border-bottom: 1px dashed rgba(160, 86, 46, 0.35);
    }
  }
}

.nk-synopsis-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;

  .nk-synopsis-btn {
    display: inline-flex;
    align-items: center;
    gap: 3px;
    font-size: 12.5px;

    .el-icon {
      font-size: 13px;
    }
  }
}
</style>
