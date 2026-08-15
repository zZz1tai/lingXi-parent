<template>
  <div class="nk-outline">
    <button
      class="nk-btn nk-ai-outline"
      type="button"
      :disabled="generating"
      title="AI 依据作品设定与已有章节，重建全书→卷→章三层大纲，并检查断链"
      @click="handleGenerate"
    >
      <el-icon><MagicStick /></el-icon>{{ generating ? 'AI 推演中…' : 'AI 生成三层大纲' }}
    </button>

    <div v-if="gaps.length" class="nk-outline-gaps">
      <p class="nk-outline-label">断链报告</p>
      <div v-for="(gap, index) in gaps" :key="index" class="nk-outline-gap">
        <div class="nk-outline-gap-head">
          <span class="nk-outline-gap-tag">{{ gapTag(gap.issue) }}</span>
          <span class="nk-outline-gap-name">第 {{ gap.chapterNo }} 章「{{ gap.chapterTitle || '未命名' }}」</span>
        </div>
        <p class="nk-outline-gap-suggest">{{ gap.suggestion || '请人工核对' }}</p>
      </div>
    </div>

    <div v-if="tree.length" class="nk-outline-tree">
      <div v-for="book in tree" :key="book.outlineId" class="nk-outline-book">
        <div class="nk-outline-node-head">
          <span class="nk-outline-node-title">{{ book.outlineTitle || '全书' }}</span>
          <el-dropdown trigger="click" placement="bottom-end" @command="command => handleCommand(command, book)" @click.stop>
            <el-icon class="nk-chapter-more"><MoreFilled /></el-icon>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="add"><el-icon><Plus /></el-icon><span>加一卷</span></el-dropdown-item>
                <el-dropdown-item command="edit"><el-icon><EditPen /></el-icon><span>编辑全书总纲</span></el-dropdown-item>
                <el-dropdown-item command="delete" divided class="danger-item">
                  <el-icon><Delete /></el-icon><span>删除全书大纲</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <p v-if="book.outlineContent" class="nk-outline-content">{{ book.outlineContent }}</p>

        <div v-for="volume in book.children" :key="volume.outlineId" class="nk-outline-volume">
          <div class="nk-outline-node-head">
            <span class="nk-outline-node-title">{{ volumeTitle(volume) }}</span>
            <el-dropdown trigger="click" placement="bottom-end" @command="command => handleCommand(command, volume)" @click.stop>
              <el-icon class="nk-chapter-more"><MoreFilled /></el-icon>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="add"><el-icon><Plus /></el-icon><span>加一章</span></el-dropdown-item>
                  <el-dropdown-item command="edit"><el-icon><EditPen /></el-icon><span>编辑卷纲</span></el-dropdown-item>
                  <el-dropdown-item command="delete" divided class="danger-item">
                    <el-icon><Delete /></el-icon><span>删除本卷及章</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
          <p v-if="volume.outlineContent" class="nk-outline-content">{{ volume.outlineContent }}</p>

          <div v-for="chapter in volume.children" :key="chapter.outlineId" class="nk-outline-chapter">
            <div class="nk-outline-node-head">
              <span class="nk-outline-node-title">
                <span class="nk-outline-chapter-no">第 {{ chapter.chapterNo || '?' }} 章</span>
                {{ chapter.outlineTitle }}
              </span>
              <el-dropdown trigger="click" placement="bottom-end" @command="command => handleCommand(command, chapter)" @click.stop>
                <el-icon class="nk-chapter-more"><MoreFilled /></el-icon>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="edit"><el-icon><EditPen /></el-icon><span>编辑章纲</span></el-dropdown-item>
                    <el-dropdown-item command="delete" divided class="danger-item">
                      <el-icon><Delete /></el-icon><span>删除章纲</span>
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
            <p v-if="chapter.outlineContent" class="nk-outline-content">{{ chapter.outlineContent }}</p>
          </div>
        </div>
      </div>
    </div>
    <div v-else-if="!generating" class="nk-outline-empty">还没有大纲，点上方按钮让 AI 推演全书骨架</div>

    <el-dialog
      v-model="dialog.open"
      :title="dialogTitle"
      width="420px"
      append-to-body
      class="nk-outline-dialog"
    >
      <el-form label-position="top">
        <el-form-item label="标题">
          <el-input v-model="dialog.form.title" maxlength="128" placeholder="如：第一卷 · 青云起势" />
        </el-form-item>
        <el-form-item v-if="dialogLevel === 'CHAPTER'" label="计划章节号">
          <el-input-number
            v-model="dialog.form.chapterNo"
            :min="1"
            :max="99999"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="大纲内容">
          <el-input
            v-model="dialog.form.content"
            type="textarea"
            :rows="5"
            maxlength="4000"
            show-word-limit
            placeholder="概述本层要推进的情节与目标"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.open = false">取消</el-button>
        <el-button type="primary" :loading="dialog.submitting" @click="submitDialog">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { Delete, EditPen, MagicStick, MoreFilled, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addNovelOutline, delNovelOutline, generateNovelOutline,
  listNovelOutline, updateNovelOutline
} from '@/api/novel/novel'

