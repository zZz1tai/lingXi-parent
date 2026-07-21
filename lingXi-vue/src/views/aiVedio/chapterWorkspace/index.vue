<template>
  <div class="chapter-studio">
    <div class="workspace-shell">
      <chapter-rail
        :project="project"
        :chapters="chapters"
        :active-id="chapterId"
        :loading="chapterLoading"
        :error="chapterError"
        @back="goBack"
        @select="selectChapter"
        @retry="loadContext"
      />

      <main class="workspace-main">
        <header class="workspace-header">
          <div>
            <p class="eyebrow">Chapter materials</p>
            <h1>{{ chapterTitle }}</h1>
            <p>先锁定人物三视图与空场景，再为每个镜头绑定参考资产并生成关键帧。</p>
          </div>
          <div class="header-actions">
            <el-button :icon="Refresh" :loading="assetLoading" @click="loadAssets">刷新</el-button>
            <el-button type="primary" :icon="VideoPlay" :loading="preparing" @click="prepareVideoDrafts">准备视频草稿</el-button>
          </div>
        </header>

        <el-alert
          v-if="contextError"
          :title="contextError"
          type="error"
          :closable="false"
          show-icon
          class="context-alert"
        >
          <template #default><el-button size="small" @click="loadContext">重新加载工作区</el-button></template>
        </el-alert>

        <section class="overview-grid" aria-label="章节素材统计">
          <button type="button" :class="{ active: activeTab === 'characters' }" @click="activeTab = 'characters'">
            <small>01 · IDENTITY</small><strong>{{ characterReadyCount }}/{{ characterReferenceAssets.length }}</strong><span>人物三视图</span>
          </button>
          <button type="button" :class="{ active: activeTab === 'scenes' }" @click="activeTab = 'scenes'">
            <small>02 · ENVIRONMENT</small><strong>{{ sceneReadyCount }}/{{ sceneReferenceAssets.length }}</strong><span>无人物场景</span>
          </button>
          <button type="button" :class="{ active: activeTab === 'shots' }" @click="activeTab = 'shots'">
            <small>03 · KEYFRAMES</small><strong>{{ approvedKeyframeAssets.length }}/{{ keyframeAssets.length }}</strong><span>绑定后关键帧</span>
          </button>
          <button type="button" :class="{ active: activeTab === 'videos' }" @click="activeTab = 'videos'">
            <small>04 · VIDEO TAKES</small><strong>{{ videoAssets.length }}</strong><span>视频候选</span>
          </button>
        </section>

        <nav class="workspace-tabs" aria-label="素材类型">
          <button :class="{ active: activeTab === 'characters' }" @click="activeTab = 'characters'">人物三视图</button>
          <button :class="{ active: activeTab === 'scenes' }" @click="activeTab = 'scenes'">空场景图</button>
          <button :class="{ active: activeTab === 'shots' }" @click="activeTab = 'shots'">关键帧与绑定</button>
          <button :class="{ active: activeTab === 'videos' }" @click="activeTab = 'videos'">视频候选</button>
        </nav>

        <asset-panel
          v-if="activeTab === 'characters'"
          title="人物三视图"
          eyebrow="Identity lock"
          description="人物资产属于整个项目。先生成并锁定正面、侧面、背面和固定服装，后续章节复用同一人物身份。"
          :assets="characterReferenceAssets"
          :task-by-asset-id="taskByAssetId"
          :loading="assetLoading"
          :error="assetError"
          empty-mark="人"
          empty-title="还没有人物资产"
          empty-description="完成章节解析后，系统会在项目级人物库中准备人物三视图草稿。"
          @retry-load="loadAssets"
          @edit="openPrompt"
          @approve="approveAsset"
          @retry="retryAsset"
          @delete="deleteAsset"
        />

        <asset-panel
          v-else-if="activeTab === 'scenes'"
          title="无人物场景图"
          eyebrow="Environment lock"
          description="场景参考图只定义空间、陈设、光线和色彩，必须保持空景，不包含人物、文字或水印。"
          :assets="sceneReferenceAssets"
          :task-by-asset-id="taskByAssetId"
          :loading="assetLoading"
          :error="assetError"
          empty-mark="景"
          empty-title="还没有场景资产"
          empty-description="完成章节解析后，系统会为每个场景准备一张纯环境参考图。"
          @retry-load="loadAssets"
          @edit="openPrompt"
          @approve="approveAsset"
          @retry="retryAsset"
          @delete="deleteAsset"
        />

        <asset-panel
          v-else-if="activeTab === 'shots'"
          title="分镜关键帧"
          eyebrow="Shot keyframes"
          description="每张关键帧必须绑定 1 个本场景空景版本，以及镜头中实际出现人物的三视图版本；支持自动匹配和人工换版。"
          :assets="keyframeAssets"
          :task-by-asset-id="taskByAssetId"
          :loading="assetLoading"
          :error="assetError"
          show-binding
          empty-mark="帧"
          empty-title="还没有关键帧"
          empty-description="请先完成人物与场景参考图，再生成本章分镜关键帧。"
          @retry-load="loadAssets"
          @edit="openPrompt"
          @approve="approveAsset"
          @retry="retryAsset"
          @bind="openKeyframeBinding"
          @delete="deleteAsset"
        />

        <video-version-panel
          v-else
          :assets="videoAssets"
          :task-by-asset-id="taskByAssetId"
          :loading="assetLoading"
          :preparing="preparing"
          :error="assetError"
          @retry-load="loadAssets"
          @prepare="prepareVideoDrafts"
          @edit="openPrompt"
          @delete="deleteAsset"
        />
      </main>
    </div>

    <el-dialog v-model="promptDialog.open" :title="promptDialog.title" width="min(720px, calc(100vw - 24px))" append-to-body>
      <el-alert
        :title="promptNotice"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-form label-position="top" class="prompt-form">
        <el-form-item label="正向提示词">
          <el-input v-model="promptDialog.promptText" type="textarea" :rows="9" maxlength="12000" show-word-limit :readonly="promptDialog.readonly" />
        </el-form-item>
        <el-form-item v-if="promptDialog.assetType !== 'VIDEO_CLIP'" label="负向提示词">
          <el-input v-model="promptDialog.negativePromptText" type="textarea" :rows="4" maxlength="4000" show-word-limit :readonly="promptDialog.readonly" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="promptDialog.open = false">关闭</el-button>
        <el-button
          v-if="!promptDialog.readonly"
          type="primary"
          :loading="promptDialog.submitting"
          @click="submitPrompt"
        >{{ promptSubmitLabel }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="bindingDialog.open"
      :title="`${bindingDialog.assetName} · 场景与人物绑定`"
      width="min(760px, calc(100vw - 24px))"
      append-to-body
      :close-on-click-modal="!bindingDialog.submitting"
      :show-close="!bindingDialog.submitting"
    >
      <el-alert
        :title="bindingDialog.editableInPlace ? '当前草稿会直接更新绑定' : '当前版本已有结果，保存后会创建新的关键帧草稿版本'"
        description="绑定操作不会调用生成模型。自动模式选择当前场景和人物的最新可用版本；人工模式只切换对应身份的具体版本。"
        type="info"
        :closable="false"
        show-icon
      />
      <div class="binding-status-row">
        <el-tag :type="bindingDialog.bindingMode === 'MANUAL' ? 'warning' : 'success'" effect="plain">
          当前：{{ bindingDialog.bindingMode === 'MANUAL' ? '人工' : '自动' }}绑定
        </el-tag>
        <span>自动识别人物 {{ bindingDialog.requiredCharacterCount }} 人 · 人工可选 0–4 人 · 场景固定 1 张</span>
      </div>
      <el-alert
        v-if="bindingDialog.loadError || bindingDialog.selectionMessage"
        :title="bindingDialog.loadError || bindingDialog.selectionMessage"
        :type="bindingDialog.loadError ? 'error' : 'warning'"
        :closable="false"
        show-icon
        class="binding-alert"
      />
      <el-form v-loading="bindingDialog.loading" label-position="top" class="prompt-form binding-form">
        <el-form-item label="空场景参考图（固定 1 张）">
          <el-select v-model="bindingDialog.sceneReferenceAssetId" filterable placeholder="选择当前场景已生成的空景版本" :disabled="bindingDialog.loading || bindingDialog.submitting">
            <el-option v-for="asset in bindingDialog.sceneAssets" :key="asset.assetId" :label="referenceOptionLabel(asset)" :value="asset.assetId" :disabled="asset.status !== 'APPROVED'" />
          </el-select>
        </el-form-item>
        <el-form-item label="人物三视图（人工可选 0–4 张）">
          <el-select
            v-model="bindingDialog.characterReferenceAssetIds"
            multiple
            filterable
            collapse-tags
            :multiple-limit="4"
            placeholder="选择当前镜头人物对应的三视图版本"
            :disabled="bindingDialog.loading || bindingDialog.submitting"
          >
            <el-option v-for="asset in bindingDialog.characterAssets" :key="asset.assetId" :label="referenceOptionLabel(asset)" :value="asset.assetId" :disabled="asset.status !== 'APPROVED'" />
          </el-select>
          <small class="binding-help">人工模式可以增删或改选人物参考；请同步检查关键帧提示词，确保人物姓名、出场人数和所选三视图一致。</small>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="bindingDialog.submitting" @click="bindingDialog.open = false">取消</el-button>
        <el-button :loading="bindingDialog.submitting" :disabled="bindingDialog.loading || !!bindingDialog.loadError" @click="resetKeyframeBindingToAuto">恢复自动匹配</el-button>
        <el-button type="primary" :loading="bindingDialog.submitting" :disabled="!bindingSelectionReady" @click="saveManualKeyframeBinding">
          {{ bindingDialog.editableInPlace ? '保存人工绑定' : '创建新版本并绑定' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="AiVedioChapterWorkspace">
import { Refresh, VideoPlay } from '@element-plus/icons-vue'
import ChapterRail from './components/ChapterRail.vue'
import AssetPanel from './components/AssetPanel.vue'
import VideoVersionPanel from './components/VideoVersionPanel.vue'
import {
  approveAiVideoAsset,
  createAiVideoAssetVideoDraft,
  delAiVideoAsset,
  generateAiVideoAssetImage,
  generateAiVideoAssetVideo,
  getAiVideoKeyframeReferenceBinding,
  getAiVideoProject,
  listAiVideoAsset,
  listAiVideoChapter,
  listAiVideoTask,
  retryAiVideoAssetImage,
  resetAiVideoKeyframeReferenceBinding,
  updateAiVideoAssetPrompt,
  updateAiVideoKeyframeReferenceBinding,
  updateAiVideoAssetVideoPrompt
} from '@/api/aiVedio/project'

const route = useRoute()
const router = useRouter()
const { proxy } = getCurrentInstance()

const project = ref(null)
const chapters = ref([])
const assets = ref([])
const taskByAssetId = ref({})
const chapterLoading = ref(false)
const assetLoading = ref(false)
const preparing = ref(false)
const chapterError = ref('')
const assetError = ref('')
const contextError = ref('')
const activeTab = ref('characters')
let pollTimer = null
let autoPrepareKey = ''

const projectId = computed(() => route.params.projectId)
const chapterId = computed(() => route.params.chapterId)
const currentChapter = computed(() => chapters.value.find(chapter => String(chapter.chapterId) === String(chapterId.value)) || null)
const chapterTitle = computed(() => currentChapter.value?.chapterTitle || (currentChapter.value ? `第 ${currentChapter.value.chapterNo} 章` : '章节素材工作台'))
const characterReferenceAssets = computed(() => assets.value.filter(asset => asset.assetType === 'CHARACTER_REFERENCE'))
const sceneReferenceAssets = computed(() => assets.value.filter(asset => asset.assetType === 'SCENE_REFERENCE'))
const keyframeAssets = computed(() => assets.value.filter(asset => asset.assetType === 'SHOT_KEYFRAME'))
const videoAssets = computed(() => assets.value.filter(asset => asset.assetType === 'VIDEO_CLIP'))
const characterReadyCount = computed(() => characterReferenceAssets.value.filter(asset => asset.status === 'APPROVED').length)
const sceneReadyCount = computed(() => sceneReferenceAssets.value.filter(asset => asset.status === 'APPROVED').length)
const approvedKeyframeAssets = computed(() => keyframeAssets.value.filter(asset => asset.status === 'APPROVED'))
const promptDialog = reactive({ open: false, submitting: false, readonly: false, title: '', assetId: null, assetType: '', promptText: '', negativePromptText: '' })
const promptNotice = computed(() => ({
  CHARACTER_REFERENCE: '人物三视图只锁定人物身份、固定服装与配色，不应包含故事场景。',
  SCENE_REFERENCE: '场景参考图必须保持空景，不包含人物、文字或水印。',
  SHOT_KEYFRAME: '关键帧会使用已绑定的人物三视图和空场景图；参考资产未生成完成时不能提交。',
  VIDEO_CLIP: '确认后才会提交视频生成任务并产生模型费用。'
}[promptDialog.assetType] || '确认提示词后才会提交图片生成任务。'))
const promptSubmitLabel = computed(() => ({
  CHARACTER_REFERENCE: '保存并生成三视图',
  SCENE_REFERENCE: '保存并生成空场景',
  SHOT_KEYFRAME: '保存并生成关键帧',
  VIDEO_CLIP: '保存并生成视频'
}[promptDialog.assetType] || '保存并生成'))
const bindingDialog = reactive({
  open: false,
  loading: false,
  submitting: false,
  assetId: null,
  assetName: '',
  bindingMode: 'AUTO',
  editableInPlace: true,
  sceneReferenceAssetId: null,
  characterReferenceAssetIds: [],
  sceneAssets: [],
  characterAssets: [],
  currentCharacterReferences: [],
  requiredCharacterCount: 0,
  loadError: '',
  selectionMessage: ''
})
const bindingSelectionReady = computed(() => {
  if (bindingDialog.loading || bindingDialog.submitting || bindingDialog.loadError) return false
  const sceneSelected = bindingDialog.sceneAssets.find(asset => String(asset.assetId) === String(bindingDialog.sceneReferenceAssetId))
  if (sceneSelected?.status !== 'APPROVED' || bindingDialog.characterReferenceAssetIds.length > 4) return false
  const selectedCharacters = bindingDialog.characterReferenceAssetIds
    .map(assetId => bindingDialog.characterAssets.find(asset => String(asset.assetId) === String(assetId)))
    .filter(Boolean)
  return selectedCharacters.length === bindingDialog.characterReferenceAssetIds.length
    && selectedCharacters.every(asset => asset.status === 'APPROVED')
})

async function loadContext() {
  chapterLoading.value = true
  chapterError.value = ''
  contextError.value = ''
  try {
    const [projectResponse, chapterResponse] = await Promise.all([
      getAiVideoProject(projectId.value),
      listAiVideoChapter(projectId.value)
    ])
    project.value = projectResponse.data || null
    chapters.value = chapterResponse.rows || chapterResponse.data || []
    if (!currentChapter.value && chapters.value.length) {
      await router.replace({ name: 'AiVedioChapterWorkspace', params: { projectId: projectId.value, chapterId: chapters.value[0].chapterId } })
      return
    }
    await loadAssets()
    const prepareKey = `${projectId.value}:${chapterId.value}`
    if (route.query.prepare === '1' && autoPrepareKey !== prepareKey) {
      autoPrepareKey = prepareKey
      await prepareVideoDrafts()
    }
  } catch (error) {
    const message = errorMessage(error, '项目或章节读取失败')
    chapterError.value = message
    contextError.value = message
  } finally {
    chapterLoading.value = false
  }
}

async function fetchAllAssets(query) {
  const result = []
  const pageSize = 200
  for (let pageNum = 1; pageNum <= 100; pageNum += 1) {
    const response = await listAiVideoAsset({ ...query, pageNum, pageSize })
    const rows = response.rows || []
    result.push(...rows)
    const total = Number(response.total)
    if (!rows.length || (Number.isFinite(total) && result.length >= total) || rows.length < pageSize) break
  }
  return result
}

async function loadAssets() {
  if (!projectId.value || !chapterId.value) return
  assetLoading.value = true
  assetError.value = ''
  try {
    const [chapterAssetRows, characterAssetRows, taskResponse] = await Promise.all([
      fetchAllAssets({ projectId: projectId.value, chapterId: chapterId.value }),
      fetchAllAssets({ projectId: projectId.value, assetType: 'CHARACTER_REFERENCE' }),
      listAiVideoTask(projectId.value)
    ])
    const assetById = new Map()
    const combinedAssetRows = [...characterAssetRows, ...chapterAssetRows]
    combinedAssetRows.forEach(asset => assetById.set(String(asset.assetId), asset))
    assets.value = Array.from(assetById.values())
    const tasks = taskResponse.rows || taskResponse.data || []
    taskByAssetId.value = Object.fromEntries(tasks.filter(task => task.assetId).map(task => [task.assetId, task]))
    schedulePolling()
  } catch (error) {
    assetError.value = errorMessage(error, '章节素材读取失败')
  } finally {
    assetLoading.value = false
  }
}

async function openKeyframeBinding(asset) {
  Object.assign(bindingDialog, {
    open: true,
    loading: true,
    submitting: false,
    assetId: asset.assetId,
    assetName: asset.assetName || '分镜关键帧',
    bindingMode: 'AUTO',
    editableInPlace: ['DRAFT', 'REJECTED'].includes(asset.status),
    sceneReferenceAssetId: null,
    characterReferenceAssetIds: [],
    sceneAssets: [],
    characterAssets: [],
    currentCharacterReferences: [],
    requiredCharacterCount: 0,
    loadError: '',
    selectionMessage: ''
  })
  try {
    const response = await getAiVideoKeyframeReferenceBinding(asset.assetId)
    if (bindingDialog.assetId !== asset.assetId) return
    const detail = response?.data || {}
    const currentScene = detail.sceneReference || null
    const currentCharacters = Array.isArray(detail.characterReferences) ? detail.characterReferences : []
    const sceneIdentity = detail.asset?.sceneId ?? currentScene?.sceneId ?? asset.sceneId
    const approvedSceneOptions = sceneReferenceAssets.value.filter(candidate =>
      candidate.status === 'APPROVED'
      && sceneIdentity !== null
      && sceneIdentity !== undefined
      && String(candidate.sceneId) === String(sceneIdentity)
    )
    const approvedCharacterOptions = characterReferenceAssets.value.filter(candidate =>
      candidate.status === 'APPROVED'
    )
    bindingDialog.sceneAssets = sortReferenceOptions(mergeReferenceOptions(approvedSceneOptions, currentScene ? [currentScene] : []))
    bindingDialog.characterAssets = sortReferenceOptions(mergeReferenceOptions(approvedCharacterOptions, currentCharacters))
    bindingDialog.sceneReferenceAssetId = currentScene?.assetId || null
    bindingDialog.characterReferenceAssetIds = currentCharacters.map(item => item.assetId)
    bindingDialog.currentCharacterReferences = currentCharacters
    bindingDialog.requiredCharacterCount = currentCharacters.length
    bindingDialog.editableInPlace = detail.editableInPlace === true
    bindingDialog.bindingMode = detail.bindingMode || 'AUTO'
    const missingScene = !bindingDialog.sceneAssets.length
    const missingCurrentCharacters = currentCharacters.some(current => current.status !== 'APPROVED')
    if (missingScene || missingCurrentCharacters) {
      bindingDialog.selectionMessage = '请先在“人物三视图”和“空场景图”阶段完成对应参考图；只有已生成完成的版本可以绑定关键帧。'
    }
  } catch (error) {
    bindingDialog.loadError = errorMessage(error, '关键帧参考绑定读取失败')
  } finally {
    bindingDialog.loading = false
  }
}

function sortReferenceOptions(items) {
  return [...items].sort((left, right) => {
    const identityOrder = String(left.assetName || '').localeCompare(String(right.assetName || ''), 'zh-CN')
    return identityOrder || Number(right.versionNo || 0) - Number(left.versionNo || 0)
  })
}

function mergeReferenceOptions(...groups) {
  const optionById = new Map()
  groups.flat().filter(Boolean).forEach(asset => optionById.set(String(asset.assetId), asset))
  return Array.from(optionById.values())
}

function referenceOptionLabel(asset) {
  const dimensions = asset.width && asset.height ? `${asset.width}×${asset.height}` : '尺寸待定'
  const status = { APPROVED: '已生成', DRAFT: '待生成', GENERATING: '生成中', REJECTED: '生成失败' }[asset.status] || asset.status || '状态未知'
  return `${asset.assetName || `资产 #${asset.assetId}`} · v${asset.versionNo || 1} · ${status} · ${dimensions}`
}

async function saveManualKeyframeBinding() {
  if (!bindingSelectionReady.value) {
    proxy.$modal.msgWarning('请选择当前场景的空景版本；人物三视图可选 0–4 张且不能重复')
    return
  }
  bindingDialog.submitting = true
  try {
    const response = await updateAiVideoKeyframeReferenceBinding(bindingDialog.assetId, {
      mode: 'MANUAL',
      sceneReferenceAssetId: bindingDialog.sceneReferenceAssetId,
      characterReferenceAssetIds: bindingDialog.characterReferenceAssetIds
    })
    const draft = response?.data || null
    if (!draft?.assetId) throw new Error('绑定更新响应缺少关键帧草稿')
    bindingDialog.open = false
    await loadAssets()
    proxy.$modal.msgSuccess(String(draft.assetId) === String(bindingDialog.assetId)
      ? '已保存人工绑定，尚未调用图片模型'
      : `已创建关键帧 v${draft.versionNo || ''} 并保存人工绑定`)
    openPrompt(draft)
  } catch (error) {
    proxy.$modal.msgError(errorMessage(error, '人工绑定保存失败'))
  } finally {
    bindingDialog.submitting = false
  }
}

async function resetKeyframeBindingToAuto() {
  if (!bindingDialog.assetId) return
  bindingDialog.submitting = true
  try {
    const response = await resetAiVideoKeyframeReferenceBinding(bindingDialog.assetId)
    const draft = response?.data || null
    if (!draft?.assetId) throw new Error('自动绑定响应缺少关键帧草稿')
    bindingDialog.open = false
    await loadAssets()
    proxy.$modal.msgSuccess('已恢复自动匹配最新可用的人物和场景参考版本')
    openPrompt(draft)
  } catch (error) {
    proxy.$modal.msgError(errorMessage(error, '恢复自动绑定失败'))
  } finally {
    bindingDialog.submitting = false
  }
}

function openPrompt(asset) {
  Object.assign(promptDialog, {
    open: true,
    submitting: false,
    readonly: asset.status === 'APPROVED' || asset.status === 'GENERATED',
    title: asset.assetName || '素材提示词',
    assetId: asset.assetId,
    assetType: asset.assetType,
    promptText: asset.promptText || '',
    negativePromptText: asset.negativePromptText || ''
  })
}

async function submitPrompt() {
  if (!promptDialog.promptText.trim()) {
    proxy.$modal.msgWarning('请填写提示词')
    return
  }
  promptDialog.submitting = true
  try {
    if (promptDialog.assetType === 'VIDEO_CLIP') {
      await updateAiVideoAssetVideoPrompt(promptDialog.assetId, { promptText: promptDialog.promptText })
      await proxy.$modal.confirm('确认使用当前提示词生成视频吗？此操作会调用视频模型并产生费用。')
      await generateAiVideoAssetVideo(promptDialog.assetId)
    } else {
      if (promptDialog.assetType === 'SHOT_KEYFRAME') {
        const response = await getAiVideoKeyframeReferenceBinding(promptDialog.assetId)
        const detail = response?.data || {}
        const sceneReady = detail.sceneReference?.status === 'APPROVED'
        const characterReferences = Array.isArray(detail.characterReferences) ? detail.characterReferences : []
        const charactersReady = characterReferences.every(reference => reference.status === 'APPROVED')
        if (!sceneReady || !charactersReady) {
          proxy.$modal.msgWarning('关键帧绑定的人物三视图或空场景图尚未生成完成，请先完成前两个阶段')
          activeTab.value = !charactersReady ? 'characters' : 'scenes'
          return
        }
      }
      await updateAiVideoAssetPrompt(promptDialog.assetId, { promptText: promptDialog.promptText, negativePromptText: promptDialog.negativePromptText })
      await proxy.$modal.confirm('确认使用当前提示词生成图片吗？此操作会调用图片模型。')
      await generateAiVideoAssetImage(promptDialog.assetId)
    }
    promptDialog.open = false
    proxy.$modal.msgSuccess('生成任务已提交')
    await loadAssets()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') proxy.$modal.msgError(errorMessage(error, '生成任务提交失败'))
  } finally {
    promptDialog.submitting = false
  }
}

async function approveAsset(asset) {
  try {
    await approveAiVideoAsset(asset.assetId)
    proxy.$modal.msgSuccess('素材已确认')
    await loadAssets()
  } catch (error) {
    proxy.$modal.msgError(errorMessage(error, '素材确认失败'))
  }
}

async function retryAsset(asset) {
  try {
    await retryAiVideoAssetImage(asset.assetId)
    proxy.$modal.msgSuccess('已重新提交生成任务')
    await loadAssets()
  } catch (error) {
    proxy.$modal.msgError(errorMessage(error, '重新生成失败'))
  }
}

async function deleteAsset(asset) {
  try {
    await proxy.$modal.confirm(`确认删除“${asset.assetName || `素材 #${asset.assetId}`}”吗？`)
    await delAiVideoAsset(asset.assetId)
    proxy.$modal.msgSuccess('素材已删除')
    await loadAssets()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') proxy.$modal.msgError(errorMessage(error, '素材删除失败'))
  }
}

async function prepareVideoDrafts() {
  const approvedKeyframes = approvedKeyframeAssets.value
  if (!approvedKeyframes.length) {
    proxy.$modal.msgWarning('请先生成并确认至少一张分镜关键帧')
    activeTab.value = 'shots'
    return
  }
  preparing.value = true
  try {
    const results = await Promise.allSettled(approvedKeyframes.map(asset => createAiVideoAssetVideoDraft(asset.assetId)))
    const failed = results.filter(result => result.status === 'rejected').length
    if (failed) proxy.$modal.msgWarning(`${failed} 个视频草稿准备失败，其余草稿已处理`)
    else proxy.$modal.msgSuccess('视频草稿已准备')
    activeTab.value = 'videos'
    await loadAssets()
  } finally {
    preparing.value = false
  }
}

function selectChapter(chapter) {
  router.push({ name: 'AiVedioChapterWorkspace', params: { projectId: projectId.value, chapterId: chapter.chapterId } })
}

function goBack() {
  router.push('/aiVedio/project')
}

function schedulePolling() {
  stopPolling()
  const busy = assets.value.some(asset => asset.status === 'GENERATING') || Object.values(taskByAssetId.value).some(task => ['PENDING', 'RUNNING', 'PROCESSING', 'GENERATING', 'SUBMITTED'].includes(task.status))
  if (busy) pollTimer = setTimeout(loadAssets, 5000)
}

function stopPolling() {
  if (pollTimer) clearTimeout(pollTimer)
  pollTimer = null
}

function errorMessage(error, fallback) {
  return error?.response?.data?.msg || error?.message || error?.msg || fallback
}

watch(() => [route.params.projectId, route.params.chapterId], loadContext)
onBeforeUnmount(stopPolling)
loadContext()
</script>

<style scoped>
.chapter-studio { min-height: calc(100dvh - 84px); padding: clamp(18px, 2.6vw, 36px); color: #edf1f5; background: radial-gradient(circle at 88% -8%, rgb(229 144 74 / 12%), transparent 30rem), #0c1118; font-family: "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif; }
.workspace-shell { display: grid; grid-template-columns: minmax(250px, 288px) minmax(0, 1fr); max-width: 1500px; gap: 18px; margin: 0 auto; }
.workspace-main { min-width: 0; min-height: calc(100dvh - 128px); padding: clamp(20px, 2.5vw, 32px); border: 1px solid #273241; border-radius: 10px 18px 18px 10px; background: rgb(18 25 34 / 92%); box-shadow: inset 0 1px rgb(255 255 255 / 4%), 0 24px 70px rgb(0 0 0 / 24%); }
.workspace-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; padding-bottom: 22px; border-bottom: 1px solid #263140; }
.eyebrow { margin: 0 0 7px; color: #e5904a; font-size: 10px; font-weight: 800; letter-spacing: .13em; text-transform: uppercase; }
.workspace-header h1 { margin: 0 0 7px; font-size: clamp(28px, 3vw, 42px); line-height: 1.08; letter-spacing: -.04em; }
.workspace-header p:not(.eyebrow) { margin: 0; color: #8490a0; font-size: 13px; }
.header-actions { display: flex; gap: 9px; }
.chapter-studio :deep(.el-button--primary) { --el-button-bg-color: #e5904a; --el-button-border-color: #e5904a; --el-button-text-color: #17120e; --el-button-hover-bg-color: #f0a15d; --el-button-hover-border-color: #f0a15d; --el-button-hover-text-color: #17120e; }
.context-alert { margin: 18px 0; }
.overview-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; margin: 20px 0 16px; }
.overview-grid button { display: grid; grid-template-columns: 1fr auto; grid-template-rows: auto auto; gap: 4px 12px; padding: 15px 16px; border: 1px solid #293443; border-radius: 10px; color: #7d8999; background: #101720; font: inherit; text-align: left; cursor: pointer; transition: border-color .2s ease, transform .2s ease, background-color .2s ease; }
.overview-grid button:hover, .overview-grid button.active { border-color: #59422f; background: rgb(229 144 74 / 6%); transform: translateY(-1px); }
.overview-grid small { color: #b57845; font-family: Consolas, monospace; font-size: 8px; letter-spacing: .08em; }
.overview-grid strong { grid-row: 1 / span 2; grid-column: 2; align-self: center; color: #e5904a; font-size: 22px; font-variant-numeric: tabular-nums; }
.overview-grid span { color: #cbd2da; font-size: 11px; font-weight: 650; }
.workspace-tabs { display: flex; gap: 4px; margin-bottom: 24px; padding: 4px; border: 1px solid #263140; border-radius: 9px; background: #0f161e; }
.workspace-tabs button { flex: 1; padding: 9px 12px; border: 0; border-radius: 6px; color: #788596; background: transparent; font: inherit; font-size: 11px; font-weight: 650; cursor: pointer; }
.workspace-tabs button.active { color: #f0e8e1; background: #202a36; box-shadow: inset 0 -2px #e5904a; }
.prompt-form { margin-top: 18px; }
.binding-status-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin: 16px 0 12px; color: #7d8999; font-size: 12px; }
.binding-alert { margin-bottom: 12px; }
.binding-form :deep(.el-select) { width: 100%; }
.binding-help { display: block; margin-top: 8px; color: #7a8695; font-size: 11px; line-height: 1.6; }
@media (max-width: 900px) { .workspace-shell { grid-template-columns: 1fr; } .workspace-main { min-height: auto; border-radius: 15px; } }
@media (max-width: 1100px) { .overview-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 680px) { .chapter-studio { padding: 12px; } .workspace-main { padding: 16px; } .workspace-header { align-items: flex-start; flex-direction: column; } .header-actions { width: 100%; } .header-actions :deep(.el-button) { flex: 1; } .overview-grid { grid-template-columns: 1fr; } .workspace-tabs { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); } .binding-status-row { align-items: flex-start; flex-direction: column; } }
</style>
