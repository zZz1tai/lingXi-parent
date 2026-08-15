<template>
  <div class="nk-page" :class="{ 'is-resizing': activeResizePanel }">
    <div ref="layoutBodyRef" class="nk-body" :style="bodyLayoutStyle">
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

      <div
        class="nk-layout-splitter is-outer"
        :class="{ 'is-active': activeResizePanel === 'rail' }"
        role="separator"
        tabindex="0"
        aria-orientation="vertical"
        aria-label="调整书斋与创作区宽度"
        :aria-valuemin="NOVEL_LAYOUT_LIMITS.railMin"
        :aria-valuemax="NOVEL_LAYOUT_LIMITS.railMax"
        :aria-valuenow="Math.round(novelLayout.railWidth)"
        title="拖动调整书斋宽度；双击恢复默认"
        @pointerdown="startPanelResize('rail', $event)"
        @keydown="handleSplitterKeydown('rail', $event)"
        @dblclick="resetPanelWidth('rail')"
      >
        <span class="nk-layout-splitter-grip" aria-hidden="true"></span>
      </div>

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
            <button
              v-if="selectedWork.workType === 'novel'"
              class="nk-btn"
              type="button"
              :disabled="!currentChapter || !manuscript.trim() || contextSyncApplying"
              :title="contextSyncError || '分析已保存章节中的设定与伏笔变化，确认后再写回'"
              @click="handleContextSyncClick"
            >
              <el-icon :class="{ 'is-loading': contextSyncLoading }">
                <component :is="contextSyncLoading ? Loading : MagicStick" />
              </el-icon>
              {{ contextSyncStatusText }}
            </button>
            <button
              class="nk-btn"
              type="button"
              :disabled="!manuscript.trim()"
              title="分析当前正文的节奏评分与修改建议"
              @click="openPacingDialog"
            >
              <el-icon><MagicStick /></el-icon>
              {{ currentPacingRecord ? `节奏分析 ${currentPacingRecord.result.score} 分` : '节奏分析' }}
            </button>
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
          <div class="nk-empty-actions">
            <button class="nk-btn is-primary" type="button" @click="openWorkDialog()">
              <el-icon><Plus /></el-icon>新建作品
            </button>
            <button class="nk-btn nk-idea-entry" type="button" @click="ideaStudioOpen = true">
              <el-icon><MagicStick /></el-icon>让 AI 帮我构思
            </button>
          </div>
        </div>

        <!-- 工作区：对话 + 手稿 + （长篇）抽屉 -->
        <template v-if="selectedWork">
          <div ref="workspaceRef" class="nk-workspace" :style="workspaceLayoutStyle">
            <div class="nk-chat-col">
              <ChatComposer
                ref="chatComposerRef"
                :work="selectedWork"
                :chapter="currentChapter"
                :manuscript="manuscript"
                :style-cards="settingStyles"
                :user-id="userStore.id"
                :user-name="userStore.name"
                @insert="handleInsert"
              />
            </div>

            <div
              class="nk-layout-splitter"
              :class="{ 'is-active': activeResizePanel === 'chat' }"
              role="separator"
              tabindex="0"
              aria-orientation="vertical"
              aria-label="调整 AI 对话与正文编辑宽度"
              :aria-valuemin="NOVEL_LAYOUT_LIMITS.chatMin"
              :aria-valuemax="NOVEL_LAYOUT_LIMITS.chatMax"
              :aria-valuenow="Math.round(novelLayout.chatWidth)"
              title="拖动调整 AI 对话宽度；双击恢复默认"
              @pointerdown="startPanelResize('chat', $event)"
              @keydown="handleSplitterKeydown('chat', $event)"
              @dblclick="resetPanelWidth('chat')"
            >
              <span class="nk-layout-splitter-grip" aria-hidden="true"></span>
            </div>

            <div class="nk-paper-col">
              <ManuscriptEditor
                v-model="manuscript"
                :title="currentChapter?.chapterTitle || selectedWork.workName"
                :word-count="manuscriptWordCount"
                :placeholder="selectedWork.workType === 'novel' ? '在右侧目录中新起一章，或让 AI 续写下一章…' : '提笔，写下属于你的故事…'"
              />
            </div>

            <div
              class="nk-layout-splitter"
              :class="{ 'is-active': activeResizePanel === 'drawer' }"
              role="separator"
              tabindex="0"
              aria-orientation="vertical"
              aria-label="调整正文编辑与资料栏宽度"
              :aria-valuemin="NOVEL_LAYOUT_LIMITS.drawerMin"
              :aria-valuemax="NOVEL_LAYOUT_LIMITS.drawerMax"
              :aria-valuenow="Math.round(novelLayout.drawerWidth)"
              title="拖动调整右侧资料栏宽度；双击恢复默认"
              @pointerdown="startPanelResize('drawer', $event)"
              @keydown="handleSplitterKeydown('drawer', $event)"
              @dblclick="resetPanelWidth('drawer')"
            >
              <span class="nk-layout-splitter-grip" aria-hidden="true"></span>
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
                <button
                  type="button"
                  class="nk-drawer-tab"
                  :class="{ 'is-active': drawerTab === 'foreshadows' }"
                  @click="drawerTab = 'foreshadows'"
                >
                  伏笔
                </button>
                <button
                  type="button"
                  class="nk-drawer-tab"
                  :class="{ 'is-active': drawerTab === 'outline' }"
                  @click="drawerTab = 'outline'"
                >
                  大纲
                </button>
              </div>

              <div class="nk-drawer-body">
                <template v-if="drawerTab === 'chapters'">
                  <button
                    class="nk-btn nk-ai-next-chapter"
                    type="button"
                    :disabled="!chapters.length || aiNextChapterLoading"
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

                <template v-else-if="drawerTab === 'settings'">
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
                    <p class="nk-settings-label">文风</p>
                    <SettingNotebook
                      type="style"
                      :cards="settingStyles"
                      @add="openSettingDialog"
                      @edit="openSettingDialog"
                      @delete="handleDeleteSetting"
                    />
                  </div>
                  <div v-if="settingOthers.length" class="nk-settings-group">
                    <p class="nk-settings-label">构思档案</p>
                    <SettingNotebook
                      type="other"
                      :cards="settingOthers"
                      @add="openSettingDialog"
                      @edit="openSettingDialog"
                      @delete="handleDeleteSetting"
                    />
                  </div>
                </template>

                <template v-else-if="drawerTab === 'foreshadows'">
                  <div class="nk-settings-group">
                    <p class="nk-settings-label">伏笔</p>
                    <ForeshadowBoard
                      :cards="foreshadows"
                      @add="openForeshadowDialog()"
                      @edit="openForeshadowDialog"
                      @delete="handleDeleteForeshadow"
                      @resolve="handleResolveForeshadow"
                    />
                  </div>
                </template>

                <template v-else-if="drawerTab === 'outline'">
                  <OutlinePanel v-if="selectedWork" :work-id="selectedWork.workId" />
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
      <button
        v-if="!workDialog.isEdit"
        class="nk-work-idea-bridge"
        type="button"
        @click="workDialog.open = false; ideaStudioOpen = true"
      >
        <el-icon><MagicStick /></el-icon>
        <span><strong>还只有一个模糊念头？</strong>让构思编辑通过几轮追问整理人物、冲突与世界观</span>
        <em>开始构思</em>
      </button>
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
        <el-form-item label="节奏档位" prop="pacingLevel">
          <el-select v-model="workForm.pacingLevel" class="nk-pacing-select">
            <el-option
              v-for="item in PACING_LEVELS"
              :key="item.id"
              :label="`${item.label} · ${item.desc}`"
              :value="item.id"
            />
          </el-select>
          <div class="nk-form-hint">AI 续写与精修会按所选节奏控制信息密度与推进速度。</div>
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

    <IdeaStudioDialog v-model="ideaStudioOpen" @created="handleIdeaWorkCreated" />

    <!-- 章节节奏分析 -->
    <el-dialog
      v-model="pacingDialog.open"
      title="章节节奏分析"
      width="620px"
      append-to-body
      class="nk-dialog-paper nk-pacing-dialog"
    >
      <div v-if="pacingLoading" class="nk-pacing-loading">
        <el-icon class="is-loading"><Loading /></el-icon>
        <p>正在品读这一章的呼吸与脉动…</p>
      </div>
      <template v-else-if="pacingDialog.result">
        <div class="nk-pacing-head">
          <div class="nk-pacing-score">
            <strong>{{ pacingDialog.result.score }}</strong>
            <span>节奏分 / 100</span>
          </div>
          <div class="nk-pacing-meta">
            <p class="nk-pacing-level">
              目标档位 {{ pacingLabel(pacingDialog.level) }}
              <el-tag size="small" type="warning">实际 {{ pacingDialog.result.levelLabel }}</el-tag>
            </p>
            <p class="nk-pacing-note">{{ pacingDialog.result.scoreNote }}</p>
          </div>
        </div>
        <p class="nk-pacing-levelnote">{{ pacingDialog.result.levelNote }}</p>
        <p class="nk-pacing-summary">{{ pacingDialog.result.summary }}</p>

        <div class="nk-pacing-grid">
          <div
            v-for="dim in pacingDialog.result.dimensions"
            :key="dim.name"
            class="nk-pacing-dim"
          >
            <div class="nk-pacing-dim-head">
              <span>{{ dim.name }}</span>
              <strong>{{ dim.score }}</strong>
            </div>
            <el-progress :percentage="dim.score" :show-text="false" :stroke-width="8" />
            <p class="nk-pacing-dim-note">{{ dim.note }}</p>
          </div>
        </div>

        <template v-if="pacingDialog.result.issues.length">
          <h4 class="nk-pacing-section-title">发现的问题</h4>
          <ul class="nk-pacing-issues">
            <li v-for="(issue, index) in pacingDialog.result.issues" :key="index">
              <el-tag size="small" type="danger">{{ issue.typeLabel }}</el-tag>
              <span v-if="issue.position" class="nk-pacing-pos">[{{ issue.position }}]</span>
              {{ issue.issue }}
              <p class="nk-pacing-fix">{{ issue.suggestion }}</p>
            </li>
          </ul>
        </template>

        <template v-if="pacingDialog.result.suggestions.length">
          <h4 class="nk-pacing-section-title">优化建议</h4>
          <ul class="nk-pacing-suggestions">
            <li v-for="(item, index) in pacingDialog.result.suggestions" :key="index">{{ item }}</li>
          </ul>
        </template>
      </template>
      <template #footer>
        <el-button @click="pacingDialog.open = false">关闭</el-button>
        <el-button
          type="primary"
          :loading="pacingLoading"
          :disabled="!manuscript.trim()"
          @click="runPacingAnalysis"
        >
          重新分析
        </el-button>
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

    <!-- 伏笔编辑 -->
    <el-dialog
      v-model="foreshadowDialog.open"
      :title="foreshadowDialog.isEdit ? '修改伏笔' : '埋一条新伏笔'"
      width="520px"
      append-to-body
      class="nk-dialog-paper"
    >
      <el-form label-position="top">
        <el-form-item label="伏笔名称">
          <el-input v-model="foreshadowForm.title" maxlength="32" placeholder="例如：林家祠堂里那只断手镯" />
        </el-form-item>
        <el-form-item label="伏笔详情">
          <el-input
            v-model="foreshadowForm.description"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
            placeholder="埋了什么、打算怎么收、读者会留意到什么…"
          />
        </el-form-item>
        <div class="nk-foreshadow-row">
          <el-form-item label="状态">
            <el-select v-model="foreshadowForm.status" class="nk-foreshadow-col">
              <el-option label="已埋" value="buried" />
              <el-option label="待解" value="pending" />
              <el-option label="已解" value="resolved" />
            </el-select>
          </el-form-item>
          <el-form-item label="重要等级">
            <el-select v-model="foreshadowForm.priority" class="nk-foreshadow-col">
              <el-option label="高" value="high" />
              <el-option label="中" value="medium" />
              <el-option label="低" value="low" />
            </el-select>
          </el-form-item>
        </div>
        <div class="nk-foreshadow-row">
          <el-form-item label="关键词">
            <el-input v-model="foreshadowForm.keyword" maxlength="32" placeholder="便于检索，如：断手镯" />
          </el-form-item>
          <el-form-item label="计划回收章节">
            <el-input-number v-model="foreshadowForm.resolveChapterNo" :min="1" :max="100000" class="nk-foreshadow-col" placeholder="可留空" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="foreshadowDialog.open = false">取消</el-button>
        <el-button type="primary" :loading="foreshadowDialog.submitting" @click="submitForeshadow">保存伏笔</el-button>
      </template>
    </el-dialog>

    <NovelContextSyncDialog
      v-model="contextSyncDialog.open"
      :suggestions="contextSyncDialog.suggestions"
      :chapter-title="contextSyncDialog.chapterTitle"
      :applying="contextSyncApplying"
      @apply="handleApplyContextChanges"
    />
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import {
  Delete, Download, EditPen, Loading, MagicStick, Plus, Check, CircleCheckFilled, WarningFilled
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import useUserStore from '@/store/modules/user'
import {
  addNovelChapter, addNovelForeshadow, addNovelSetting, addNovelWork, analyzeNovelContext, analyzeNovelPacing,
  applyNovelContextChanges,
  delNovelChapter, delNovelForeshadow, delNovelSetting,
  delNovelWork, exportNovelWorkText, getNovelWork, listNovelChapter, listNovelForeshadow, listNovelSetting,
  listNovelWork, saveNovelManuscript, streamNovelSynopsis,
  updateNovelChapter, updateNovelForeshadow, updateNovelSetting, updateNovelWork
} from '@/api/novel/novel'
import WorksRail from './components/WorksRail.vue'
import ChatComposer from './components/ChatComposer.vue'
import ManuscriptEditor from './components/ManuscriptEditor.vue'
import ChapterTree from './components/ChapterTree.vue'
import SettingNotebook from './components/SettingNotebook.vue'
import ForeshadowBoard from './components/ForeshadowBoard.vue'
import OutlinePanel from './components/OutlinePanel.vue'
import IdeaStudioDialog from './components/IdeaStudioDialog.vue'
import NovelContextSyncDialog from './components/NovelContextSyncDialog.vue'
import { countNovelCharacters } from './novelWordCount'
import {
  DEFAULT_PACING_LEVEL, PACING_CACHE_STORAGE_KEY, PACING_LEVELS,
  buildPacingCacheKey, buildPacingRequest, normalizePacingResult, pacingLabel, parsePacingCache
} from './novelPacing'
import {
  DEFAULT_NOVEL_LAYOUT, NOVEL_LAYOUT_LIMITS, NOVEL_LAYOUT_STORAGE_KEY,
  fitNovelLayout, parseNovelLayout, resizeNovelPanel
} from './novelLayout'
import {
  CONTEXT_SYNC_HASH_STORAGE_KEY, buildContextSyncKey, fingerprintNovelContent,
  normalizeContextChanges, parseContextSyncHashes, toContextApplyChanges
} from './novelContextSync'
import { nextNovelChapterNo, pickNovelChapter, planNovelContinuation } from './novelChapter'
import './novel-kraft.scss'

defineOptions({ name: 'NovelWriting' })

const userStore = useUserStore()

// ── 可拖拽分栏 ──────────────────────────────────────
const layoutBodyRef = ref(null)
const workspaceRef = ref(null)
const chatComposerRef = ref(null)
const activeResizePanel = ref('')
const novelLayout = reactive(parseNovelLayout(localStorage.getItem(NOVEL_LAYOUT_STORAGE_KEY)))

const bodyLayoutStyle = computed(() => ({
  '--nk-rail-width': `${novelLayout.railWidth}px`
}))

const workspaceLayoutStyle = computed(() => ({
  '--nk-chat-width': `${novelLayout.chatWidth}px`,
  '--nk-drawer-width': `${novelLayout.drawerWidth}px`
}))

function contentWidth(element) {
  if (!element) return Number.POSITIVE_INFINITY
  const style = window.getComputedStyle(element)
  return element.clientWidth - parseFloat(style.paddingLeft || 0) - parseFloat(style.paddingRight || 0)
}

function currentLayoutDimensions() {
  return {
    bodyWidth: contentWidth(layoutBodyRef.value),
    workspaceWidth: workspaceRef.value?.clientWidth ?? Number.POSITIVE_INFINITY
  }
}

function applyFittedLayout() {
  if (activeResizePanel.value) return
  Object.assign(novelLayout, fitNovelLayout(novelLayout, currentLayoutDimensions()))
}

function persistNovelLayout() {
  try {
    localStorage.setItem(NOVEL_LAYOUT_STORAGE_KEY, JSON.stringify(novelLayout))
  } catch (error) {
    console.warn('小说工作台分栏尺寸保存失败', error)
  }
}

let resizeSession = null

function startPanelResize(panel, event) {
  if (event.button !== 0) return
  event.preventDefault()
  resizeSession = {
    panel,
    pointerId: event.pointerId,
    handle: event.currentTarget,
    startX: event.clientX,
    startLayout: { ...novelLayout },
    ...currentLayoutDimensions()
  }
  activeResizePanel.value = panel
  event.currentTarget.setPointerCapture?.(event.pointerId)
  window.addEventListener('pointermove', handlePanelResize)
  window.addEventListener('pointerup', finishPanelResize)
  window.addEventListener('pointercancel', finishPanelResize)
}

function handlePanelResize(event) {
  if (!resizeSession || event.pointerId !== resizeSession.pointerId) return
  Object.assign(novelLayout, resizeNovelPanel({
    panel: resizeSession.panel,
    startLayout: resizeSession.startLayout,
    deltaX: event.clientX - resizeSession.startX,
    bodyWidth: resizeSession.bodyWidth,
    workspaceWidth: resizeSession.workspaceWidth
  }))
}

function finishPanelResize(event) {
  if (!resizeSession || (event?.pointerId != null && event.pointerId !== resizeSession.pointerId)) return
  const { handle, pointerId } = resizeSession
  if (handle?.hasPointerCapture?.(pointerId)) handle.releasePointerCapture(pointerId)
  resizeSession = null
  activeResizePanel.value = ''
  window.removeEventListener('pointermove', handlePanelResize)
  window.removeEventListener('pointerup', finishPanelResize)
  window.removeEventListener('pointercancel', finishPanelResize)
  persistNovelLayout()
}

function handleSplitterKeydown(panel, event) {
  if (!['ArrowLeft', 'ArrowRight'].includes(event.key)) return
  event.preventDefault()
  const step = event.shiftKey ? 48 : 16
  Object.assign(novelLayout, resizeNovelPanel({
    panel,
    startLayout: novelLayout,
    deltaX: event.key === 'ArrowRight' ? step : -step,
    ...currentLayoutDimensions()
  }))
  persistNovelLayout()
}

function resetPanelWidth(panel) {
  const propertyByPanel = {
    rail: 'railWidth',
    chat: 'chatWidth',
    drawer: 'drawerWidth'
  }
  const property = propertyByPanel[panel]
  novelLayout[property] = DEFAULT_NOVEL_LAYOUT[property]
  Object.assign(novelLayout, fitNovelLayout(novelLayout, currentLayoutDimensions()))
  persistNovelLayout()
}

let layoutObserver = null

onMounted(() => {
  nextTick(() => {
    applyFittedLayout()
    if (typeof ResizeObserver !== 'undefined' && layoutBodyRef.value) {
      layoutObserver = new ResizeObserver(applyFittedLayout)
      layoutObserver.observe(layoutBodyRef.value)
    }
  })
})

// ── 作品列表 ─────────────────────────────────────────
const works = ref([])
const loadingWorks = ref(false)
const apiReady = ref(true)
const category = ref('short')
const keyword = ref('')
const selectedWork = ref(null)
const ideaStudioOpen = ref(false)

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
  lastSavedContent.value = ''
  saveState.value = 'saved'
  chapters.value = []
  currentChapter.value = null
  settings.value = { character: [], world: [], outline: [], style: [], other: [] }
  foreshadows.value = []
  if (work.workType === 'novel') {
    loadChapters(work.workId)
  } else {
    loadManuscript(work.workId)
  }
}