const props = defineProps({
  workId: { type: Number, required: true }
})

const outlines = ref([])
const gaps = ref([])
const generating = ref(false)

const tree = computed(() => {
  const nodes = outlines.value
  return nodes
    .filter(node => node.outlineLevel === 'BOOK')
    .map(book => ({
      ...book,
      children: nodes
        .filter(node => node.outlineLevel === 'VOLUME' && node.parentId === book.outlineId)
        .map(volume => ({
          ...volume,
          children: nodes
            .filter(node => node.outlineLevel === 'CHAPTER' && node.parentId === volume.outlineId)
            .sort((a, b) => (a.chapterNo || 0) - (b.chapterNo || 0))
        }))
        .sort((a, b) => (a.seqNo || 0) - (b.seqNo || 0))
    }))
})

const dialog = reactive({
  open: false,
  submitting: false,
  mode: 'edit', // edit | add
  node: null,
  targetLevel: '',
  form: { title: '', content: '', chapterNo: null }
})

const dialogLevel = computed(() =>
  dialog.mode === 'add' ? dialog.targetLevel : dialog.node?.outlineLevel
)

const dialogTitle = computed(() => {
  if (!dialog.node) return '大纲节点'
  const names = { BOOK: '全书总纲', VOLUME: '卷纲', CHAPTER: '章纲' }
  return dialog.mode === 'add' ? `新增${names[dialog.targetLevel] || '节点'}` : `编辑${names[dialog.node.outlineLevel] || '节点'}`
})

const gapLabels = { ORPHAN_CHAPTER: '游离章节', MISSING_CHAPTER: '大纲缺章', MISMATCH: '标题不一致' }

function gapTag(issue) {
  return gapLabels[issue] || issue || '问题'
}

function volumeTitle(volume) {
  return volume.outlineTitle || `卷 · ${volume.seqNo || ''}`.trim()
}

async function loadOutline() {
  try {
    const result = await listNovelOutline(props.workId)
    outlines.value = result?.rows || []
  } catch {
    outlines.value = []
  }
}

async function handleGenerate() {
  if (generating.value) return
  generating.value = true
  try {
    const data = await generateNovelOutline(props.workId)
    gaps.value = data?.gaps || []
    ElMessage.success(`大纲已生成，断链项 ${gaps.value.length} 条`)
    await loadOutline()
  } catch (error) {
    gaps.value = []
    ElMessage.error(error?.message || 'AI 生成大纲失败，请稍后重试')
  } finally {
    generating.value = false
  }
}

function handleCommand(command, node) {
  if (command === 'add') openAddDialog(node)
  else if (command === 'edit') openEditDialog(node)
  else if (command === 'delete') handleDelete(node)
}

function openAddDialog(parent) {
  const childLevel = { BOOK: 'VOLUME', VOLUME: 'CHAPTER' }[parent.outlineLevel]
  if (!childLevel) return
  dialog.mode = 'add'
  dialog.node = parent
  dialog.targetLevel = childLevel
  const nextChapterNo = Math.max(
    0,
    ...outlines.value
      .filter(node => node.outlineLevel === 'CHAPTER')
      .map(node => Number(node.chapterNo) || 0)
  ) + 1
  const defaults = { VOLUME: '新一卷', CHAPTER: '新一章' }
  dialog.form = {
    title: defaults[childLevel] || '新节点',
    content: '',
    chapterNo: childLevel === 'CHAPTER' ? nextChapterNo : null
  }
  dialog.open = true
}

function openEditDialog(node) {
  dialog.mode = 'edit'
  dialog.node = node
  dialog.targetLevel = ''
  dialog.form = {
    title: node.outlineTitle || '',
    content: node.outlineContent || '',
    chapterNo: node.chapterNo || null
  }
  dialog.open = true
}

async function submitDialog() {
  if (!dialog.form.title.trim()) {
    ElMessage.warning('请填写标题')
    return
  }
  if (dialogLevel.value === 'CHAPTER' && (!dialog.form.chapterNo || dialog.form.chapterNo < 1)) {
    ElMessage.warning('请填写有效的计划章节号')
    return
  }
  dialog.submitting = true
  try {
    const isAdd = dialog.mode === 'add'
    const payload = {
      outlineId: dialog.node.outlineId,
      outlineTitle: dialog.form.title,
      outlineContent: dialog.form.content,
      outlineLevel: dialogLevel.value,
      parentId: isAdd ? dialog.node.outlineId : dialog.node.parentId,
      chapterNo: dialogLevel.value === 'CHAPTER' ? dialog.form.chapterNo : null
    }
    if (isAdd) {
      payload.outlineId = null
      await addNovelOutline(props.workId, payload)
      ElMessage.success('已挂入大纲')
    } else {
      await updateNovelOutline(props.workId, payload)
      ElMessage.success('大纲已更新')
    }
    dialog.open = false
    await loadOutline()
  } catch (error) {
    ElMessage.error(error?.message || '保存失败')
  } finally {
    dialog.submitting = false
  }
}

function handleDelete(node) {
  const names = { BOOK: '全书大纲', VOLUME: `本卷「${node.outlineTitle || ''}」`, CHAPTER: `第 ${node.chapterNo || '?'} 章章纲` }
  ElMessageBox.confirm(
    `确定删除${names[node.outlineLevel] || '该节点'}吗？${node.outlineLevel === 'VOLUME' ? '其下章纲将一并删除。' : ''}`,
    '撕掉这条大纲',
    { confirmButtonText: '删除', cancelButtonText: '留着', type: 'warning' }
  )
    .then(async () => {
      try {
        await delNovelOutline(props.workId, node.outlineId)
        ElMessage.success('已删除')
        await loadOutline()
      } catch (error) {
        ElMessage.error(error?.message || '删除失败')
      }
    })
    .catch(() => {})
}

loadOutline()
</script>

<style scoped>
.nk-outline { display: flex; flex-direction: column; gap: 12px; }

.nk-ai-outline {
  width: 100%;
  background: linear-gradient(135deg, var(--nk-sienna), #c65b1e);
  color: #fff;
  border: none;
  border-radius: 10px;
  padding: 10px 14px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  box-shadow: 0 4px 12px rgba(166, 84, 38, 0.28);
}
.nk-ai-outline:disabled { opacity: 0.6; cursor: not-allowed; }

.nk-outline-label {
  font-size: 12px;
  color: var(--nk-ink-faint);
  margin: 0 0 6px;
  font-weight: 600;
  letter-spacing: 0.05em;
}

.nk-outline-gaps {
  background: #fdf6ec;
  border: 1px solid #f3e0c0;
  border-radius: 10px;
  padding: 10px 12px;
}
.nk-outline-gap { padding: 6px 0; border-bottom: 1px dashed #f0e2c8; }
.nk-outline-gap:last-child { border-bottom: none; }
.nk-outline-gap-head { display: flex; align-items: center; gap: 8px; }
.nk-outline-gap-tag {
  font-size: 11px;
  color: var(--nk-sienna);
  background: #f5e3cc;
  border-radius: 4px;
  padding: 1px 6px;
  flex: 0 0 auto;
}
.nk-outline-gap-name { font-size: 12px; font-weight: 600; color: var(--nk-ink); }
.nk-outline-gap-suggest { margin: 4px 0 0; font-size: 12px; color: var(--nk-ink-soft); line-height: 1.5; }

.nk-outline-tree { display: flex; flex-direction: column; gap: 10px; }

.nk-outline-book {
  background: #fbf7f0;
  border: 1px solid #ead9c0;
  border-radius: 12px;
  padding: 10px 12px;
}
.nk-outline-volume {
  margin-top: 8px;
  background: #fff;
  border: 1px solid #eee2d2;
  border-radius: 10px;
  padding: 8px 10px;
}
.nk-outline-chapter {
  margin-top: 6px;
  border-left: 2px solid #e8d5bc;
  padding: 4px 0 4px 10px;
}

.nk-outline-node-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.nk-outline-node-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--nk-ink);
  line-height: 1.4;
}
.nk-outline-book > .nk-outline-node-head .nk-outline-node-title { font-size: 15px; }
.nk-outline-volume > .nk-outline-node-head .nk-outline-node-title { color: var(--nk-sienna); }
.nk-outline-chapter-no {
  font-weight: 600;
  color: var(--nk-ink-faint);
  margin-right: 6px;
  font-size: 12px;
}
.nk-outline-content {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--nk-ink-soft);
  line-height: 1.6;
  white-space: pre-wrap;
}
.nk-outline-empty {
  text-align: center;
  color: var(--nk-ink-faint);
  font-size: 12px;
  padding: 28px 0;
}
.nk-chapter-more { color: var(--nk-ink-faint); cursor: pointer; }
.nk-chapter-more:hover { color: var(--nk-sienna); }
.danger-item :deep(.el-dropdown-menu__item) { color: var(--nk-seal); }
</style>