// ── 作品 CRUD ───────────────────────────────────────
const workDialog = reactive({ open: false, isEdit: false, submitting: false, workId: null })
const workForm = reactive({
  workName: '',
  workType: 'short',
  genre: '',
  synopsis: '',
  pacingLevel: DEFAULT_PACING_LEVEL
})
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
      synopsis: work.synopsis || '',
      pacingLevel: work.pacingLevel || DEFAULT_PACING_LEVEL
    })
  } else {
    workDialog.isEdit = false
    workDialog.workId = null
    Object.assign(workForm, {
      workName: '',
      workType: category.value,
      genre: '',
      synopsis: '',
      pacingLevel: DEFAULT_PACING_LEVEL
    })
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
      synopsis: workForm.synopsis,
      pacingLevel: workForm.pacingLevel
    }
    if (workDialog.isEdit) {
      await updateNovelWork({ ...payload, workId: workDialog.workId })
      if (selectedWork.value?.workId === workDialog.workId) {
        selectedWork.value.pacingLevel = payload.pacingLevel
      }
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

async function handleIdeaWorkCreated(workId) {
  category.value = 'novel'
  await loadWorks()
  const created = works.value.find(work => String(work.workId) === String(workId))
  if (created) selectWork(created)
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

// ── 章节节奏分析 ─────────────────────────────────────
const pacingDialog = reactive({ open: false, level: DEFAULT_PACING_LEVEL, result: null })
const pacingLoading = ref(false)
const pacingRecords = reactive(parsePacingCache(localStorage.getItem(PACING_CACHE_STORAGE_KEY)))

function currentPacingCacheKey() {
  return buildPacingCacheKey({
    userId: userStore.id,
    workId: selectedWork.value?.workId,
    chapterId: selectedWork.value?.workType === 'novel' ? currentChapter.value?.chapterId : null
  })
}

const currentPacingRecord = computed(() => {
  const cacheKey = currentPacingCacheKey()
  return cacheKey ? pacingRecords[cacheKey] || null : null
})

function persistPacingRecords() {
  try {
    localStorage.setItem(PACING_CACHE_STORAGE_KEY, JSON.stringify(pacingRecords))
  } catch {
    ElMessage.warning('节奏分析已保留在当前页面，但浏览器缓存写入失败')
  }
}

function openPacingDialog() {
  if (!manuscript.value.trim()) {
    ElMessage.warning('先写一点正文，才能分析节奏')
    return
  }
  const cached = currentPacingRecord.value
  pacingDialog.level = cached?.level || selectedWork.value?.pacingLevel || DEFAULT_PACING_LEVEL
  pacingDialog.result = cached ? normalizePacingResult(cached.result) : null
  pacingDialog.open = true
  if (!pacingDialog.result) runPacingAnalysis()
}

async function runPacingAnalysis() {
  if (!manuscript.value.trim()) return
  const cacheKey = currentPacingCacheKey()
  if (!cacheKey) return
  const previousResult = pacingDialog.result
  const targetLevel = selectedWork.value?.pacingLevel || DEFAULT_PACING_LEVEL
  pacingLoading.value = true
  pacingDialog.result = null
  try {
    const { data } = await analyzeNovelPacing(
      buildPacingRequest({
        workName: selectedWork.value?.workName || '',
        genre: selectedWork.value?.genre || '',
        chapterTitle: currentChapter.value?.chapterTitle || '',
        pacingLevel: targetLevel,
        content: manuscript.value
      })
    )
    const result = normalizePacingResult(data)
    if (!result) throw new Error('AI 返回了空的节奏分析结果')
    pacingDialog.level = targetLevel
    pacingDialog.result = result
    pacingRecords[cacheKey] = {
      level: targetLevel,
      analyzedAt: new Date().toISOString(),
      result
    }
    persistPacingRecords()
  } catch (error) {
    pacingDialog.result = previousResult
    if (!previousResult) pacingDialog.open = false
    ElMessage.error(previousResult ? '重新分析失败，已保留上次结果' : (error?.message || '节奏分析失败，请稍后再试'))
  } finally {
    pacingLoading.value = false
  }
}

// ── 短篇正文 / 长篇章节 ─────────────────────────────
const manuscript = ref('')
const lastSavedContent = ref('')
const currentChapter = ref(null)
const chapters = ref([])
const aiNextChapterLoading = ref(false)

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
  lastSavedContent.value = manuscript.value
  saveState.value = 'saved'
  syncSelectedWorkWordCount()
}

async function loadChapters(workId, preferredChapter = {}) {
  try {
    const result = await listNovelChapter(workId)
    chapters.value = result?.rows || []
    const selected = pickNovelChapter(chapters.value, preferredChapter)
    if (selected) {
      await selectChapter(selected)
    }
  } catch {
    chapters.value = []
  }
}

async function selectChapter(chapter) {
  clearTimeout(saveTimer)
  clearTimeout(contextAnalysisTimer)
  if (contextSyncDialog.chapterId !== chapter.chapterId) {
    contextSyncDialog.open = false
    contextSyncDialog.suggestions = []
    contextSyncDialog.contentHash = ''
    contextSyncDialog.contentFingerprint = ''
  }
  contextSyncError.value = ''
  currentChapter.value = chapter
  manuscript.value = chapter.content || ''
  lastSavedContent.value = manuscript.value
  saveState.value = 'saved'
}

function handleInsert(text) {
  const insertion = text.trim()
  if (!insertion) return
  manuscript.value = manuscript.value ? `${manuscript.value.replace(/\s+$/, '')}\n\n${insertion}` : insertion
  markDirty()
}

async function handleAddChapter() {
  const order = nextNovelChapterNo(chapters.value)
  try {
    await addNovelChapter(selectedWork.value.workId, {
      chapterNo: order,
      chapterTitle: `第 ${order} 章`,
      content: ''
    })
    ElMessage.success(`已新起第 ${order} 章`)
    await loadChapters(selectedWork.value.workId, { chapterNo: order })
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
  if (!work || aiNextChapterLoading.value) return
  const composer = chatComposerRef.value
  if (!composer?.send) {
    ElMessage.error('AI 创作面板尚未就绪，请稍后重试')
    return
  }
  if (composer.isStreaming?.()) {
    ElMessage.warning('AI 正在创作，请等待当前续写完成')
    return
  }
  const plan = planNovelContinuation(chapters.value)
  if (!plan) {
    ElMessage.warning('先完成当前章节的正文，AI 才好接着往下写')
    return
  }
  const { chapterNo: order, createNew, sourceChapter, targetChapter } = plan
  aiNextChapterLoading.value = true
  try {
    if (createNew) {
      await addNovelChapter(work.workId, {
        chapterNo: order,
        chapterTitle: `第 ${order} 章`,
        content: ''
      })
    }
    await loadChapters(work.workId, targetChapter
      ? { chapterId: targetChapter.chapterId }
      : { chapterNo: order })
    await nextTick()
    const activeComposer = chatComposerRef.value
    if (!activeComposer?.send) {
      throw new Error('AI 创作面板尚未就绪，请稍后重试')
    }
    ElMessage.success(createNew
      ? `已新起第 ${order} 章，AI 正在续写…`
      : `正在第 ${order} 章重试 AI 续写…`)
    activeComposer.send(
      `续写下一章：衔接上一章《${sourceChapter.chapterTitle || `第 ${sourceChapter.chapterNo} 章`}》的结尾，自然地开启新章节，保持人物口吻与叙事风格一致。`,
      true
    )
  } catch (error) {
    ElMessage.error(error?.message || '自动续写下一章失败，请检查后端服务')
  } finally {
    aiNextChapterLoading.value = false
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
    await loadChapters(selectedWork.value.workId, {
      chapterId: chapterDialog.chapter.chapterId
    })
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
const settings = reactive({ character: [], world: [], outline: [], style: [], other: [] })

const settingCharacters = computed(() => settings.character)
const settingWorlds = computed(() => settings.world)
const settingOutlines = computed(() => settings.outline)
const settingStyles = computed(() => settings.style)
const settingOthers = computed(() => settings.other)

const settingDialog = reactive({ open: false, isEdit: false, submitting: false, type: 'character', settingId: null })
const settingForm = reactive({ title: '', content: '' })

const settingTypeLabel = computed(() => {
  const labels = { character: '人物', world: '世界观', outline: '大纲', style: '文风', other: '构思档案' }
  return labels[settingDialog.type] || '设定'
})

async function loadSettings(workId) {
  for (const type of ['character', 'world', 'style', 'other']) {
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

// ── 伏笔 ────────────────────────────────────────────
const foreshadows = ref([])

const foreshadowDialog = reactive({ open: false, isEdit: false, submitting: false, foreshadowId: null })
const foreshadowForm = reactive({
  title: '',
  description: '',
  status: 'buried',
  priority: 'medium',
  keyword: '',
  resolveChapterNo: null
})

async function loadForeshadows(workId) {
  try {
    const result = await listNovelForeshadow(workId)
    foreshadows.value = result?.rows || []
  } catch {
    foreshadows.value = []
  }
}

function openForeshadowDialog(card = null) {
  if (card) {
    foreshadowDialog.isEdit = true
    foreshadowDialog.foreshadowId = card.foreshadowId
    Object.assign(foreshadowForm, {
      title: card.title || '',
      description: card.description || '',
      status: card.status || 'buried',
      priority: card.priority || 'medium',
      keyword: card.keyword || '',
      resolveChapterNo: card.resolveChapterNo ?? null
    })
  } else {
    foreshadowDialog.isEdit = false
    foreshadowDialog.foreshadowId = null
    Object.assign(foreshadowForm, {
      title: '',
      description: '',
      status: 'buried',
      priority: 'medium',
      keyword: '',
      resolveChapterNo: null
    })
  }
  foreshadowDialog.open = true
}

async function submitForeshadow() {
  if (!foreshadowForm.title.trim()) {
    ElMessage.warning('请填写伏笔名称')
    return
  }
  foreshadowDialog.submitting = true
  try {
    const payload = {
      title: foreshadowForm.title.trim(),
      description: foreshadowForm.description,
      status: foreshadowForm.status,
      priority: foreshadowForm.priority,
      keyword: foreshadowForm.keyword?.trim() || null,
      resolveChapterNo: foreshadowForm.resolveChapterNo || null
    }
    if (foreshadowDialog.isEdit) {
      await updateNovelForeshadow(selectedWork.value.workId, { ...payload, foreshadowId: foreshadowDialog.foreshadowId })
      ElMessage.success('伏笔已更新')
    } else {
      await addNovelForeshadow(selectedWork.value.workId, payload)
      ElMessage.success('伏笔已埋下')
    }
    foreshadowDialog.open = false
    await loadForeshadows(selectedWork.value.workId)
  } catch (error) {
    ElMessage.error(error?.message || '保存失败，请检查后端服务')
  } finally {
    foreshadowDialog.submitting = false
  }
}

async function handleDeleteForeshadow(card) {
  try {
    await delNovelForeshadow(selectedWork.value.workId, card.foreshadowId)
    ElMessage.success('伏笔已删除')
    await loadForeshadows(selectedWork.value.workId)
  } catch (error) {
    ElMessage.error(error?.message || '删除失败')
  }
}

async function handleResolveForeshadow(card) {
  try {
    await updateNovelForeshadow(selectedWork.value.workId, {
      ...card,
      status: 'resolved'
    })
    ElMessage.success('伏笔已回收，线头收好了')
    await loadForeshadows(selectedWork.value.workId)
  } catch (error) {
    ElMessage.error(error?.message || '标记失败')
  }
}

// ── 章节资料同步：保存 → AI 建议 → 人工确认 → 写回 ──
const analyzedContextHashes = reactive(
  parseContextSyncHashes(localStorage.getItem(CONTEXT_SYNC_HASH_STORAGE_KEY))
)
const contextSyncLoading = ref(false)
const contextSyncApplying = ref(false)
const contextSyncError = ref('')
const contextSyncDialog = reactive({
  open: false,
  workId: null,
  chapterId: null,
  chapterTitle: '',
  contentHash: '',
  contentFingerprint: '',
  suggestions: []
})
const contextSyncStatusText = computed(() => {
  if (contextSyncLoading.value) return 'AI 正在整理资料…'
  if (contextSyncDialog.suggestions.length) {
    return contextSyncDialog.suggestions.length + ' 条资料建议'
  }
  if (contextSyncError.value) return '资料同步失败'
  return '资料同步'
})
let contextAnalysisTimer = null

function persistContextSyncHashes() {
  try {
    const entries = Object.entries(analyzedContextHashes).slice(-500)
    localStorage.setItem(CONTEXT_SYNC_HASH_STORAGE_KEY, JSON.stringify(Object.fromEntries(entries)))
  } catch (error) {
    console.warn('小说资料同步去重记录保存失败', error)
  }
}

function currentContextSnapshot() {
  if (selectedWork.value?.workType !== 'novel' || !currentChapter.value || !manuscript.value.trim()) {
    return null
  }
  return {
    workId: selectedWork.value.workId,
    chapterId: currentChapter.value.chapterId,
    chapterTitle: currentChapter.value.chapterTitle || chapterLabel(currentChapter.value),
    content: manuscript.value
  }
}

function scheduleContextAnalysis(snapshot = currentContextSnapshot()) {
  clearTimeout(contextAnalysisTimer)
  if (!snapshot) return
  contextAnalysisTimer = setTimeout(() => {
    runContextAnalysis({ snapshot, quiet: true })
  }, 8000)
}

async function runContextAnalysis({ force = false, quiet = false, snapshot = null } = {}) {
  const target = snapshot || currentContextSnapshot()
  if (!target || contextSyncLoading.value) return
  const syncKey = buildContextSyncKey({
    userId: userStore.id,
    workId: target.workId,
    chapterId: target.chapterId
  })
  const browserHash = fingerprintNovelContent(target.content)
  if (!force && syncKey && analyzedContextHashes[syncKey] === browserHash) return

  contextSyncLoading.value = true
  contextSyncError.value = ''
  try {
    const response = await analyzeNovelContext(target.workId, target.chapterId)
    const data = response?.data || response
    if (!data?.contentHash || !Array.isArray(data?.changes)) {
      throw new Error('AI 返回了无效的资料建议')
    }
    if (syncKey) {
      analyzedContextHashes[syncKey] = browserHash
      persistContextSyncHashes()
    }

    const stillCurrent = selectedWork.value?.workId === target.workId
      && currentChapter.value?.chapterId === target.chapterId
      && manuscript.value === target.content
    if (!stillCurrent) return

    const suggestions = normalizeContextChanges(data.changes)
    if (!suggestions.length) {
      contextSyncDialog.suggestions = []
      if (!quiet) ElMessage.info('本章没有发现需要写入设定集或伏笔的新变化')
      return
    }
    Object.assign(contextSyncDialog, {
      open: true,
      workId: target.workId,
      chapterId: target.chapterId,
      chapterTitle: target.chapterTitle,
      contentHash: data.contentHash,
      contentFingerprint: browserHash,
      suggestions
    })
  } catch (error) {
    contextSyncError.value = error?.message || 'AI 资料同步分析失败'
    if (!quiet) ElMessage.error(contextSyncError.value)
    else console.warn('章节已保存，但 AI 资料同步分析失败', error)
  } finally {
    contextSyncLoading.value = false
    const latest = currentContextSnapshot()
    if (latest && (latest.workId !== target.workId
      || latest.chapterId !== target.chapterId
      || latest.content !== target.content)) {
      scheduleContextAnalysis(latest)
    }
  }
}

async function handleContextSyncClick() {
  const current = currentContextSnapshot()
  if (!current) return
  const pendingMatches = contextSyncDialog.suggestions.length
    && contextSyncDialog.workId === current.workId
    && contextSyncDialog.chapterId === current.chapterId
    && contextSyncDialog.contentFingerprint === fingerprintNovelContent(current.content)
    && manuscript.value === lastSavedContent.value
  if (pendingMatches) {
    contextSyncDialog.open = true
    return
  }
  if (saveState.value === 'saving') {
    ElMessage.info('正文正在保存，请稍后再整理资料')
    return
  }
  if (manuscript.value !== lastSavedContent.value || saveState.value !== 'saved') {
    const saved = await saveCurrent({ scheduleAnalysis: false })
    if (!saved) return
  }
  await runContextAnalysis({ force: true, quiet: false })
}

async function handleApplyContextChanges(selectedSuggestions) {
  if (!selectedSuggestions.length || !contextSyncDialog.workId) return
  contextSyncApplying.value = true
  try {
    const response = await applyNovelContextChanges(contextSyncDialog.workId, {
      chapterId: contextSyncDialog.chapterId,
      contentHash: contextSyncDialog.contentHash,
      changes: toContextApplyChanges(selectedSuggestions)
    })
    const affected = response?.data?.affected ?? selectedSuggestions.length
    if (selectedWork.value?.workId === contextSyncDialog.workId) {
      await Promise.all([
        loadSettings(contextSyncDialog.workId),
        loadForeshadows(contextSyncDialog.workId)
      ])
    }
    contextSyncDialog.open = false
    contextSyncDialog.suggestions = []
    contextSyncDialog.contentHash = ''
    contextSyncDialog.contentFingerprint = ''
    ElMessage.success('已确认并同步 ' + affected + ' 条设定/伏笔变化')
  } catch (error) {
    ElMessage.error(error?.message || '资料同步失败，请重新分析后重试')
  } finally {
    contextSyncApplying.value = false
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
  clearTimeout(contextAnalysisTimer)
  saveTimer = setTimeout(saveCurrent, 1500)
}

async function saveCurrent({ scheduleAnalysis = true } = {}) {
  if (!selectedWork.value || saveState.value === 'saving') return false
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
    lastSavedContent.value = manuscript.value
    syncSelectedWorkWordCount()
    saveState.value = 'saved'
    if (scheduleAnalysis && work.workType === 'novel' && currentChapter.value) {
      scheduleContextAnalysis(currentContextSnapshot())
    }
    return true
  } catch {
    saveState.value = 'dirty'
    ElMessage.warning('自动保存失败：后端接口未就绪')
    return false
  }
}

watch(manuscript, value => {
  if (selectedWork.value) {
    syncSelectedWorkWordCount()
    if (value === lastSavedContent.value) {
      saveState.value = 'saved'
      return
    }
    if (contextSyncDialog.suggestions.length) {
      contextSyncDialog.open = false
      contextSyncDialog.suggestions = []
      contextSyncDialog.contentHash = ''
      contextSyncDialog.contentFingerprint = ''
    }
    markDirty()
  }
})

// ── 导出 ────────────────────────────────────────────
async function handleExport() {
  const work = selectedWork.value
  if (!work) return
  if (saveState.value === 'dirty') {
    await saveCurrent()
    if (saveState.value !== 'saved') {
      ElMessage.warning('自动保存失败，暂无法导出最新内容，请稍后重试')
      return
    }
  }
  exportNovelWorkText(work.workId, `${work.workName}.txt`)
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
  async (workId, previousWorkId) => {
    if (workId !== previousWorkId) {
      clearTimeout(contextAnalysisTimer)
      contextSyncDialog.open = false
      contextSyncDialog.suggestions = []
      contextSyncDialog.contentHash = ''
      contextSyncDialog.contentFingerprint = ''
      contextSyncError.value = ''
    }
    drawerTab.value = 'chapters'
    if (selectedWork.value?.workType === 'novel') {
      await loadSettings(selectedWork.value.workId)
      await loadForeshadows(selectedWork.value.workId)
    }
    await nextTick()
    if (workspaceRef.value) {
      layoutObserver?.disconnect()
      layoutObserver?.observe(layoutBodyRef.value)
      layoutObserver?.observe(workspaceRef.value)
    }
    applyFittedLayout()
  }
)

onBeforeUnmount(() => {
  clearTimeout(saveTimer)
  clearTimeout(contextAnalysisTimer)
  finishPanelResize()
  layoutObserver?.disconnect()
})
</script>

<style scoped>
.nk-chat-col {
  width: var(--nk-chat-width, 520px);
  flex: 0 0 auto;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.nk-paper-col {
  flex: 1 1 auto;
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

.nk-foreshadow-row {
  display: flex;
  gap: 14px;

  .el-form-item {
    flex: 1;
    min-width: 0;
  }
}

.nk-foreshadow-col {
  width: 100%;
}
</style>
