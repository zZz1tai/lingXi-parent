<template>
  <div class="studio-page">
    <header class="studio-header">
      <div>
        <p class="eyebrow">AI VIDEO STUDIO</p>
        <h1>小说视频工作台</h1>
        <p class="subtitle">从章节、人物和场景资产开始组织你的自动化生产流程。</p>
      </div>
      <el-button type="primary" size="large" :icon="Plus" @click="handleAddProject">
        新建项目
      </el-button>
    </header>

    <section class="toolbar">
      <el-input
        v-model="queryParams.projectName"
        clearable
        placeholder="搜索项目名称"
        :prefix-icon="Search"
        @keyup.enter="handleQuery"
      />
      <el-select v-model="queryParams.status" clearable placeholder="全部状态" @change="handleQuery">
        <el-option label="草稿" value="DRAFT" />
        <el-option label="制作中" value="ACTIVE" />
        <el-option label="已暂停" value="PAUSED" />
        <el-option label="已归档" value="ARCHIVED" />
      </el-select>
      <el-button :icon="Refresh" circle @click="resetQuery" />
    </section>

    <main v-loading="loading" class="project-grid">
      <article v-for="project in projectList" :key="project.projectId" class="project-card">
        <div class="card-topline">
          <span class="ratio">{{ project.defaultAspectRatio || '16:9' }}</span>
          <el-tag :type="statusTagType(project.status)" effect="dark" round>{{ statusLabel(project.status) }}</el-tag>
        </div>
        <div class="poster">
          <span>{{ project.projectName.slice(0, 1) }}</span>
          <div class="poster-glow" />
        </div>
        <h2>{{ project.projectName }}</h2>
        <p class="style-line">{{ project.visualStyle || '尚未设定视觉风格' }}</p>
        <div class="project-meta">
          <span>{{ project.adaptationMode === 'ADAPTIVE' ? '改编模式' : '忠实模式' }}</span>
          <span>{{ project.defaultLanguage || 'zh-CN' }}</span>
          <span>{{ parseTime(project.updateTime, '{y}-{m}-{d}') }}</span>
        </div>
        <div class="card-actions">
          <el-button type="primary" plain @click="openChapterDrawer(project)">章节工作台</el-button>
          <el-dropdown @command="command => handleProjectCommand(command, project)">
            <el-button text :icon="MoreFilled" aria-label="项目更多操作" />
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="edit">编辑项目</el-dropdown-item>
                <el-dropdown-item command="delete" divided>删除项目</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </article>

      <button v-if="!loading" class="create-card" @click="handleAddProject">
        <el-icon><Plus /></el-icon>
        <strong>创建新的影视项目</strong>
        <span>导入小说章节，建立角色和场景资产</span>
      </button>
    </main>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getProjectList"
    />

    <el-dialog v-model="projectDialog.open" :title="projectDialog.title" width="560px" append-to-body>
      <el-form ref="projectFormRef" :model="projectForm" :rules="projectRules" label-position="top">
        <el-form-item label="项目名称" prop="projectName">
          <el-input v-model="projectForm.projectName" maxlength="128" show-word-limit placeholder="例如：雨夜病历" />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="改编方式" prop="adaptationMode">
            <el-select v-model="projectForm.adaptationMode">
              <el-option label="忠实还原" value="FAITHFUL" />
              <el-option label="保守改编" value="ADAPTIVE" />
            </el-select>
          </el-form-item>
          <el-form-item label="默认画幅" prop="defaultAspectRatio">
            <el-select v-model="projectForm.defaultAspectRatio">
              <el-option label="横版 16:9" value="16:9" />
              <el-option label="竖版 9:16" value="9:16" />
              <el-option label="方形 1:1" value="1:1" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="视觉风格">
          <el-input v-model="projectForm.visualStyle" placeholder="例如：电影写实、冷蓝色雨夜光影" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="projectDialog.open = false">取消</el-button>
        <el-button type="primary" @click="submitProject">保存项目</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="chapterDrawer.open" size="min(820px, 96vw)" :with-header="false" append-to-body @closed="stopChapterPolling">
      <div class="chapter-workspace">
        <div class="drawer-header">
          <div>
            <p class="eyebrow">CHAPTERS</p>
            <h2>{{ chapterDrawer.project?.projectName }}</h2>
            <p>原文会在下一步自动解析为故事圣经、场景和分镜。</p>
          </div>
          <div class="drawer-actions">
            <el-button @click="openAssetDrawer()">资产库</el-button>
            <el-button type="primary" :icon="Plus" @click="openChapterDialog">导入章节</el-button>
          </div>
        </div>
        <el-empty v-if="!chapterDrawer.loading && !chapterDrawer.chapters.length" description="还没有导入章节" />
        <div v-loading="chapterDrawer.loading" class="chapter-list">
          <article v-for="chapter in chapterDrawer.chapters" :key="chapter.chapterId" class="chapter-item">
            <div class="chapter-number">{{ String(chapter.chapterNo).padStart(2, '0') }}</div>
            <div class="chapter-content">
              <button type="button" class="chapter-title-button" @click="openChapterVideoWorkspace(chapter)">
                {{ chapter.chapterTitle || `第 ${chapter.chapterNo} 章` }}
              </button>
              <p>{{ chapter.wordCount || 0 }} 字 · {{ pipelineLabel(chapter.pipelineStatus) }}</p>
            </div>
            <el-tag effect="plain" :type="chapter.parseStatus === 'FAILED' ? 'danger' : 'info'">{{ chapter.parseStatus }}</el-tag>
            <div class="chapter-item-actions">
              <el-button v-if="chapter.parseStatus !== 'RUNNING'" text type="primary" @click="analyzeChapter(chapter)">解析</el-button>
              <el-button v-if="chapter.pipelineStatus === 'SCRIPT_READY'" text type="success" @click="openStoryBible(chapter)">查看结果</el-button>
              <el-button
                type="primary"
                plain
                :icon="VideoPlay"
                :loading="preparingChapterId === chapter.chapterId"
                @click="prepareChapterVideoWorkspace(chapter)"
              >生成视频</el-button>
              <el-button text type="danger" :icon="Delete" aria-label="删除章节" @click="removeChapter(chapter)" />
            </div>
          </article>
        </div>
      </div>
    </el-drawer>

    <el-drawer
      v-model="chapterVideoDrawer.open"
      size="min(1180px, 98vw)"
      append-to-body
      @closed="handleChapterVideoDrawerClosed"
    >
      <template #header>
        <div>
          <p class="eyebrow">CHAPTER MATERIALS</p>
          <h2>{{ chapterVideoTitle }} · 章节素材工作台</h2>
        </div>
      </template>

      <div class="chapter-video-toolbar">
        <el-alert
          title="这里汇总本章的场景图、分镜关键帧和全部视频版本"
          description="打开工作台不会调用模型。图片需先查看提示词并手动生成；关键帧同意后才可准备视频提示词，最终仍需二次确认才会调用 Wanx。"
          type="info"
          :closable="false"
          show-icon
        />
        <div class="chapter-material-stats" aria-label="章节素材统计">
          <button type="button" :class="{ active: chapterVideoDrawer.activeTab === 'scenes' }" @click="chapterVideoDrawer.activeTab = 'scenes'">
            <strong>{{ chapterSceneGroups.length }}</strong>
            <span>场景</span>
            <small>{{ chapterVideoDrawer.sceneAssets.length }} 个版本</small>
          </button>
          <button type="button" :class="{ active: chapterVideoDrawer.activeTab === 'shots' }" @click="chapterVideoDrawer.activeTab = 'shots'">
            <strong>{{ chapterShotCount }}</strong>
            <span>分镜</span>
            <small>{{ chapterVideoDrawer.keyframeAssets.length }} 张关键帧</small>
          </button>
          <button type="button" :class="{ active: chapterVideoDrawer.activeTab === 'videos' }" @click="chapterVideoDrawer.activeTab = 'videos'">
            <strong>{{ chapterVideoShotCount }}</strong>
            <span>视频镜头</span>
            <small>{{ chapterVideoDrawer.assets.length }} 个版本</small>
          </button>
        </div>
        <el-alert
          v-if="chapterVideoDrawer.preparing"
          :title="chapterVideoDrawer.prepareTotal ? `正在准备视频提示词草稿（${chapterVideoDrawer.preparedCount}/${chapterVideoDrawer.prepareTotal}）` : '正在检查本章已同意的关键帧'"
          description="这里只会创建或复用草稿；已有视频版本的镜头会跳过，不会提交 Wanx。"
          type="warning"
          :closable="false"
          show-icon
        />
        <div class="chapter-video-toolbar-actions">
          <el-button @click="openChapterKeyframeAssets">打开筛选资产库</el-button>
          <el-button
            type="primary"
            plain
            :icon="VideoPlay"
            :loading="chapterVideoDrawer.preparing"
            @click="prepareChapterVideoWorkspace(chapterVideoDrawer.chapter)"
          >准备视频提示词</el-button>
          <el-button :icon="Refresh" :loading="chapterVideoDrawer.loading" :disabled="chapterVideoDrawer.preparing" @click="loadChapterVideoAssets">刷新全部素材</el-button>
        </div>
      </div>

      <el-alert v-if="chapterVideoDrawer.loadError" :title="chapterVideoDrawer.loadError" type="error" :closable="false" show-icon />
      <el-alert v-else-if="chapterVideoDrawer.referenceLoadError" :title="chapterVideoDrawer.referenceLoadError" type="warning" :closable="false" show-icon />

      <el-tabs v-model="chapterVideoDrawer.activeTab" class="chapter-material-tabs">
        <el-tab-pane name="scenes">
          <template #label>场景参考图（{{ chapterSceneGroups.length }}）</template>
          <el-empty
            v-if="!chapterVideoDrawer.loading && !chapterVideoDrawer.loadError && !chapterVideoDrawer.sceneAssets.length"
            description="本章还没有场景参考图提示词"
          />
          <div v-loading="chapterVideoDrawer.loading" class="chapter-reference-scenes">
            <section v-for="scene in chapterSceneGroups" :key="scene.key" class="chapter-video-scene">
              <div class="chapter-video-scene-heading">
                <div>
                  <span>{{ scene.sceneNo ? `场景 ${scene.sceneNo}` : '场景参考' }}</span>
                  <h3>{{ scene.label }}</h3>
                </div>
                <small>{{ scene.versions.length }} 个版本 · 新版本优先</small>
              </div>
              <div class="chapter-material-card-grid">
                <article v-for="asset in scene.versions" :key="asset.assetId" class="chapter-material-card">
                  <div class="chapter-material-preview">
                    <el-image
                      v-if="asset.previewObjectKey || asset.objectKey"
                      :src="asset.previewObjectKey || asset.objectKey"
                      fit="cover"
                      :preview-src-list="[asset.objectKey || asset.previewObjectKey]"
                    />
                    <span v-else>{{ assetPreviewPlaceholder(asset) }}</span>
                  </div>
                  <div class="chapter-material-card-body">
                    <div class="chapter-video-version-tags">
                      <el-tag size="small" effect="plain">v{{ asset.versionNo || 1 }}</el-tag>
                      <el-tag size="small" :type="assetStatusTagType(asset.status)" effect="light">{{ assetStatusLabel(asset) }}</el-tag>
                      <el-tag v-if="isLatestAssetVersion(asset, scene.versions)" size="small" type="primary" effect="plain">最新版本</el-tag>
                      <el-tag v-if="assetAnalysisVersion(asset)" size="small" type="info" effect="plain">
                        分析 v{{ assetAnalysisVersion(asset) }}{{ isCurrentAnalysisAsset(asset, chapterVideoDrawer.chapter) ? ' · 当前' : ' · 历史' }}
                      </el-tag>
                    </div>
                    <h4>{{ asset.assetName }}</h4>
                    <p>场景参考图 · {{ assetDimensionLabel(asset) }}</p>
                    <div class="asset-prompt-preview chapter-material-prompt">
                      <strong>图片提示词</strong>
                      <p>{{ asset.promptText || '尚未填写提示词' }}</p>
                      <strong v-if="asset.negativePromptText">负向提示词</strong>
                      <p v-if="asset.negativePromptText">{{ asset.negativePromptText }}</p>
                    </div>
                    <el-button class="video-action" size="small" type="primary" plain @click="openPromptDialog(asset)">
                      {{ asset.status === 'DRAFT' || asset.status === 'REJECTED' ? '查看 / 修改提示词并生成' : '查看生成提示词' }}
                    </el-button>
                    <div class="asset-version-actions">
                      <el-button
                        size="small"
                        :loading="regeneratingAssetId === asset.assetId"
                        :disabled="isAssetBusy(asset, chapterVideoDrawer.taskByAssetId[asset.assetId])"
                        @click="createRegenerationDraft(asset)"
                      >重新生成</el-button>
                      <el-button
                        v-if="!isAssetBusy(asset, chapterVideoDrawer.taskByAssetId[asset.assetId])"
                        size="small"
                        type="danger"
                        plain
                        :loading="deletingAssetId === asset.assetId"
                        @click="deleteAssetVersion(asset)"
                      >删除此版本</el-button>
                    </div>
                  </div>
                </article>
              </div>
            </section>
          </div>
        </el-tab-pane>

        <el-tab-pane name="shots">
          <template #label>分镜关键帧（{{ chapterShotCount }}）</template>
          <el-empty
            v-if="!chapterVideoDrawer.loading && !chapterVideoDrawer.loadError && !chapterVideoDrawer.keyframeAssets.length"
            description="本章还没有分镜关键帧提示词；请先完成章节解析"
          />
          <div v-loading="chapterVideoDrawer.loading" class="chapter-video-scenes">
            <section v-for="scene in chapterShotGroups" :key="scene.key" class="chapter-video-scene">
              <div class="chapter-video-scene-heading">
                <div>
                  <span>{{ scene.orderLabel }}</span>
                  <h3>{{ scene.label }}</h3>
                </div>
                <small>{{ scene.shots.length }} 个分镜</small>
              </div>

              <article v-for="shot in scene.shots" :key="shot.key" class="chapter-video-shot chapter-keyframe-shot">
                <div class="chapter-video-shot-heading">
                  <strong>{{ shot.label }}</strong>
                  <span>{{ shot.versions.length }} 个关键帧版本 · 新版本优先</span>
                </div>

                <div class="shot-reference-heading">
                  <div>
                    <strong>本分镜引用资产</strong>
                    <span>场景参考图与人物三视图会作为 Qwen 生成关键帧时的实际参考图输入</span>
                  </div>
                  <div class="shot-reference-tags">
                    <el-tag size="small" :type="shot.referencesApproved ? 'success' : 'warning'" effect="light">
                      {{ shot.referencesApproved ? '引用图已全部 APPROVED' : '引用图未完成' }}
                    </el-tag>
                    <el-tag size="small" :type="shot.hasExplicitReferenceIds ? 'primary' : 'danger'" effect="plain">
                      {{ shot.hasExplicitReferenceIds ? '精确资产引用' : '精确引用 metadata 不完整' }}
                    </el-tag>
                  </div>
                </div>
                <p v-if="!shot.referencesApproved" class="shot-reference-warning">{{ shot.referenceStatusMessage }}</p>
                <div class="shot-reference-strip">
                  <button
                    type="button"
                    class="shot-reference-card"
                    :class="{ missing: !shot.sceneReference }"
                    :disabled="!shot.sceneReference"
                    @click="shot.sceneReference && openPromptDialog(shot.sceneReference)"
                  >
                    <span class="shot-reference-kind">场景参考</span>
                    <div class="shot-reference-preview">
                      <el-image
                        v-if="shot.sceneReference && (shot.sceneReference.previewObjectKey || shot.sceneReference.objectKey)"
                        :src="shot.sceneReference.previewObjectKey || shot.sceneReference.objectKey"
                        fit="cover"
                      />
                      <span v-else>{{ shot.sceneReference ? assetPreviewPlaceholder(shot.sceneReference) : '场景参考图未创建' }}</span>
                    </div>
                    <strong>{{ shot.sceneReference?.assetName || '缺少场景参考图' }}</strong>
                    <el-tag v-if="shot.sceneReference" size="small" :type="assetStatusTagType(shot.sceneReference.status)" effect="light">
                      {{ assetStatusLabel(shot.sceneReference) }}
                    </el-tag>
                    <el-tag v-else size="small" type="danger" effect="plain">未创建</el-tag>
                  </button>

                  <button
                    v-for="reference in shot.characterReferences"
                    :key="reference.key"
                    type="button"
                    class="shot-reference-card"
                    :class="{ missing: !reference.asset }"
                    :disabled="!reference.asset"
                    @click="reference.asset && openPromptDialog(reference.asset)"
                  >
                    <span class="shot-reference-kind">人物三视图</span>
                    <div class="shot-reference-preview character">
                      <el-image
                        v-if="reference.asset && (reference.asset.previewObjectKey || reference.asset.objectKey)"
                        :src="reference.asset.previewObjectKey || reference.asset.objectKey"
                        fit="contain"
                      />
                      <span v-else>{{ reference.asset ? assetPreviewPlaceholder(reference.asset) : '人物参考图未创建' }}</span>
                    </div>
                    <strong>{{ reference.name }}</strong>
                    <el-tag v-if="reference.asset" size="small" :type="assetStatusTagType(reference.asset.status)" effect="light">
                      {{ assetStatusLabel(reference.asset) }}
                    </el-tag>
                    <el-tag v-else size="small" type="danger" effect="plain">缺少资产</el-tag>
                  </button>

                  <div v-if="!shot.characterReferences.length" class="shot-reference-card empty-reference">
                    <span class="shot-reference-kind">人物参考</span>
                    <strong>{{ shot.characterReferenceNote }}</strong>
                    <small>不影响查看关键帧提示词</small>
                  </div>
                </div>

                <div class="chapter-material-card-grid keyframe-version-grid">
                  <article v-for="asset in shot.versions" :key="asset.assetId" class="chapter-material-card">
                    <div class="chapter-material-preview keyframe-preview">
                      <el-image
                        v-if="asset.previewObjectKey || asset.objectKey"
                        :src="asset.previewObjectKey || asset.objectKey"
                        fit="cover"
                        :preview-src-list="[asset.objectKey || asset.previewObjectKey]"
                      />
                      <span v-else>{{ assetPreviewPlaceholder(asset) }}</span>
                    </div>
                    <div class="chapter-material-card-body">
                      <div class="chapter-video-version-tags">
                        <el-tag size="small" effect="plain">v{{ asset.versionNo || 1 }}</el-tag>
                        <el-tag size="small" :type="assetStatusTagType(asset.status)" effect="light">{{ assetStatusLabel(asset) }}</el-tag>
                        <el-tag v-if="isLatestAssetVersion(asset, shot.versions)" size="small" type="primary" effect="plain">最新版本</el-tag>
                        <el-tag v-if="assetAnalysisVersion(asset)" size="small" type="info" effect="plain">
                          分析 v{{ assetAnalysisVersion(asset) }}{{ isCurrentAnalysisAsset(asset, chapterVideoDrawer.chapter) ? ' · 当前' : ' · 历史' }}
                        </el-tag>
                      </div>
                      <h4>{{ asset.assetName }}</h4>
                      <p>{{ assetDimensionLabel(asset) }}</p>
                      <div class="asset-prompt-preview chapter-material-prompt">
                        <strong>关键帧提示词</strong>
                        <p>{{ asset.promptText || '尚未填写提示词' }}</p>
                        <strong v-if="asset.negativePromptText">负向提示词</strong>
                        <p v-if="asset.negativePromptText">{{ asset.negativePromptText }}</p>
                      </div>
                      <p v-if="asset.status !== 'APPROVED'" class="asset-task-status">{{ assetTaskMessage(asset, chapterVideoDrawer) }}</p>
                      <div
                        v-if="asset.status === 'DRAFT' || asset.status === 'REJECTED'"
                        class="keyframe-action-row"
                      >
                        <el-button size="small" plain @click="openPromptDialog(asset, shot)">查看 / 修改关键帧提示词</el-button>
                        <el-tooltip
                          :disabled="shot.referencesApproved"
                          :content="shot.referenceStatusMessage"
                          placement="top"
                        >
                          <span class="keyframe-generate-button-wrap">
                            <el-button
                              size="small"
                              :type="asset.status === 'REJECTED' ? 'danger' : 'primary'"
                              :plain="asset.status === 'REJECTED'"
                              :disabled="!shot.referencesApproved"
                              @click="openPromptDialog(asset, shot)"
                            >{{ asset.status === 'REJECTED' ? '重新生成关键帧图片' : '生成关键帧图片' }}</el-button>
                          </span>
                        </el-tooltip>
                      </div>
                      <el-button
                        v-if="asset.status !== 'DRAFT' && asset.status !== 'REJECTED'"
                        class="video-action"
                        size="small"
                        plain
                        @click="openPromptDialog(asset, shot)"
                      >查看当时关键帧提示词</el-button>
                      <el-button
                        v-if="asset.status === 'GENERATED'"
                        class="video-action"
                        size="small"
                        type="primary"
                        :loading="preparingVideoDraftId === asset.assetId"
                        @click="approveAndPrepareVideoPrompt(asset)"
                      >同意画面并提炼视频提示词</el-button>
                      <el-button
                        v-if="asset.status === 'APPROVED'"
                        class="video-action"
                        size="small"
                        type="primary"
                        plain
                        :loading="preparingVideoDraftId === asset.assetId"
                        @click="prepareVideoPromptDraft(asset)"
                      >提炼 / 查看视频提示词</el-button>
                      <div class="asset-version-actions">
                        <el-button
                          size="small"
                          :loading="regeneratingAssetId === asset.assetId"
                          :disabled="isAssetBusy(asset, chapterVideoDrawer.taskByAssetId[asset.assetId])"
                          @click="createRegenerationDraft(asset)"
                        >重新生成</el-button>
                        <el-button
                          v-if="!isAssetBusy(asset, chapterVideoDrawer.taskByAssetId[asset.assetId])"
                          size="small"
                          type="danger"
                          plain
                          :loading="deletingAssetId === asset.assetId"
                          @click="deleteAssetVersion(asset)"
                        >删除此版本</el-button>
                      </div>
                    </div>
                  </article>
                </div>
              </article>
            </section>
          </div>
        </el-tab-pane>

        <el-tab-pane name="videos">
          <template #label>视频片段（{{ chapterVideoDrawer.assets.length }}）</template>
      <el-empty
        v-if="!chapterVideoDrawer.loading && !chapterVideoDrawer.preparing && !chapterVideoDrawer.loadError && !chapterVideoDrawer.assets.length"
        description="本章还没有视频草稿。请先检查并同意关键帧，再提炼视频提示词。"
      >
        <el-button type="primary" plain @click="openChapterKeyframeAssets">去审批关键帧</el-button>
      </el-empty>

      <div v-loading="chapterVideoDrawer.loading" class="chapter-video-scenes">
        <section v-for="scene in chapterVideoGroups" :key="scene.key" class="chapter-video-scene">
          <div class="chapter-video-scene-heading">
            <div>
              <span>{{ scene.orderLabel }}</span>
              <h3>{{ scene.label }}</h3>
            </div>
            <small>{{ scene.shots.length }} 个镜头</small>
          </div>

          <article v-for="shot in scene.shots" :key="shot.key" class="chapter-video-shot">
            <div class="chapter-video-shot-heading">
              <strong>{{ shot.label }}</strong>
              <span>{{ shot.versions.length }} 个版本 · 新版本优先</span>
            </div>

            <div class="chapter-video-version-grid">
              <div v-for="asset in shot.versions" :key="asset.assetId" class="chapter-video-version-card">
                <div class="chapter-video-preview">
                  <video v-if="asset.objectKey || asset.previewObjectKey" :src="asset.objectKey || asset.previewObjectKey" controls preload="metadata" />
                  <span v-else>{{ assetPreviewPlaceholder(asset) }}</span>
                </div>
                <div class="chapter-video-version-body">
                  <div class="chapter-video-version-tags">
                    <el-tag size="small" effect="plain">v{{ asset.versionNo || 1 }}</el-tag>
                    <el-tag size="small" :type="assetStatusTagType(asset.status)" effect="light">{{ assetStatusLabel(asset) }}</el-tag>
                    <el-tag v-if="isLatestAssetVersion(asset, shot.versions)" size="small" type="primary" effect="plain">最新版本</el-tag>
                    <el-tag v-if="assetAnalysisVersion(asset)" size="small" type="info" effect="plain">
                      分析 v{{ assetAnalysisVersion(asset) }}{{ isCurrentAnalysisAsset(asset, chapterVideoDrawer.chapter) ? ' · 当前' : ' · 历史' }}
                    </el-tag>
                    <el-tag v-if="asset.canonicalFlag === 1" size="small" type="success" effect="plain">规范资产</el-tag>
                  </div>
                  <h4>{{ asset.assetName }}</h4>
                  <p>{{ assetDimensionLabel(asset) }}</p>
                  <div class="asset-prompt-preview chapter-video-prompt-preview">
                    <strong>视频提示词</strong>
                    <p>{{ asset.promptText || '尚未填写提示词' }}</p>
                  </div>
                  <p v-if="asset.status !== 'APPROVED'" class="asset-task-status">{{ assetTaskMessage(asset, chapterVideoDrawer) }}</p>

                  <el-button
                    v-if="asset.status === 'DRAFT' || asset.status === 'REJECTED'"
                    class="video-action"
                    size="small"
                    type="primary"
                    @click="openVideoPromptDialog(asset)"
                  >查看 / 修改视频提示词</el-button>
                  <el-button
                    v-else
                    class="video-action"
                    size="small"
                    plain
                    @click="openVideoPromptDialog(asset)"
                  >查看视频提示词</el-button>
                  <el-button
                    v-if="chapterVideoDrawer.taskByAssetId[asset.assetId]?.status === 'NEEDS_REVIEW'"
                    class="video-action"
                    size="small"
                    type="warning"
                    @click="resumeWanxSubmission(asset, chapterVideoDrawer)"
                  >填写任务ID并恢复轮询</el-button>
                  <el-button
                    v-if="chapterVideoDrawer.taskByAssetId[asset.assetId]?.status === 'NEEDS_REVIEW' && !chapterVideoDrawer.taskByAssetId[asset.assetId]?.providerTaskId"
                    class="video-action"
                    size="small"
                    type="danger"
                    plain
                    @click="confirmWanxNotSubmitted(asset)"
                  >确认未提交并解锁</el-button>

                  <div class="asset-version-actions">
                    <el-button
                      size="small"
                      :loading="regeneratingAssetId === asset.assetId"
                      :disabled="isAssetBusy(asset, chapterVideoDrawer.taskByAssetId[asset.assetId])"
                      @click="createRegenerationDraft(asset)"
                    >重新生成</el-button>
                    <el-button
                      v-if="!isAssetBusy(asset, chapterVideoDrawer.taskByAssetId[asset.assetId])"
                      size="small"
                      type="danger"
                      plain
                      :loading="deletingAssetId === asset.assetId"
                      @click="deleteAssetVersion(asset)"
                    >删除此版本</el-button>
                  </div>
                </div>
              </div>
            </div>
          </article>
        </section>
      </div>
        </el-tab-pane>
      </el-tabs>
    </el-drawer>

    <el-drawer v-model="storyBibleDrawer.open" size="780px" append-to-body>
      <template #header>
        <div>
          <p class="eyebrow">STORY BIBLE</p>
          <h2>{{ storyBibleDrawer.chapter?.chapterTitle || `第 ${storyBibleDrawer.chapter?.chapterNo || ''} 章` }} · 解析结果</h2>
        </div>
      </template>
      <div v-if="storyBibleDrawer.data" class="story-bible">
        <section class="bible-summary">
          <h3>章节摘要</h3>
          <p>{{ storyBibleDrawer.data.summary || '暂无摘要' }}</p>
        </section>
        <section>
          <div class="section-heading"><h3>人物</h3><span>{{ storyBibleDrawer.data.characters?.length || 0 }} 位</span></div>
          <div class="bible-characters">
            <article v-for="character in storyBibleDrawer.data.characters || []" :key="character.name" class="bible-card">
              <strong>{{ character.name }}</strong>
              <p>{{ character.appearance || '暂无外观描述' }}</p>
              <small>{{ Array.isArray(character.personality) ? character.personality.join(' · ') : character.personality }}</small>
            </article>
          </div>
        </section>
        <section>
          <div class="section-heading"><h3>场景与分镜</h3><span>{{ storyBibleDrawer.data.scenes?.length || 0 }} 场</span></div>
          <article v-for="scene in storyBibleDrawer.data.scenes || []" :key="scene.sceneNo" class="bible-scene">
            <div><span class="scene-no">{{ String(scene.sceneNo).padStart(2, '0') }}</span><strong>{{ scene.title }}</strong></div>
            <p>{{ scene.time }} · {{ scene.location }}</p>
            <p>{{ scene.dramaticGoal }}</p>
            <div class="scene-meta">对白 {{ scene.dialogues?.length || 0 }} 句 · 分镜 {{ scene.shots?.length || 0 }} 个</div>
          </article>
        </section>
      </div>
      <el-empty v-else description="尚未取得有效的解析结果" />
    </el-drawer>

    <el-drawer v-model="assetDrawer.open" size="760px" append-to-body @closed="handleAssetDrawerClosed">
      <template #header>
        <div>
          <p class="eyebrow">ASSET LIBRARY</p>
          <h2>{{ chapterDrawer.project?.projectName }} · 资产库</h2>
        </div>
      </template>
      <div v-if="assetDrawer.chapterId" class="asset-filter-context">
        <el-tag type="warning" effect="plain" closable @close="clearAssetChapterFilter">{{ assetChapterFilterLabel }}</el-tag>
        <span>当前只显示本章资产与历史版本</span>
      </div>
      <div class="asset-toolbar">
        <el-select v-model="assetDrawer.assetType" clearable placeholder="全部资产类型" @change="loadAssets">
          <el-option label="角色三视图" value="CHARACTER_REFERENCE" />
          <el-option label="场景设定" value="SCENE_REFERENCE" />
          <el-option label="镜头关键帧" value="SHOT_KEYFRAME" />
          <el-option label="视频片段" value="VIDEO_CLIP" />
        </el-select>
        <el-select v-model="assetDrawer.status" clearable placeholder="全部状态" @change="loadAssets">
          <el-option label="待确认提示词" value="DRAFT" />
          <el-option label="生成中" value="GENERATING" />
          <el-option label="待我同意" value="GENERATED" />
          <el-option label="已完成 / 已同意" value="APPROVED" />
          <el-option label="生成失败" value="REJECTED" />
        </el-select>
        <el-button :icon="Refresh" circle @click="loadAssets" />
      </div>
      <el-alert v-if="assetDrawer.loadError" :title="assetDrawer.loadError" type="error" :closable="false" show-icon />
      <el-empty v-if="!assetDrawer.loading && !assetDrawer.loadError && !assetDrawer.assets.length" description="暂无符合条件的图片或视频资产" />
      <div v-loading="assetDrawer.loading" class="asset-grid">
        <article v-for="asset in assetDrawer.assets" :key="asset.assetId" class="asset-card">
          <div class="asset-preview">
            <video v-if="asset.assetType === 'VIDEO_CLIP' && (asset.objectKey || asset.previewObjectKey)" :src="asset.objectKey || asset.previewObjectKey" controls preload="metadata" />
            <el-image v-else-if="asset.previewObjectKey || asset.objectKey" :src="asset.previewObjectKey || asset.objectKey" :fit="asset.assetType === 'CHARACTER_REFERENCE' ? 'contain' : 'cover'" :preview-src-list="[asset.objectKey || asset.previewObjectKey]" />
            <span v-else>{{ assetPreviewPlaceholder(asset) }}</span>
          </div>
          <div class="asset-body">
            <el-tag size="small" effect="plain">{{ assetTypeLabel(asset.assetType) }}</el-tag>
            <el-tag size="small" :type="assetStatusTagType(asset.status)" effect="light">{{ assetStatusLabel(asset) }}</el-tag>
            <el-tag v-if="isLatestAssetVersion(asset, assetDrawer.assets)" size="small" type="primary" effect="plain">最新版本</el-tag>
            <el-tag v-if="asset.canonicalFlag === 1" size="small" type="success" effect="plain">规范资产</el-tag>
            <h3>{{ asset.assetName }}</h3>
            <p>v{{ asset.versionNo }} · {{ assetDimensionLabel(asset) }}</p>
            <p v-if="asset.assetType === 'CHARACTER_REFERENCE'" class="character-view-spec">正面 / 侧面 / 背面 · 横向 16:9 · 1280 × 720</p>
            <div class="asset-prompt-preview">
              <strong>正向提示词</strong>
              <p>{{ asset.promptText || '尚未填写提示词' }}</p>
              <strong v-if="asset.negativePromptText">负向提示词</strong>
              <p v-if="asset.negativePromptText">{{ asset.negativePromptText }}</p>
            </div>
            <p v-if="asset.assetType === 'SHOT_KEYFRAME' && asset.status === 'APPROVED' && asset.approvedBy">{{ asset.approvedBy }} 于 {{ asset.approvedTime }} 同意</p>
            <p v-if="asset.status !== 'APPROVED'" class="asset-task-status">{{ assetTaskMessage(asset) }}</p>
            <el-button v-if="asset.assetType !== 'VIDEO_CLIP' && asset.status === 'DRAFT'" class="video-action" size="small" type="primary" @click="openPromptDialog(asset)">{{ asset.assetType === 'CHARACTER_REFERENCE' ? '查看三视图提示词并生成' : '查看提示词并生成' }}</el-button>
            <el-button v-if="asset.assetType !== 'VIDEO_CLIP' && asset.status === 'REJECTED'" class="video-action" size="small" type="danger" plain @click="openPromptDialog(asset)">{{ asset.assetType === 'CHARACTER_REFERENCE' ? '修改三视图提示词并重试' : '修改提示词并重试' }}</el-button>
            <el-button
              v-if="asset.assetType !== 'VIDEO_CLIP' && asset.status !== 'DRAFT' && asset.status !== 'REJECTED'"
              class="video-action"
              size="small"
              plain
              @click="openPromptDialog(asset)"
            >{{ asset.assetType === 'CHARACTER_REFERENCE' ? '查看当时三视图提示词' : '查看当时图片提示词' }}</el-button>
            <el-button
              v-if="asset.assetType === 'SHOT_KEYFRAME' && asset.status === 'GENERATED'"
              class="video-action"
              size="small"
              type="primary"
              :loading="preparingVideoDraftId === asset.assetId"
              @click="approveAndPrepareVideoPrompt(asset)"
            >同意画面并提炼视频提示词</el-button>
            <el-button
              v-if="asset.assetType === 'SHOT_KEYFRAME' && asset.status === 'APPROVED'"
              class="video-action"
              size="small"
              type="primary"
              plain
              :loading="preparingVideoDraftId === asset.assetId"
              @click="prepareVideoPromptDraft(asset)"
            >提炼 / 查看视频提示词</el-button>
            <el-button
              v-if="asset.assetType === 'VIDEO_CLIP' && (asset.status === 'DRAFT' || asset.status === 'REJECTED')"
              class="video-action"
              size="small"
              :type="asset.status === 'REJECTED' ? 'danger' : 'primary'"
              :plain="asset.status === 'REJECTED'"
              @click="openVideoPromptDialog(asset)"
            >{{ asset.status === 'REJECTED' ? '修改视频提示词并重试' : '查看 / 修改视频提示词' }}</el-button>
            <el-button
              v-if="asset.assetType === 'VIDEO_CLIP' && (asset.status === 'GENERATING' || asset.status === 'GENERATED' || asset.status === 'APPROVED')"
              class="video-action"
              size="small"
              plain
              @click="openVideoPromptDialog(asset)"
            >查看已确认视频提示词</el-button>
            <el-button
              v-if="asset.assetType === 'VIDEO_CLIP' && assetDrawer.taskByAssetId[asset.assetId]?.status === 'NEEDS_REVIEW'"
              class="video-action"
              size="small"
              type="warning"
              @click="resumeWanxSubmission(asset)"
            >填写任务ID并恢复轮询</el-button>
            <el-button
              v-if="asset.assetType === 'VIDEO_CLIP' && assetDrawer.taskByAssetId[asset.assetId]?.status === 'NEEDS_REVIEW' && !assetDrawer.taskByAssetId[asset.assetId]?.providerTaskId"
              class="video-action"
              size="small"
              type="danger"
              plain
              @click="confirmWanxNotSubmitted(asset)"
            >确认未提交并解锁</el-button>
            <div class="asset-version-actions">
              <el-button
                size="small"
                :loading="regeneratingAssetId === asset.assetId"
                :disabled="isAssetBusy(asset, assetDrawer.taskByAssetId[asset.assetId])"
                @click="createRegenerationDraft(asset)"
              >重新生成</el-button>
              <el-button
                v-if="!isAssetBusy(asset, assetDrawer.taskByAssetId[asset.assetId])"
                size="small"
                type="danger"
                plain
                :loading="deletingAssetId === asset.assetId"
                @click="deleteAssetVersion(asset)"
              >删除此版本</el-button>
            </div>
          </div>
        </article>
      </div>
    </el-drawer>

    <el-dialog
      v-model="promptDialog.open"
      :title="promptDialog.assetName + (promptDialog.assetType === 'CHARACTER_REFERENCE' ? ' · 三视图提示词' : ' · 图片提示词')"
      width="720px"
      append-to-body
      :close-on-click-modal="!promptDialog.submitting"
      :close-on-press-escape="!promptDialog.submitting"
      :show-close="!promptDialog.submitting"
    >
      <el-alert
        v-if="promptDialog.assetType === 'SHOT_KEYFRAME' && !promptDialog.referenceReady"
        title="关键帧引用图尚未全部完成，当前不能生成"
        :description="promptDialog.referenceMessage"
        type="error"
        :closable="false"
        show-icon
      />
      <el-alert
        v-if="promptDialog.editable && promptDialog.assetType === 'CHARACTER_REFERENCE'"
        title="人物资产固定生成正面、侧面、背面三视图"
        description="系统会强制使用同一人物、完整全身、统一服装与比例，从左到右横向排列，并使用 16:9（1280 × 720）画布。你可以修改人物外观和美术风格。"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-alert v-else-if="promptDialog.editable" title="图片不会自动生成。请确认或修改提示词，再手动点击生成。" type="info" :closable="false" show-icon />
      <el-form label-position="top" class="prompt-form">
        <el-form-item :label="promptDialog.assetType === 'CHARACTER_REFERENCE' ? '三视图完整提示词（必填）' : '正向提示词（必填）'">
          <el-input v-model="promptDialog.promptText" type="textarea" :rows="10" :readonly="!promptDialog.editable || promptDialog.submitting" maxlength="12000" show-word-limit :placeholder="promptDialog.assetType === 'CHARACTER_REFERENCE' ? '描述人物脸型、五官、发型、体型、服装、配色、配饰和美术风格' : '描述人物、场景、构图、光线和画面风格'" />
        </el-form-item>
        <el-form-item label="负向提示词（可选）">
          <el-input v-model="promptDialog.negativePromptText" type="textarea" :rows="4" :readonly="!promptDialog.editable || promptDialog.submitting" maxlength="4000" show-word-limit placeholder="不希望图片中出现的内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="promptDialog.submitting" @click="promptDialog.open = false">{{ promptDialog.editable ? '取消' : '关闭' }}</el-button>
        <el-button v-if="promptDialog.editable" :loading="promptDialog.submitting" @click="saveImagePrompt(false)">仅保存提示词</el-button>
        <el-button
          v-if="promptDialog.editable"
          type="primary"
          :loading="promptDialog.submitting"
          :disabled="promptDialog.assetType === 'SHOT_KEYFRAME' && !promptDialog.referenceReady"
          @click="saveImagePrompt(true)"
        >{{ promptDialog.assetType === 'CHARACTER_REFERENCE' ? (promptDialog.status === 'REJECTED' ? '保存并重新生成三视图' : '保存并生成三视图') : (promptDialog.status === 'REJECTED' ? '保存并重新生成' : '保存并生成图片') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="regenerationDialog.open"
      :title="regenerationDialog.assetName + ' · 选择关键帧参考版本'"
      width="760px"
      append-to-body
      :close-on-click-modal="!regenerationDialog.submitting"
      :close-on-press-escape="!regenerationDialog.submitting"
      :show-close="!regenerationDialog.submitting"
    >
      <el-alert
        title="这里只创建关键帧新版本草稿，不会调用 Qwen Image"
        description="新版本会使用你选择的已批准参考图；原关键帧和旧参考资产会继续保留，可在资产库中手动删除。"
        type="info"
        :closable="false"
        show-icon
      />
      <el-alert
        v-if="regenerationDialog.loadError"
        class="regeneration-reference-alert"
        :title="regenerationDialog.loadError"
        type="error"
        :closable="false"
        show-icon
      />
      <el-alert
        v-else-if="regenerationDialog.selectionMessage"
        class="regeneration-reference-alert"
        :title="regenerationDialog.selectionMessage"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-form v-loading="regenerationDialog.loading" label-position="top" class="prompt-form regeneration-reference-form">
        <el-form-item label="场景参考图（必选 1 张）">
          <el-select
            v-model="regenerationDialog.sceneReferenceAssetId"
            filterable
            placeholder="请选择同项目已 APPROVED 的场景参考版本"
            :disabled="regenerationDialog.loading || regenerationDialog.submitting"
          >
            <el-option
              v-for="asset in regenerationDialog.sceneAssets"
              :key="asset.assetId"
              :label="referenceOptionLabel(asset)"
              :value="asset.assetId"
            >
              <div class="reference-option-row">
                <span>{{ asset.assetName }}</span>
                <small>v{{ asset.versionNo || 1 }} · {{ assetDimensionLabel(asset) }}</small>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="人物三视图（可选，最多 2 张）">
          <el-select
            v-model="regenerationDialog.characterReferenceAssetIds"
            multiple
            filterable
            collapse-tags
            :multiple-limit="2"
            placeholder="请选择同项目已 APPROVED 的人物三视图版本"
            :disabled="regenerationDialog.loading || regenerationDialog.submitting"
          >
            <el-option
              v-for="asset in regenerationDialog.characterAssets"
              :key="asset.assetId"
              :label="referenceOptionLabel(asset)"
              :value="asset.assetId"
            >
              <div class="reference-option-row">
                <span>{{ asset.assetName }}</span>
                <small>v{{ asset.versionNo || 1 }} · {{ assetDimensionLabel(asset) }}</small>
              </div>
            </el-option>
          </el-select>
          <small class="regeneration-reference-help">默认预选当前 metadata 中的精确引用；你可以切换到同一人物或场景的新版本。</small>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="regenerationDialog.submitting" @click="regenerationDialog.open = false">取消</el-button>
        <el-button
          type="primary"
          :loading="regenerationDialog.submitting"
          :disabled="regenerationDialog.loading || !!regenerationDialog.loadError || !regenerationDialog.sceneReferenceAssetId"
          @click="submitKeyframeRegenerationDraft"
        >创建新版本草稿</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="videoPromptDialog.open"
      :title="videoPromptDialog.assetName + ' · 视频提示词'"
      width="780px"
      append-to-body
      :close-on-click-modal="!videoPromptDialog.submitting"
      :close-on-press-escape="!videoPromptDialog.submitting"
      :show-close="!videoPromptDialog.submitting"
    >
      <el-alert
        v-if="videoPromptDialog.editable"
        title="这一步只准备视频提示词，不会自动调用 Wanx"
        description="提示词由章节分镜、关键帧、人物与场景一致性约束自动提炼。当前 Wanx 2.1 正向词最多 800 字、负向词最多 500 字、时长支持 3/4/5 秒；只有点击“保存并生成视频”且再次确认后，才会提交并产生费用。"
        type="info"
        :closable="false"
        show-icon
      />
      <el-alert
        v-else
        title="这是已确认的视频提示词，只读不可修改"
        :description="videoPromptDialog.status === 'GENERATED' ? '该提示词对应的视频已经生成并保存。' : '该提示词已经进入 Wanx 提交流程；为保证审计一致性，提交后不能修改。'"
        type="warning"
        :closable="false"
        show-icon
      />

      <section class="video-shot-summary">
        <div class="video-summary-heading">
          <strong>镜头执行摘要</strong>
          <span>提交前请核对运镜、动作与节奏</span>
        </div>
        <div class="video-summary-grid">
          <div>
            <small>景别</small>
            <p>{{ videoPromptDialog.shotSize || '未指定' }}</p>
          </div>
          <div>
            <small>运镜</small>
            <p>{{ videoPromptDialog.cameraMovement || '未指定' }}</p>
          </div>
          <div v-if="videoPromptDialog.modelCode">
            <small>视频模型</small>
            <p>{{ videoPromptDialog.modelCode }}</p>
          </div>
          <div v-if="videoPromptDialog.compositionText">
            <small>构图</small>
            <p>{{ videoPromptDialog.compositionText }}</p>
          </div>
          <div v-if="videoPromptDialog.actionText">
            <small>动作与时序</small>
            <p>{{ videoPromptDialog.actionText }}</p>
          </div>
          <div v-if="videoPromptDialog.emotionText">
            <small>情绪</small>
            <p>{{ videoPromptDialog.emotionText }}</p>
          </div>
          <div v-if="videoPromptDialog.dialogueText">
            <small>对白 / 口型</small>
            <p>{{ videoPromptDialog.dialogueText }}</p>
          </div>
        </div>
      </section>

      <el-form label-position="top" class="prompt-form video-prompt-form">
        <el-form-item label="正向视频提示词（必填）">
          <el-input
            v-model="videoPromptDialog.promptText"
            type="textarea"
            :rows="10"
            :readonly="!videoPromptDialog.editable || videoPromptDialog.submitting"
            maxlength="800"
            show-word-limit
            placeholder="描述主体一致性、环境、动作时序、表情、景别、运镜、构图和画面稳定性"
          />
        </el-form-item>
        <el-form-item label="负向视频提示词（可选）">
          <el-input
            v-model="videoPromptDialog.negativePromptText"
            type="textarea"
            :rows="4"
            :readonly="!videoPromptDialog.editable || videoPromptDialog.submitting"
            maxlength="500"
            show-word-limit
            placeholder="例如：人物变脸、肢体畸变、闪烁、画面跳变、镜头抖动、身份漂移"
          />
        </el-form-item>
        <el-form-item label="目标时长">
          <div class="duration-editor">
            <el-input-number
              v-model="videoPromptDialog.durationSeconds"
              :min="3"
              :max="5"
              :step="1"
              :precision="0"
              :disabled="!videoPromptDialog.editable || videoPromptDialog.submitting"
              controls-position="right"
            />
            <span>秒</span>
            <small>将以 {{ Math.round(videoPromptDialog.durationSeconds * 1000) }} ms 保存</small>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button :disabled="videoPromptDialog.submitting" @click="videoPromptDialog.open = false">{{ videoPromptDialog.editable ? '取消' : '关闭' }}</el-button>
        <el-button v-if="videoPromptDialog.editable" :loading="videoPromptDialog.submitting" @click="saveVideoPrompt(false)">仅保存</el-button>
        <el-button v-if="videoPromptDialog.editable" type="primary" :loading="videoPromptDialog.submitting" @click="saveVideoPrompt(true)">保存并生成视频</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="chapterDialog.open" title="导入小说章节" width="700px" append-to-body>
      <el-form ref="chapterFormRef" :model="chapterForm" :rules="chapterRules" label-position="top">
        <div class="form-grid">
          <el-form-item label="章节序号" prop="chapterNo">
            <el-input-number v-model="chapterForm.chapterNo" :min="1" :precision="0" controls-position="right" />
          </el-form-item>
          <el-form-item label="章节标题">
            <el-input v-model="chapterForm.chapterTitle" placeholder="例如：雨夜的病历" />
          </el-form-item>
        </div>
        <el-form-item label="小说原文" prop="sourceText">
          <el-input v-model="chapterForm.sourceText" type="textarea" :rows="12" maxlength="100000" show-word-limit placeholder="粘贴本章节正文；保存后将作为可追溯的生成依据。" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="chapterDialog.open = false">取消</el-button>
        <el-button type="primary" @click="submitChapter">保存章节</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="AiVedioProject">
import { Delete, MoreFilled, Plus, Refresh, Search, VideoPlay } from '@element-plus/icons-vue'
import {
  addAiVideoChapter,
  addAiVideoProject,
  analyzeAiVideoChapter,
  approveAiVideoAsset,
  createAiVideoAssetRegenerationDraft,
  createAiVideoAssetVideoDraft,
  delAiVideoAsset,
  delAiVideoChapter,
  delAiVideoProject,
  getAiVideoProject,
  getAiVideoStoryBible,
  generateAiVideoAssetImage,
  generateAiVideoAssetVideo,
  listAiVideoChapter,
  listAiVideoAsset,
  listAiVideoTask,
  listAiVideoProject,
  retryAiVideoAssetImage,
  resolveAiVideoAssetWanxSubmission,
  updateAiVideoAssetPrompt,
  updateAiVideoAssetVideoPrompt,
  updateAiVideoProject
} from '@/api/aiVedio/project'

const { proxy } = getCurrentInstance()
const loading = ref(false)
const projectList = ref([])
const total = ref(0)
const projectFormRef = ref()
const chapterFormRef = ref()

const queryParams = reactive({ pageNum: 1, pageSize: 12, projectName: '', status: '' })
const projectDialog = reactive({ open: false, title: '' })
const chapterDialog = reactive({ open: false })
const chapterDrawer = reactive({ open: false, loading: false, project: null, chapters: [] })
const chapterVideoDrawer = reactive({
  open: false,
  activeTab: 'shots',
  loading: false,
  preparing: false,
  prepareTotal: 0,
  preparedCount: 0,
  prepareErrorCount: 0,
  chapter: null,
  allAssets: [],
  sceneAssets: [],
  keyframeAssets: [],
  characterAssets: [],
  assets: [],
  taskByAssetId: {},
  loadError: '',
  taskLoadError: '',
  referenceLoadError: ''
})
const assetDrawer = reactive({ open: false, loading: false, assets: [], taskByAssetId: {}, loadError: '', taskLoadError: '', chapterId: null, assetType: '', status: '' })
const promptDialog = reactive({
  open: false,
  submitting: false,
  assetId: null,
  assetName: '',
  assetType: '',
  status: '',
  editable: false,
  promptText: '',
  negativePromptText: '',
  referenceReady: true,
  referenceMessage: ''
})
const regenerationDialog = reactive({
  open: false,
  loading: false,
  submitting: false,
  assetId: null,
  assetName: '',
  sceneReferenceAssetId: null,
  characterReferenceAssetIds: [],
  sceneAssets: [],
  characterAssets: [],
  loadError: '',
  selectionMessage: ''
})
const videoPromptDialog = reactive({
  open: false,
  submitting: false,
  assetId: null,
  sourceAssetId: null,
  assetName: '',
  status: '',
  editable: false,
  promptText: '',
  negativePromptText: '',
  durationSeconds: 5,
  shotSize: '',
  cameraMovement: '',
  compositionText: '',
  actionText: '',
  emotionText: '',
  dialogueText: '',
  modelCode: ''
})
const storyBibleDrawer = reactive({ open: false, chapter: null, data: null })
const preparingVideoDraftId = ref(null)
const preparingChapterId = ref(null)
const regeneratingAssetId = ref(null)
const deletingAssetId = ref(null)
const keyframeReferenceOverrides = new Map()
const busyAssetStatuses = new Set([
  'PENDING', 'QUEUED', 'SUBMITTING', 'SUBMITTED', 'PROCESSING', 'GENERATING',
  'RUNNING', 'POLLING', 'WAITING', 'WAITING_CALLBACK', 'RETRYING', 'NEEDS_REVIEW',
  'VALIDATING', 'QUALITY_CHECK'
])
const chapterVideoTitle = computed(() => chapterVideoDrawer.chapter?.chapterTitle || `第 ${chapterVideoDrawer.chapter?.chapterNo || ''} 章`)
const assetChapterFilterLabel = computed(() => {
  const chapter = chapterDrawer.chapters.find(item => item.chapterId === assetDrawer.chapterId)
  return chapter?.chapterTitle || (chapter ? `第 ${chapter.chapterNo} 章` : `章节 #${assetDrawer.chapterId}`)
})
const chapterSceneGroups = computed(() => buildChapterSceneGroups(chapterVideoDrawer.sceneAssets))
const chapterShotGroups = computed(() => buildChapterShotGroups(
  chapterVideoDrawer.keyframeAssets,
  chapterVideoDrawer.allAssets.filter(asset => asset.assetType === 'SCENE_REFERENCE'),
  chapterVideoDrawer.characterAssets
))
const chapterVideoGroups = computed(() => buildChapterVideoGroups(chapterVideoDrawer.assets))
const chapterShotCount = computed(() => chapterShotGroups.value.reduce((total, scene) => total + scene.shots.length, 0))
const chapterVideoShotCount = computed(() => chapterVideoGroups.value.reduce((total, scene) => total + scene.shots.length, 0))
let chapterPollTimer = null
let assetPollTimer = null
let chapterVideoPollTimer = null
let assetLoadRequestId = 0
let chapterVideoLoadRequestId = 0
let regenerationLoadRequestId = 0
const projectForm = reactive({})
const chapterForm = reactive({})
const projectRules = { projectName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }] }
const chapterRules = {
  chapterNo: [{ required: true, message: '请输入章节序号', trigger: 'change' }],
  sourceText: [{ required: true, message: '请粘贴章节原文', trigger: 'blur' }]
}

function resetProjectForm() {
  Object.assign(projectForm, {
    projectId: null,
    projectName: '',
    sourceType: 'NOVEL',
    adaptationMode: 'FAITHFUL',
    defaultAspectRatio: '16:9',
    defaultLanguage: 'zh-CN',
    visualStyle: ''
  })
}

function getProjectList() {
  loading.value = true
  listAiVideoProject(queryParams).then(response => {
    projectList.value = response.rows || []
    total.value = Number(response.total) || 0
  }).finally(() => { loading.value = false })
}

function handleQuery() {
  queryParams.pageNum = 1
  getProjectList()
}

function resetQuery() {
  queryParams.projectName = ''
  queryParams.status = ''
  handleQuery()
}

function handleAddProject() {
  resetProjectForm()
  projectDialog.title = '新建影视项目'
  projectDialog.open = true
}

function handleProjectCommand(command, project) {
  if (command === 'edit') {
    getAiVideoProject(project.projectId).then(response => {
      Object.assign(projectForm, response.data)
      projectDialog.title = '编辑影视项目'
      projectDialog.open = true
    })
    return
  }
  proxy.$modal.confirm(`确认删除项目“${project.projectName}”吗？`).then(() => delAiVideoProject(project.projectId)).then(() => {
    proxy.$modal.msgSuccess('项目已删除')
    getProjectList()
  }).catch(() => {})
}

function submitProject() {
  projectFormRef.value.validate(valid => {
    if (!valid) return
    const request = projectForm.projectId ? updateAiVideoProject : addAiVideoProject
    request(projectForm).then(() => {
      proxy.$modal.msgSuccess('项目已保存')
      projectDialog.open = false
      getProjectList()
    })
  })
}

function openChapterDrawer(project) {
  if (chapterDrawer.project?.projectId !== project.projectId) {
    resetAssetDrawerData()
    resetChapterVideoDrawerData()
    chapterVideoDrawer.open = false
    chapterVideoDrawer.chapter = null
  }
  chapterDrawer.project = project
  chapterDrawer.open = true
  loadChapters()
}

function loadChapters() {
  if (!chapterDrawer.project) return
  chapterDrawer.loading = true
  listAiVideoChapter(chapterDrawer.project.projectId).then(response => {
    chapterDrawer.chapters = response.data || []
    scheduleChapterPolling()
  }).finally(() => { chapterDrawer.loading = false })
}

function openChapterDialog() {
  Object.assign(chapterForm, { chapterNo: chapterDrawer.chapters.length + 1, chapterTitle: '', sourceText: '' })
  chapterDialog.open = true
}

function submitChapter() {
  chapterFormRef.value.validate(valid => {
    if (!valid) return
    addAiVideoChapter(chapterDrawer.project.projectId, chapterForm).then(() => {
      proxy.$modal.msgSuccess('章节已导入，等待解析')
      chapterDialog.open = false
      loadChapters()
    })
  })
}

function removeChapter(chapter) {
  proxy.$modal.confirm(`确认删除第 ${chapter.chapterNo} 章吗？`).then(() => {
    return delAiVideoChapter(chapterDrawer.project.projectId, chapter.chapterId)
  }).then(() => {
    proxy.$modal.msgSuccess('章节已删除')
    loadChapters()
  }).catch(() => {})
}

function analyzeChapter(chapter) {
  analyzeAiVideoChapter(chapterDrawer.project.projectId, chapter.chapterId).then(response => {
    proxy.$modal.msgSuccess(`已创建解析任务 #${response.taskId}`)
    loadChapters()
  })
}

function scheduleChapterPolling() {
  stopChapterPolling()
  if (chapterDrawer.open && chapterDrawer.chapters.some(item => item.parseStatus === 'RUNNING')) {
    chapterPollTimer = setTimeout(() => loadChapters(), 5000)
  }
}

function stopChapterPolling() {
  if (chapterPollTimer) {
    clearTimeout(chapterPollTimer)
    chapterPollTimer = null
  }
}

function openStoryBible(chapter) {
  getAiVideoStoryBible(chapterDrawer.project.projectId, chapter.chapterId).then(response => {
    const raw = response.data?.contentJson
    if (!raw) {
      proxy.$modal.msgWarning('当前章节尚未生成故事圣经')
      return
    }
    try {
      storyBibleDrawer.data = JSON.parse(raw)
      storyBibleDrawer.chapter = chapter
      storyBibleDrawer.open = true
    } catch (error) {
      proxy.$modal.msgError('故事圣经格式异常，请重新解析章节')
    }
  })
}

function openChapterVideoWorkspace(chapter) {
  if (!activateChapterVideoWorkspace(chapter) || chapterVideoDrawer.preparing) return
  chapterVideoDrawer.activeTab = 'shots'
  loadChapterVideoAssets()
}

function activateChapterVideoWorkspace(chapter) {
  if (!chapter?.chapterId) return false
  if (chapterVideoDrawer.preparing && chapterVideoDrawer.chapter?.chapterId !== chapter.chapterId) {
    proxy.$modal.msgWarning('请等待当前章节的视频草稿准备完成')
    return false
  }
  if (chapterVideoDrawer.chapter?.chapterId !== chapter.chapterId) resetChapterVideoDrawerData()
  chapterVideoDrawer.chapter = chapter
  chapterVideoDrawer.open = true
  return true
}

function prepareChapterVideoWorkspace(chapter) {
  if (!activateChapterVideoWorkspace(chapter) || chapterVideoDrawer.preparing) return
  chapterVideoDrawer.activeTab = 'videos'
  const projectId = chapterDrawer.project?.projectId
  const chapterId = chapter.chapterId
  if (!projectId) return
  stopChapterVideoPolling()
  preparingChapterId.value = chapterId
  Object.assign(chapterVideoDrawer, {
    preparing: true,
    loading: true,
    prepareTotal: 0,
    preparedCount: 0,
    prepareErrorCount: 0,
    loadError: ''
  })
  Promise.all([
    fetchAllAiVideoAssets({ projectId, chapterId, assetType: 'SHOT_KEYFRAME', status: 'APPROVED' }),
    fetchAllAiVideoAssets({ projectId, chapterId, assetType: 'VIDEO_CLIP' })
  ]).then(([keyframeAssets, videoAssets]) => {
    const keyframes = selectLatestApprovedKeyframes(keyframeAssets
      .filter(asset => isCurrentAnalysisAsset(asset, chapter)))
    const videos = videoAssets
    chapterVideoDrawer.assets = videos
    chapterVideoDrawer.loading = false
    const candidates = keyframes.filter(keyframe => !hasVideoForKeyframe(keyframe, videos))
    chapterVideoDrawer.prepareTotal = candidates.length
    if (!keyframes.length) {
      proxy.$modal.msgWarning('本章还没有已同意的关键帧，请先检查并审批关键帧图片')
      return null
    }
    if (!candidates.length) {
      proxy.$modal.msgSuccess('本章已同意的镜头均已有视频版本，未重复创建草稿')
      return null
    }
    return prepareVideoDraftsSequentially(candidates)
  }).then(() => {
    if (!chapterVideoDrawer.prepareTotal) return
    if (chapterVideoDrawer.preparedCount) {
      proxy.$modal.msgSuccess(`已准备 ${chapterVideoDrawer.preparedCount} 个视频提示词草稿，尚未调用 Wanx`)
    }
    if (chapterVideoDrawer.prepareErrorCount) {
      proxy.$modal.msgWarning(`${chapterVideoDrawer.prepareErrorCount} 个镜头未能创建草稿，请按接口提示检查后重试`)
    }
  }).catch(() => {
    chapterVideoDrawer.loadError = '本章关键帧或视频版本加载失败，请检查服务状态后重试'
  }).finally(() => {
    chapterVideoDrawer.preparing = false
    chapterVideoDrawer.loading = false
    if (preparingChapterId.value === chapterId) preparingChapterId.value = null
    if (chapterVideoDrawer.open && chapterVideoDrawer.chapter?.chapterId === chapterId) loadChapterVideoAssets()
  })
}

async function fetchAllAiVideoAssets(query) {
  const pageSize = 200
  const assets = []
  const seenAssetIds = new Set()
  let fetchedRowCount = 0
  for (let pageNum = 1; pageNum <= 10000; pageNum += 1) {
    const response = await listAiVideoAsset({ ...query, pageNum, pageSize })
    const rows = Array.isArray(response?.rows) ? response.rows : []
    fetchedRowCount += rows.length
    rows.forEach(asset => {
      const identity = asset?.assetId === null || asset?.assetId === undefined
        ? `page-${pageNum}-row-${assets.length}`
        : String(asset.assetId)
      if (seenAssetIds.has(identity)) return
      seenAssetIds.add(identity)
      assets.push(asset)
    })
    const total = Number(response?.total)
    const hasTotal = Number.isFinite(total) && total >= 0
    if (!rows.length || (hasTotal && fetchedRowCount >= total) || (!hasTotal && rows.length < pageSize)) {
      return assets
    }
  }
  throw new Error('资产分页数量异常，请缩小筛选范围后重试')
}

function selectLatestApprovedKeyframes(keyframes) {
  const latestByShot = new Map()
  ;(keyframes || []).forEach(keyframe => {
    const key = keyframe.shotId ? `shot-${keyframe.shotId}` : `asset-${keyframe.assetCode || keyframe.assetId}`
    const current = latestByShot.get(key)
    const isNewer = !current
      || (Number(keyframe.versionNo) || 0) > (Number(current.versionNo) || 0)
      || ((Number(keyframe.versionNo) || 0) === (Number(current.versionNo) || 0)
        && (Number(keyframe.assetId) || 0) > (Number(current.assetId) || 0))
    if (isNewer) latestByShot.set(key, keyframe)
  })
  return Array.from(latestByShot.values())
}

function hasVideoForKeyframe(keyframe, videos) {
  return (videos || []).some(video => {
    if (video.sourceAssetId) return String(video.sourceAssetId) === String(keyframe.assetId)
    // 仅兼容没有保存 sourceAssetId 的旧开发数据；新链路始终按具体关键帧版本判断。
    return keyframe.shotId && video.shotId && String(keyframe.shotId) === String(video.shotId)
  })
}

function assetAnalysisVersion(asset) {
  const metadataVersion = Number(parseJsonObject(asset?.metadataJson).analysisVersion)
  if (Number.isFinite(metadataVersion) && metadataVersion > 0) return metadataVersion
  return 0
}

function isCurrentAnalysisAsset(asset, chapter) {
  const currentVersion = Number(chapter?.currentBibleVersion)
  if (!Number.isFinite(currentVersion) || currentVersion <= 0) return true
  const metadataVersion = assetAnalysisVersion(asset)
  if (metadataVersion > 0) return metadataVersion === currentVersion
  // 兼容加 analysisVersion 之前创建、且尚未重生成的开发数据。
  const legacyVersion = Number(asset?.versionNo)
  return Number.isFinite(legacyVersion) && legacyVersion === currentVersion
}

function prepareVideoDraftsSequentially(keyframes) {
  return keyframes.reduce((chain, keyframe) => chain.then(() => {
    return createAiVideoAssetVideoDraft(keyframe.assetId).then(() => {
      chapterVideoDrawer.preparedCount += 1
    }).catch(() => {
      chapterVideoDrawer.prepareErrorCount += 1
    })
  }), Promise.resolve())
}

function resetChapterVideoDrawerData() {
  chapterVideoLoadRequestId += 1
  stopChapterVideoPolling()
  chapterVideoDrawer.activeTab = 'shots'
  chapterVideoDrawer.loading = false
  chapterVideoDrawer.allAssets = []
  chapterVideoDrawer.sceneAssets = []
  chapterVideoDrawer.keyframeAssets = []
  chapterVideoDrawer.characterAssets = []
  chapterVideoDrawer.assets = []
  chapterVideoDrawer.taskByAssetId = {}
  chapterVideoDrawer.loadError = ''
  chapterVideoDrawer.taskLoadError = ''
  chapterVideoDrawer.referenceLoadError = ''
  chapterVideoDrawer.prepareTotal = 0
  chapterVideoDrawer.preparedCount = 0
  chapterVideoDrawer.prepareErrorCount = 0
}

function loadChapterVideoAssets() {
  if (chapterVideoDrawer.preparing) return
  const projectId = chapterDrawer.project?.projectId
  const chapterId = chapterVideoDrawer.chapter?.chapterId
  if (!projectId || !chapterId) return
  const requestId = ++chapterVideoLoadRequestId
  chapterVideoDrawer.loading = true
  chapterVideoDrawer.loadError = ''
  chapterVideoDrawer.referenceLoadError = ''
  Promise.allSettled([
    fetchAllAiVideoAssets({ projectId, chapterId }),
    fetchAllAiVideoAssets({ projectId, assetType: 'CHARACTER_REFERENCE' }),
    fetchAllAiVideoAssets({ projectId, assetType: 'SCENE_REFERENCE' }),
    listAiVideoTask(projectId)
  ]).then(([assetResult, characterResult, sceneReferenceResult, taskResult]) => {
    if (requestId !== chapterVideoLoadRequestId) return
    if (assetResult.status === 'fulfilled') {
      const chapterAssets = assetResult.value
      const projectCharacterAssets = characterResult.status === 'fulfilled' ? characterResult.value : []
      const projectSceneAssets = sceneReferenceResult.status === 'fulfilled' ? sceneReferenceResult.value : []
      const allAssetsById = [...chapterAssets, ...projectCharacterAssets, ...projectSceneAssets].reduce((result, asset) => {
        if (asset?.assetId) result.set(String(asset.assetId), asset)
        return result
      }, new Map())
      chapterVideoDrawer.allAssets = Array.from(allAssetsById.values())
      chapterVideoDrawer.sceneAssets = chapterAssets.filter(asset => asset.assetType === 'SCENE_REFERENCE')
      chapterVideoDrawer.keyframeAssets = chapterAssets.filter(asset => asset.assetType === 'SHOT_KEYFRAME')
      chapterVideoDrawer.characterAssets = chapterVideoDrawer.allAssets.filter(asset => asset.assetType === 'CHARACTER_REFERENCE')
      chapterVideoDrawer.assets = chapterAssets.filter(asset => asset.assetType === 'VIDEO_CLIP')
      if (characterResult.status !== 'fulfilled' || sceneReferenceResult.status !== 'fulfilled') {
        chapterVideoDrawer.referenceLoadError = '项目级场景或人物参考图读取失败；关键帧生成会保持锁定'
      }
    } else {
      chapterVideoDrawer.allAssets = []
      chapterVideoDrawer.sceneAssets = []
      chapterVideoDrawer.keyframeAssets = []
      chapterVideoDrawer.characterAssets = []
      chapterVideoDrawer.assets = []
      chapterVideoDrawer.loadError = '本章素材加载失败，请检查服务状态后重试'
    }
    if (taskResult.status === 'fulfilled') {
      chapterVideoDrawer.taskLoadError = ''
      chapterVideoDrawer.taskByAssetId = (taskResult.value.data || []).reduce((result, item) => {
        if (item.assetId && !result[item.assetId]) result[item.assetId] = item
        return result
      }, {})
    } else {
      chapterVideoDrawer.taskByAssetId = {}
      chapterVideoDrawer.taskLoadError = '无法读取本章视频任务状态'
    }
    if (!chapterVideoDrawer.loadError) scheduleChapterVideoPolling()
  }).finally(() => {
    if (requestId === chapterVideoLoadRequestId) chapterVideoDrawer.loading = false
  })
}

function assetContextSources(asset) {
  const metadata = parseJsonObject(asset?.metadataJson)
  const generationParams = parseJsonObject(asset?.generationParamsJson)
  return [
    asset || {},
    metadata,
    parseJsonObject(metadata.shot),
    parseJsonObject(metadata.promptContext),
    parseJsonObject(metadata.promptContextJson),
    generationParams,
    parseJsonObject(generationParams.shot),
    parseJsonObject(generationParams.promptContext),
    parseJsonObject(generationParams.promptContextJson)
  ]
}

function compareAssetVersionDesc(left, right) {
  return (Number(right?.versionNo) || 0) - (Number(left?.versionNo) || 0)
    || (Number(right?.assetId) || 0) - (Number(left?.assetId) || 0)
}

function buildChapterSceneGroups(assets) {
  const sceneMap = new Map()
  ;(assets || []).forEach(asset => {
    const sources = assetContextSources(asset)
    const sceneNo = pickContextValue(sources, ['sceneNo', 'sceneNumber'])
    const identity = asset.sceneId ?? sceneNo ?? asset.assetCode ?? asset.assetId
    const key = `scene-reference-${identity}`
    if (!sceneMap.has(key)) {
      sceneMap.set(key, {
        key,
        sortValue: sceneNo || asset.sceneId,
        sceneNo,
        label: asset.assetName || (sceneNo ? `第 ${sceneNo} 场` : `场景 #${asset.sceneId || ''}`),
        versions: []
      })
    }
    sceneMap.get(key).versions.push(asset)
  })
  return Array.from(sceneMap.values()).map(scene => ({
    ...scene,
    versions: scene.versions.sort(compareAssetVersionDesc)
  })).sort(compareAssetGroupOrder)
}

function referenceAnalysisVersion(asset) {
  const metadataVersion = assetAnalysisVersion(asset)
  if (metadataVersion > 0) return metadataVersion
  const legacyVersion = Number(asset?.versionNo)
  return Number.isFinite(legacyVersion) && legacyVersion > 0 ? legacyVersion : 0
}

function hasOwnReferenceField(metadata, field) {
  return metadata && typeof metadata === 'object' && Object.prototype.hasOwnProperty.call(metadata, field)
}

function normalizeReferenceAssetId(value) {
  if (value === null || value === undefined || String(value).trim() === '') return null
  const numeric = Number(value)
  return Number.isFinite(numeric) && numeric > 0 ? String(Math.trunc(numeric)) : String(value).trim()
}

function referenceAssetIds(value) {
  let values = value
  if (typeof values === 'string') {
    try {
      values = JSON.parse(values)
    } catch (error) {
      values = values.split(',')
    }
  }
  if (!Array.isArray(values)) return []
  return Array.from(new Set(values.map(normalizeReferenceAssetId).filter(Boolean)))
}

function strictReferenceAssetIds(value) {
  let values = value
  if (typeof values === 'string') {
    try {
      values = JSON.parse(values)
    } catch (error) {
      return { valid: false, ids: [] }
    }
  }
  if (!Array.isArray(values)) return { valid: false, ids: [] }
  const ids = values.map(normalizeReferenceAssetId)
  if (ids.some(id => !id) || new Set(ids).size !== ids.length) return { valid: false, ids: [] }
  return { valid: true, ids }
}

function keyframeReferenceMetadataState(asset) {
  const localOverride = keyframeReferenceOverrides.get(String(asset?.assetId))
  const metadata = {
    ...parseJsonObject(asset?.metadataJson),
    ...(localOverride || {})
  }
  const hasSceneReferenceAssetId = hasOwnReferenceField(metadata, 'sceneReferenceAssetId')
  const hasCharacterReferenceAssetIds = hasOwnReferenceField(metadata, 'characterReferenceAssetIds')
  if (!hasSceneReferenceAssetId || !hasCharacterReferenceAssetIds) {
    return {
      valid: false,
      message: '关键帧缺少完整的精确参考 metadata（sceneReferenceAssetId / characterReferenceAssetIds），请重新解析章节或重新选择参考版本',
      sceneReferenceAssetId: null,
      characterReferenceAssetIds: []
    }
  }
  const sceneReferenceAssetId = normalizeReferenceAssetId(metadata.sceneReferenceAssetId)
  if (!sceneReferenceAssetId) {
    return {
      valid: false,
      message: '关键帧的场景参考 metadata 无效，当前禁止生成',
      sceneReferenceAssetId: null,
      characterReferenceAssetIds: []
    }
  }
  const characterState = strictReferenceAssetIds(metadata.characterReferenceAssetIds)
  if (!characterState.valid || characterState.ids.length > 2) {
    return {
      valid: false,
      message: '关键帧的人物参考 metadata 必须是 0 至 2 个不重复的有效资产 ID，当前禁止生成',
      sceneReferenceAssetId,
      characterReferenceAssetIds: []
    }
  }
  const sourceAssetId = normalizeReferenceAssetId(asset?.sourceAssetId)
  if (!sourceAssetId || sourceAssetId !== sceneReferenceAssetId) {
    return {
      valid: false,
      message: '关键帧的场景参考关系与 metadata 不一致，当前禁止生成；请重新解析章节或重新选择参考版本',
      sceneReferenceAssetId,
      characterReferenceAssetIds: characterState.ids
    }
  }
  return {
    valid: true,
    message: '',
    sceneReferenceAssetId,
    characterReferenceAssetIds: characterState.ids
  }
}

function findReferenceAssetById(assets, assetId) {
  const normalizedId = normalizeReferenceAssetId(assetId)
  if (!normalizedId) return null
  return (assets || []).find(asset => String(asset.assetId) === normalizedId) || null
}

function referenceReadiness(sceneReference, characterReferences, sceneReferenceRequired = true) {
  const references = [
    ...(sceneReferenceRequired ? [{ name: '场景参考图', asset: sceneReference }] : []),
    ...(characterReferences || [])
  ]
  const missing = references.filter(reference => !reference.asset)
  const pending = references.filter(reference => reference.asset && reference.asset.status !== 'APPROVED')
  if (missing.length) {
    return {
      approved: false,
      message: `缺少 ${missing.map(reference => reference.name).join('、')}；请重新解析章节或恢复对应资产`
    }
  }
  if (pending.length) {
    return {
      approved: false,
      message: `${pending.map(reference => reference.name).join('、')} 尚未完成并批准；请先点击上方参考卡生成并确认参考图`
    }
  }
  return {
    approved: references.length > 0,
    message: references.length ? '场景和人物引用图已全部完成，可生成关键帧' : '当前分镜没有可用的引用图'
  }
}

function buildChapterShotGroups(keyframes, sceneAssets, characterAssets) {
  const sceneMap = new Map()
  ;(keyframes || []).forEach(asset => {
    const sources = assetContextSources(asset)
    const shotNo = pickContextValue(sources, ['shotNo', 'shotNumber'])
    const sceneIdentity = asset.sceneId ?? pickContextValue(sources, ['sceneNo', 'sceneNumber']) ?? 'unassigned'
    const sceneKey = `shot-scene-${sceneIdentity}`
    if (!sceneMap.has(sceneKey)) {
      sceneMap.set(sceneKey, {
        key: sceneKey,
        sceneId: asset.sceneId,
        sortValue: asset.sceneId,
        shotMap: new Map()
      })
    }
    const scene = sceneMap.get(sceneKey)
    const shotIdentity = asset.shotId ?? shotNo ?? asset.assetCode ?? asset.assetId
    const shotKey = `keyframe-${shotIdentity}`
    if (!scene.shotMap.has(shotKey)) {
      scene.shotMap.set(shotKey, {
        key: shotKey,
        shotId: asset.shotId,
        shotNo,
        sortValue: shotNo || asset.shotId,
        versions: []
      })
    }
    scene.shotMap.get(shotKey).versions.push(asset)
  })

  return Array.from(sceneMap.values()).map(scene => {
    const shots = Array.from(scene.shotMap.values()).map(shot => {
      const versions = shot.versions.sort(compareAssetVersionDesc)
      const newest = versions[0]
      const analysisVersion = referenceAnalysisVersion(newest)
      const metadataState = keyframeReferenceMetadataState(newest)
      const explicitSceneReferenceAssetId = metadataState.sceneReferenceAssetId
      const explicitCharacterReferenceAssetIds = metadataState.characterReferenceAssetIds
      const sceneReference = findReferenceAssetById(sceneAssets, explicitSceneReferenceAssetId)
      const sceneNo = pickContextValue(assetContextSources(sceneReference), ['sceneNo', 'sceneNumber'])
      const characterReferences = explicitCharacterReferenceAssetIds.map(assetId => {
        const asset = findReferenceAssetById(characterAssets, assetId)
        return {
          key: `${shot.key}-character-asset-${assetId}`,
          assetId,
          name: asset?.assetName?.replace(/角色三视图|人物三视图|三视图/g, '').trim() || `人物资产 #${assetId}`,
          asset
        }
      })
      const readiness = metadataState.valid
        ? referenceReadiness(sceneReference, characterReferences)
        : { approved: false, message: metadataState.message }
      return {
        ...shot,
        label: newest?.assetName || (shotNo ? `镜头 ${shotNo}` : `镜头 #${shot.shotId || ''}`),
        versions,
        analysisVersion,
        sceneReference,
        sceneReferenceAssetId: explicitSceneReferenceAssetId,
        characterReferences,
        characterReferenceNote: characterReferences.length
          ? ''
          : (metadataState.valid ? 'metadata 已明确：本分镜无需人物参考图' : metadataState.message),
        hasExplicitReferenceIds: metadataState.valid,
        referencesApproved: readiness.approved,
        referenceStatusMessage: readiness.message
      }
    }).sort(compareAssetGroupOrder)
    const firstShot = shots[0]
    const sceneReference = firstShot?.sceneReference
    const sceneNo = pickContextValue(assetContextSources(sceneReference), ['sceneNo', 'sceneNumber'])
    return {
      ...scene,
      sortValue: sceneNo || scene.sceneId,
      orderLabel: sceneNo ? `场景 ${sceneNo}` : (scene.sceneId ? `场景 ID ${scene.sceneId}` : '未分配场景'),
      label: sceneReference?.assetName || (sceneNo ? `第 ${sceneNo} 场` : (scene.sceneId ? `场景 #${scene.sceneId}` : '其他场景')),
      shots
    }
  }).sort(compareAssetGroupOrder)
}

function buildChapterVideoGroups(assets) {
  const sceneMap = new Map()
  ;(assets || []).forEach(asset => {
    const metadata = parseJsonObject(asset.metadataJson)
    const generationParams = parseJsonObject(asset.generationParamsJson)
    const sources = [
      asset,
      metadata,
      parseJsonObject(metadata.shot),
      parseJsonObject(metadata.promptContext),
      parseJsonObject(metadata.promptContextJson),
      generationParams,
      parseJsonObject(generationParams.shot),
      parseJsonObject(generationParams.promptContext),
      parseJsonObject(generationParams.promptContextJson)
    ]
    const sceneNo = pickContextValue(sources, ['sceneNo', 'sceneNumber'])
    const sceneName = pickContextValue(sources, ['sceneTitle', 'sceneName'])
    const sceneIdentity = asset.sceneId ?? sceneNo ?? 'unassigned'
    const sceneKey = `scene-${sceneIdentity}`
    if (!sceneMap.has(sceneKey)) {
      sceneMap.set(sceneKey, {
        key: sceneKey,
        sortValue: sceneNo || asset.sceneId,
        orderLabel: sceneNo ? `场景 ${sceneNo}` : (asset.sceneId ? `场景 ID ${asset.sceneId}` : '未分配场景'),
        label: sceneName || (sceneNo ? `第 ${sceneNo} 场` : (asset.sceneId ? `场景 #${asset.sceneId}` : '其他场景')),
        shotMap: new Map()
      })
    }
    const scene = sceneMap.get(sceneKey)
    const shotNo = pickContextValue(sources, ['shotNo', 'shotNumber'])
    const shotName = pickContextValue(sources, ['shotTitle', 'shotName'])
    const shotIdentity = asset.shotId ?? shotNo ?? asset.assetCode ?? asset.assetId
    const shotKey = `shot-${shotIdentity}`
    if (!scene.shotMap.has(shotKey)) {
      scene.shotMap.set(shotKey, {
        key: shotKey,
        sortValue: shotNo || asset.shotId,
        label: shotName || (shotNo ? `镜头 ${shotNo}` : (asset.shotId ? `镜头 #${asset.shotId}` : asset.assetName)),
        versions: []
      })
    }
    scene.shotMap.get(shotKey).versions.push(asset)
  })
  return Array.from(sceneMap.values()).map(scene => ({
    key: scene.key,
    sortValue: scene.sortValue,
    orderLabel: scene.orderLabel,
    label: scene.label,
    shots: Array.from(scene.shotMap.values()).map(shot => ({
      ...shot,
      versions: shot.versions.sort((left, right) => (Number(right.versionNo) || 0) - (Number(left.versionNo) || 0)
        || (Number(right.assetId) || 0) - (Number(left.assetId) || 0))
    })).sort(compareAssetGroupOrder)
  })).sort(compareAssetGroupOrder)
}

function compareAssetGroupOrder(left, right) {
  const leftNumber = Number(left.sortValue)
  const rightNumber = Number(right.sortValue)
  if (Number.isFinite(leftNumber) && Number.isFinite(rightNumber)) return leftNumber - rightNumber
  if (Number.isFinite(leftNumber)) return -1
  if (Number.isFinite(rightNumber)) return 1
  return String(left.label || left.key).localeCompare(String(right.label || right.key), 'zh-CN')
}

function openChapterKeyframeAssets() {
  openAssetDrawer(chapterVideoDrawer.chapter)
}

function openAssetDrawer(chapter = null) {
  const selectedChapter = chapter?.chapterId ? chapter : null
  resetAssetDrawerData()
  assetDrawer.chapterId = selectedChapter?.chapterId || null
  assetDrawer.assetType = selectedChapter ? 'SHOT_KEYFRAME' : ''
  assetDrawer.status = ''
  assetDrawer.open = true
  loadAssets()
}

function clearAssetChapterFilter() {
  assetDrawer.chapterId = null
  loadAssets()
}

function resetAssetDrawerData() {
  assetLoadRequestId += 1
  stopAssetPolling()
  assetDrawer.loading = false
  assetDrawer.assets = []
  assetDrawer.taskByAssetId = {}
  assetDrawer.loadError = ''
  assetDrawer.taskLoadError = ''
}

function loadAssets() {
  const projectId = chapterDrawer.project?.projectId
  if (!projectId) return
  const requestId = ++assetLoadRequestId
  assetDrawer.loading = true
  assetDrawer.loadError = ''
  const assetQuery = {
    projectId,
    chapterId: assetDrawer.chapterId || undefined,
    assetType: assetDrawer.assetType || undefined,
    status: assetDrawer.status || undefined
  }
  Promise.allSettled([fetchAllAiVideoAssets(assetQuery), listAiVideoTask(projectId)]).then(([assetResult, taskResult]) => {
    if (requestId !== assetLoadRequestId) return
    if (assetResult.status === 'fulfilled') {
      assetDrawer.assets = assetResult.value
    } else {
      assetDrawer.assets = []
      assetDrawer.loadError = '资产列表加载失败，请检查服务状态后重试'
    }
    if (taskResult.status === 'fulfilled') {
      assetDrawer.taskLoadError = ''
      assetDrawer.taskByAssetId = (taskResult.value.data || []).reduce((result, item) => {
        if (item.assetId && !result[item.assetId]) result[item.assetId] = item
        return result
      }, {})
    } else {
      assetDrawer.taskByAssetId = {}
      assetDrawer.taskLoadError = '无法读取任务状态：请确认后端已重启并加载 AI 视频任务接口'
    }
    if (!assetDrawer.loadError) scheduleAssetPolling()
  }).finally(() => {
    if (requestId === assetLoadRequestId) assetDrawer.loading = false
  })
}

function refreshAssetViews() {
  if (assetDrawer.open) loadAssets()
  if (chapterVideoDrawer.open) loadChapterVideoAssets()
}

function createRegenerationDraft(asset) {
  if (!asset?.assetId || regeneratingAssetId.value !== null) return
  if (asset.assetType === 'SHOT_KEYFRAME') {
    openKeyframeRegenerationDialog(asset)
    return
  }
  submitRegenerationDraft(asset)
}

function currentKeyframeReferenceSelection(asset) {
  const localOverride = keyframeReferenceOverrides.get(String(asset?.assetId))
  const metadata = {
    ...parseJsonObject(asset?.metadataJson),
    ...(localOverride || {})
  }
  return {
    sceneReferenceAssetId: normalizeReferenceAssetId(metadata.sceneReferenceAssetId)
      || normalizeReferenceAssetId(asset?.sourceAssetId),
    characterReferenceAssetIds: referenceAssetIds(metadata.characterReferenceAssetIds).slice(0, 2)
  }
}

function referenceOptionById(assets, assetId) {
  const normalizedId = normalizeReferenceAssetId(assetId)
  if (!normalizedId) return null
  return (assets || []).find(asset => String(asset.assetId) === normalizedId) || null
}

function sortReferenceOptions(assets) {
  return (assets || []).slice().sort((left, right) => {
    const nameOrder = String(left.assetName || '').localeCompare(String(right.assetName || ''), 'zh-CN')
    return nameOrder || compareAssetVersionDesc(left, right)
  })
}

function openKeyframeRegenerationDialog(asset) {
  const projectId = chapterDrawer.project?.projectId || asset.projectId
  if (!projectId) {
    proxy.$modal.msgError('项目ID缺失，无法加载参考资产')
    return
  }
  const currentSelection = currentKeyframeReferenceSelection(asset)
  const requestId = ++regenerationLoadRequestId
  Object.assign(regenerationDialog, {
    open: true,
    loading: true,
    submitting: false,
    assetId: asset.assetId,
    assetName: asset.assetName || '关键帧',
    sceneReferenceAssetId: null,
    characterReferenceAssetIds: [],
    sceneAssets: [],
    characterAssets: [],
    loadError: '',
    selectionMessage: ''
  })
  Promise.all([
    fetchAllAiVideoAssets({ projectId, assetType: 'SCENE_REFERENCE', status: 'APPROVED' }),
    fetchAllAiVideoAssets({ projectId, assetType: 'CHARACTER_REFERENCE', status: 'APPROVED' })
  ]).then(([sceneAssets, characterAssets]) => {
    if (requestId !== regenerationLoadRequestId || regenerationDialog.assetId !== asset.assetId) return
    regenerationDialog.sceneAssets = sortReferenceOptions(sceneAssets)
    regenerationDialog.characterAssets = sortReferenceOptions(characterAssets)
    const currentScene = referenceOptionById(sceneAssets, currentSelection.sceneReferenceAssetId)
    const currentCharacters = currentSelection.characterReferenceAssetIds
      .map(assetId => referenceOptionById(characterAssets, assetId))
      .filter(Boolean)
    regenerationDialog.sceneReferenceAssetId = currentScene?.assetId || null
    regenerationDialog.characterReferenceAssetIds = currentCharacters.map(item => item.assetId)
    const unavailableCount = currentSelection.characterReferenceAssetIds.length - currentCharacters.length
    if (!currentScene && currentSelection.sceneReferenceAssetId) {
      regenerationDialog.selectionMessage = '当前场景参考版本已删除或不再是 APPROVED，请重新选择一个可用版本'
    } else if (unavailableCount > 0) {
      regenerationDialog.selectionMessage = `${unavailableCount} 张当前人物参考版本已删除或不再是 APPROVED，请重新选择`
    }
  }).catch(() => {
    if (requestId === regenerationLoadRequestId && regenerationDialog.assetId === asset.assetId) {
      regenerationDialog.loadError = '同项目 APPROVED 参考资产加载失败，请检查服务状态后重试'
    }
  }).finally(() => {
    if (requestId === regenerationLoadRequestId && regenerationDialog.assetId === asset.assetId) {
      regenerationDialog.loading = false
    }
  })
}

function submitKeyframeRegenerationDraft() {
  const assetId = regenerationDialog.assetId
  const sceneReference = referenceOptionById(regenerationDialog.sceneAssets, regenerationDialog.sceneReferenceAssetId)
  const characterReferences = regenerationDialog.characterReferenceAssetIds
    .map(referenceAssetId => referenceOptionById(regenerationDialog.characterAssets, referenceAssetId))
    .filter(Boolean)
  if (!sceneReference) {
    proxy.$modal.msgWarning('请选择 1 张同项目且已 APPROVED 的场景参考图')
    return
  }
  if (characterReferences.length !== regenerationDialog.characterReferenceAssetIds.length
    || characterReferences.length > 2) {
    proxy.$modal.msgWarning('人物参考图必须是 0 至 2 张同项目且已 APPROVED 的人物三视图')
    return
  }
  const payload = {
    sceneReferenceAssetId: sceneReference.assetId,
    characterReferenceAssetIds: characterReferences.map(asset => asset.assetId)
  }
  regenerationDialog.submitting = true
  regeneratingAssetId.value = assetId
  createAiVideoAssetRegenerationDraft(assetId, payload).then(response => {
    const draft = response?.data || response?.asset || null
    if (!draft?.assetId) throw new Error('新版本草稿响应缺少资产信息')
    keyframeReferenceOverrides.set(String(draft.assetId), payload)
    regenerationDialog.open = false
    openPromptDialog(draft)
    proxy.$modal.msgSuccess(`已创建 ${draft.assetName || regenerationDialog.assetName} v${draft.versionNo || ''} 草稿并绑定所选参考版本；尚未调用任何生成模型`)
    refreshAssetViews()
  }).catch(error => {
    if (error?.message === '新版本草稿响应缺少资产信息') proxy.$modal.msgError(error.message)
  }).finally(() => {
    regenerationDialog.submitting = false
    if (regeneratingAssetId.value === assetId) regeneratingAssetId.value = null
  })
}

function submitRegenerationDraft(asset) {
  regeneratingAssetId.value = asset.assetId
  createAiVideoAssetRegenerationDraft(asset.assetId).then(response => {
    const draft = response?.data || response?.asset || null
    if (!draft?.assetId) throw new Error('新版本草稿响应缺少资产信息')
    if (draft.assetType === 'VIDEO_CLIP') {
      openVideoPromptDialog(draft)
    } else {
      openPromptDialog(draft)
    }
    proxy.$modal.msgSuccess(`已创建 ${draft.assetName || asset.assetName} v${draft.versionNo || ''} 草稿；尚未调用任何生成模型`)
    refreshAssetViews()
  }).catch(error => {
    if (error?.message === '新版本草稿响应缺少资产信息') proxy.$modal.msgError(error.message)
  }).finally(() => {
    if (regeneratingAssetId.value === asset.assetId) regeneratingAssetId.value = null
  })
}

function referenceOptionLabel(asset) {
  return `${asset?.assetName || '未命名参考资产'} · v${asset?.versionNo || 1}`
}

function deleteAssetVersion(asset) {
  const task = assetDrawer.taskByAssetId[asset.assetId] || chapterVideoDrawer.taskByAssetId[asset.assetId]
  if (isAssetBusy(asset, task)) {
    proxy.$modal.msgWarning('该资产仍在任务流程中，当前不能删除')
    return
  }
  const versionLabel = asset.versionNo ? ` v${asset.versionNo}` : ''
  proxy.$modal.confirm(`确认删除“${asset.assetName}”${versionLabel} 吗？只会删除这个资产版本，其他历史版本会继续保留。`).then(() => {
    deletingAssetId.value = asset.assetId
    return delAiVideoAsset(asset.assetId)
  }).then(() => {
    proxy.$modal.msgSuccess('资产版本已删除')
    refreshAssetViews()
  }).catch(() => {}).finally(() => {
    if (deletingAssetId.value === asset.assetId) deletingAssetId.value = null
  })
}

function isAssetBusy(asset, task) {
  const assetStatus = String(asset?.status || '').toUpperCase()
  const taskStatus = String(task?.status || '').toUpperCase()
  return busyAssetStatuses.has(assetStatus) || busyAssetStatuses.has(taskStatus)
}

function isLatestAssetVersion(asset, assets) {
  if (!asset?.assetCode) return true
  const latestVersion = (assets || []).filter(item => item.assetCode === asset.assetCode)
    .reduce((maxVersion, item) => Math.max(maxVersion, Number(item.versionNo) || 0), 0)
  return (Number(asset.versionNo) || 0) === latestVersion
}

function approveAndPrepareVideoPrompt(asset) {
  proxy.$modal.confirm(`请确认“${asset.assetName}”的关键帧画面可以用于视频生成。确认后只会同意图片并创建可编辑的视频提示词草稿，不会调用 Wanx。`).then(() => {
    return approveAiVideoAsset(asset.assetId)
  }).then(() => {
    return prepareVideoPromptDraft(asset, true)
  }).catch(() => {
    refreshAssetViews()
  })
}

function prepareVideoPromptDraft(asset, justApproved = false) {
  if (!asset?.assetId || preparingVideoDraftId.value !== null) return Promise.resolve(null)
  preparingVideoDraftId.value = asset.assetId
  return createAiVideoAssetVideoDraft(asset.assetId).then(response => {
    const draft = response?.data || response?.asset || null
    if (!draft?.assetId) {
      throw new Error('视频提示词草稿响应缺少资产信息')
    }
    openVideoPromptDialog(draft)
    proxy.$modal.msgSuccess(justApproved
      ? '关键帧已同意，视频提示词草稿已准备好；当前尚未调用 Wanx'
      : '视频提示词草稿已准备好；当前尚未调用 Wanx')
    refreshAssetViews()
    return draft
  }).catch(error => {
    if (error?.message === '视频提示词草稿响应缺少资产信息') {
      proxy.$modal.msgError(error.message)
    }
    if (justApproved) refreshAssetViews()
    return null
  }).finally(() => {
    if (preparingVideoDraftId.value === asset.assetId) preparingVideoDraftId.value = null
  })
}

function openVideoPromptDialog(asset) {
  syncVideoPromptDialogFromAsset(asset)
  videoPromptDialog.open = true
  videoPromptDialog.submitting = false
}

function syncVideoPromptDialogFromAsset(asset) {
  const context = resolveVideoPromptContext(asset)
  const durationMs = Number(asset.durationMs || context.durationMs) || 5000
  Object.assign(videoPromptDialog, {
    assetId: asset.assetId,
    sourceAssetId: asset.sourceAssetId,
    assetName: asset.assetName || '视频片段',
    status: asset.status,
    editable: asset.status === 'DRAFT' || asset.status === 'REJECTED',
    promptText: asset.promptText || '',
    negativePromptText: asset.negativePromptText || '',
    durationSeconds: Math.min(5, Math.max(3, Math.round(durationMs / 1000))),
    shotSize: context.shotSize,
    cameraMovement: context.cameraMovement,
    compositionText: context.compositionText,
    actionText: context.actionText,
    emotionText: context.emotionText,
    dialogueText: context.dialogueText,
    modelCode: context.modelCode
  })
}

function resolveVideoPromptContext(asset) {
  const metadata = parseJsonObject(asset.metadataJson)
  const generationParams = parseJsonObject(asset.generationParamsJson)
  const sources = [
    asset,
    metadata,
    parseJsonObject(metadata.shot),
    parseJsonObject(metadata.shotSummary),
    parseJsonObject(metadata.promptContext),
    parseJsonObject(metadata.promptContextJson),
    generationParams,
    parseJsonObject(generationParams.shot),
    parseJsonObject(generationParams.shotSummary),
    parseJsonObject(generationParams.promptContext),
    parseJsonObject(generationParams.promptContextJson)
  ]
  return {
    durationMs: pickContextValue(sources, ['durationMs', 'duration']),
    shotSize: pickContextValue(sources, ['shotSize', 'shotType', 'framing']),
    cameraMovement: pickContextValue(sources, ['cameraMovement', 'cameraMotion', 'camera']),
    compositionText: pickContextValue(sources, ['compositionText', 'composition']),
    actionText: pickContextValue(sources, ['actionText', 'action', 'motionText', 'temporalProgression']),
    emotionText: pickContextValue(sources, ['emotionText', 'emotion']),
    dialogueText: formatDialogue(pickContextValue(sources, ['dialogueText', 'dialogueJson', 'dialogues', 'dialogue'])),
    modelCode: pickContextValue(sources, ['model', 'modelCode'])
  }
}

function parseJsonObject(value) {
  if (!value) return {}
  if (typeof value === 'object') return value
  if (typeof value !== 'string') return {}
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' ? parsed : {}
  } catch (error) {
    return {}
  }
}

function pickContextValue(sources, keys) {
  for (const source of sources) {
    if (!source || typeof source !== 'object') continue
    for (const key of keys) {
      const value = source[key]
      if (value !== undefined && value !== null && String(value).trim() !== '') return value
    }
  }
  return ''
}

function formatDialogue(value) {
  if (!value) return ''
  let dialogue = value
  if (typeof dialogue === 'string') {
    try {
      dialogue = JSON.parse(dialogue)
    } catch (error) {
      return dialogue
    }
  }
  if (Array.isArray(dialogue)) {
    return dialogue.map(item => {
      if (typeof item === 'string') return item
      if (!item || typeof item !== 'object') return ''
      const speaker = item.speaker || item.characterName || item.character || item.name || ''
      const line = item.line || item.text || item.content || item.dialogue || ''
      return speaker && line ? `${speaker}：${line}` : (line || speaker)
    }).filter(Boolean).join('；')
  }
  if (typeof dialogue === 'object') {
    return dialogue.text || dialogue.content || dialogue.line || JSON.stringify(dialogue)
  }
  return String(dialogue)
}

function saveVideoPrompt(shouldGenerate) {
  if (!videoPromptDialog.editable) return
  const assetId = videoPromptDialog.assetId
  const promptText = videoPromptDialog.promptText.trim()
  const negativePromptText = videoPromptDialog.negativePromptText.trim()
  const durationSeconds = Number(videoPromptDialog.durationSeconds)
  if (!promptText) {
    proxy.$modal.msgWarning('请填写正向视频提示词')
    return
  }
  if (!Number.isInteger(durationSeconds) || durationSeconds < 3 || durationSeconds > 5) {
    proxy.$modal.msgWarning('当前 Wanx 2.1 视频时长只能选择 3、4 或 5 秒')
    return
  }
  const payload = {
    promptText,
    negativePromptText,
    durationMs: Math.round(durationSeconds * 1000)
  }
  const submit = () => {
    let promptSaved = false
    videoPromptDialog.submitting = true
    return updateAiVideoAssetVideoPrompt(assetId, payload).then(response => {
      const savedAsset = response?.data
      if (savedAsset?.assetId === assetId) syncVideoPromptDialogFromAsset(savedAsset)
      promptSaved = true
      if (!shouldGenerate) {
        proxy.$modal.msgSuccess('视频提示词已保存，尚未调用 Wanx')
        if (videoPromptDialog.assetId === assetId) videoPromptDialog.open = false
        return null
      }
      return generateAiVideoAssetVideo(assetId)
    }).then(response => {
      if (!shouldGenerate || !response) return
      const taskId = response.taskId ?? response.data?.taskId ?? response.data
      proxy.$modal.msgSuccess(taskId !== undefined && taskId !== null
        ? `已提交 Wanx 视频任务 #${taskId}`
        : '已提交 Wanx 视频生成任务')
      if (videoPromptDialog.assetId === assetId) videoPromptDialog.open = false
    }).finally(() => {
      if (promptSaved) refreshAssetViews()
      if (videoPromptDialog.assetId === assetId) videoPromptDialog.submitting = false
    })
  }
  if (shouldGenerate) {
    proxy.$modal.confirm(`确认使用当前提示词生成 ${durationSeconds} 秒视频吗？确认后将调用 Wanx，产生一次模型调用及相应费用，并进入视频生成队列。`).then(submit).catch(() => {})
  } else {
    submit().catch(() => {})
  }
}

function resumeWanxSubmission(asset, sourceDrawer = assetDrawer) {
  const task = sourceDrawer.taskByAssetId[asset.assetId]
  const resume = providerTaskId => resolveAiVideoAssetWanxSubmission(asset.assetId, {
    action: 'RESUME_WITH_PROVIDER_TASK_ID',
    providerTaskId
  }).then(() => {
    proxy.$modal.msgSuccess('已恢复 Wanx 任务轮询，不会重复创建视频任务')
    refreshAssetViews()
  })
  if (task?.providerTaskId) {
    proxy.$modal.confirm(`检测到已保存的 Wanx 任务ID：${task.providerTaskId}。确认恢复该任务的结果轮询吗？此操作不会重新生成或重复计费。`)
      .then(() => resume(task.providerTaskId)).catch(() => {})
    return
  }
  proxy.$modal.prompt('请先在阿里云百炼 / DashScope 控制台核对本次调用，再粘贴对应的 Wanx 任务ID。系统只会恢复结果轮询，不会重新提交生成。')
    .then(({ value }) => resume(String(value || '').trim())).catch(() => {})
}

function confirmWanxNotSubmitted(asset) {
  proxy.$modal.confirm('只有在阿里云百炼 / DashScope 控制台确认没有对应视频任务时才能解锁。错误确认可能导致重复生成和重复计费，是否继续？')
    .then(() => proxy.$modal.confirm('再次确认：Wanx 确实未受理本次请求。确认后草稿会变为失败状态，可修改后重新提交。'))
    .then(() => resolveAiVideoAssetWanxSubmission(asset.assetId, {
      action: 'CONFIRM_NOT_SUBMITTED'
    }))
    .then(() => {
      proxy.$modal.msgSuccess('已记录人工核对结果，视频草稿已解锁')
      refreshAssetViews()
    }).catch(() => {})
}

function findShotReferenceGate(asset) {
  if (!asset || asset.assetType !== 'SHOT_KEYFRAME') return null
  const metadataState = keyframeReferenceMetadataState(asset)
  if (!metadataState.valid) {
    return {
      referencesApproved: false,
      referenceStatusMessage: metadataState.message,
      sceneReferenceAssetId: metadataState.sceneReferenceAssetId,
      characterReferenceAssetIds: metadataState.characterReferenceAssetIds
    }
  }

  const loadedChapterId = chapterVideoDrawer.chapter?.chapterId
  if (loadedChapterId === null || loadedChapterId === undefined
    || String(loadedChapterId) !== String(asset.chapterId)) {
    return {
      referencesApproved: false,
      referenceStatusMessage: '资产库当前没有加载该关键帧的引用图，请点击章节名称进入章节素材工作台后生成'
    }
  }

  const loadedAssets = (chapterVideoDrawer.allAssets || []).filter(item =>
    !asset.projectId || !item.projectId || String(item.projectId) === String(asset.projectId)
  )
  const sceneReference = findReferenceAssetById(loadedAssets, metadataState.sceneReferenceAssetId)
  const characterReferences = metadataState.characterReferenceAssetIds.map(referenceAssetId => ({
    name: findReferenceAssetById(loadedAssets, referenceAssetId)?.assetName || `人物资产 #${referenceAssetId}`,
    asset: findReferenceAssetById(loadedAssets, referenceAssetId)
  }))
  const readiness = referenceReadiness(sceneReference, characterReferences)
  return {
    referencesApproved: readiness.approved,
    referenceStatusMessage: readiness.message,
    sceneReferenceAssetId: metadataState.sceneReferenceAssetId,
    characterReferenceAssetIds: metadataState.characterReferenceAssetIds
  }
}

function openPromptDialog(asset, referenceGate = null) {
  const resolvedReferenceGate = asset.assetType === 'SHOT_KEYFRAME'
    ? findShotReferenceGate(asset)
    : null
  Object.assign(promptDialog, {
    open: true,
    submitting: false,
    assetId: asset.assetId,
    assetName: asset.assetName,
    assetType: asset.assetType,
    status: asset.status,
    editable: asset.status === 'DRAFT' || asset.status === 'REJECTED',
    promptText: asset.promptText || '',
    negativePromptText: asset.negativePromptText || '',
    referenceReady: asset.assetType !== 'SHOT_KEYFRAME'
      || resolvedReferenceGate?.referencesApproved === true,
    referenceMessage: asset.assetType === 'SHOT_KEYFRAME' && resolvedReferenceGate?.referencesApproved !== true
      ? (resolvedReferenceGate.referenceStatusMessage || '请先完成并批准场景和人物参考图')
      : ''
  })
}

function saveImagePrompt(shouldGenerate) {
  if (shouldGenerate && promptDialog.assetType === 'SHOT_KEYFRAME' && !promptDialog.referenceReady) {
    proxy.$modal.msgWarning(promptDialog.referenceMessage || '请先完成并批准场景和人物参考图')
    return
  }
  const promptText = promptDialog.promptText.trim()
  const negativePromptText = promptDialog.negativePromptText.trim()
  const assetId = promptDialog.assetId
  const assetStatus = promptDialog.status
  const assetType = promptDialog.assetType
  if (!promptText) {
    proxy.$modal.msgWarning('请填写正向提示词')
    return
  }
  const submit = () => {
    promptDialog.submitting = true
    return updateAiVideoAssetPrompt(assetId, { promptText, negativePromptText }).then(() => {
      if (!shouldGenerate) {
        proxy.$modal.msgSuccess('提示词已保存，尚未调用图片模型')
        if (promptDialog.assetId === assetId) promptDialog.open = false
        refreshAssetViews()
        return null
      }
      return assetStatus === 'REJECTED'
        ? retryAiVideoAssetImage(assetId)
        : generateAiVideoAssetImage(assetId)
    }).then(response => {
      if (!shouldGenerate || !response) return
      proxy.$modal.msgSuccess(assetStatus === 'REJECTED'
        ? '已提交图片重新生成任务'
        : `已创建图片任务 #${response.taskId}`)
      if (promptDialog.assetId === assetId) promptDialog.open = false
      refreshAssetViews()
    }).finally(() => {
      if (promptDialog.assetId === assetId) promptDialog.submitting = false
    })
  }
  if (shouldGenerate) {
    const target = assetType === 'CHARACTER_REFERENCE' ? '人物正面、侧面、背面三视图' : `“${promptDialog.assetName}”`
    proxy.$modal.confirm(`确认使用当前提示词生成${target}吗？这将产生一次 Qwen Image 模型调用。`).then(submit).catch(() => {})
  } else {
    submit().catch(() => {})
  }
}

function statusLabel(status) {
  return { DRAFT: '草稿', ACTIVE: '制作中', PAUSED: '已暂停', ARCHIVED: '已归档' }[status] || status
}

function statusTagType(status) {
  return { DRAFT: 'info', ACTIVE: 'success', PAUSED: 'warning', ARCHIVED: 'info' }[status] || 'info'
}

function pipelineLabel(status) {
  return { NOT_STARTED: '尚未解析', RUNNING: '处理中', SUCCEEDED: '已完成', FAILED: '处理失败' }[status] || status
}

function assetTypeLabel(type) {
  return { CHARACTER_REFERENCE: '角色三视图', SCENE_REFERENCE: '场景', SHOT_KEYFRAME: '关键帧', VIDEO_CLIP: '视频' }[type] || type
}

function assetStatusLabel(asset) {
  if (asset.status === 'APPROVED') return asset.assetType === 'SHOT_KEYFRAME' ? '已同意' : '已完成'
  if (asset.assetType === 'VIDEO_CLIP') {
    return { DRAFT: '待确认视频提示词', GENERATING: '视频生成中', GENERATED: '视频已生成', REJECTED: '视频生成失败' }[asset.status] || asset.status
  }
  return { DRAFT: '待确认提示词', GENERATING: '生成中', GENERATED: '待我同意', REJECTED: '失败' }[asset.status] || asset.status
}

function assetStatusTagType(status) {
  return { DRAFT: 'info', GENERATING: 'warning', GENERATED: 'warning', APPROVED: 'success', REJECTED: 'danger' }[status] || 'info'
}

function assetPreviewPlaceholder(asset) {
  if (asset.status === 'DRAFT') {
    if (asset.assetType === 'CHARACTER_REFERENCE') return '待确认角色三视图提示词'
    if (asset.assetType === 'VIDEO_CLIP') return '待确认视频提示词'
    return '待确认提示词'
  }
  if (asset.status === 'GENERATING') return asset.assetType === 'VIDEO_CLIP' ? '正在等待 Wanx 视频结果…' : '正在等待图片模型结果…'
  if (asset.status === 'REJECTED') return asset.assetType === 'VIDEO_CLIP' ? '视频生成失败，可修改提示词重试' : '图片生成失败'
  return '暂无预览'
}

function assetDimensionLabel(asset) {
  if (asset.assetType === 'VIDEO_CLIP' && asset.durationMs) return `${(asset.durationMs / 1000).toFixed(asset.durationMs % 1000 ? 1 : 0)} 秒`
  if (asset.width && asset.height) return `${asset.width} × ${asset.height}`
  if (asset.status === 'DRAFT') return '尚未生成'
  if (asset.status === 'REJECTED') return '生成失败'
  return '等待图片尺寸'
}

function assetTaskMessage(asset, sourceDrawer = assetDrawer) {
  const task = sourceDrawer.taskByAssetId[asset.assetId]
  if (asset.assetType === 'VIDEO_CLIP') {
    if (asset.status === 'DRAFT') return '视频提示词草稿已准备好；保存不会调用 Wanx，生成前仍需你二次确认'
    if (sourceDrawer.taskLoadError && (asset.status === 'GENERATING' || asset.status === 'REJECTED')) return sourceDrawer.taskLoadError
    if (task?.errorMessage) return task.errorMessage
    if (asset.status === 'REJECTED') {
      try {
        return JSON.parse(asset.metadataJson || '{}').generationError || 'Wanx 视频生成失败，可修改提示词后手动重试'
      } catch (error) {
        return 'Wanx 视频生成失败，可修改提示词后手动重试'
      }
    }
    if (asset.status === 'GENERATED') return '视频片段已生成并保存到资产库'
    if (task?.status === 'RETRYING') return '上次 Wanx 调用未正常结束，任务正在等待恢复'
    if (task?.status === 'NEEDS_REVIEW') return task.errorMessage || 'Wanx 提交结果待人工核对，请勿重复生成'
    if (task?.status === 'WAITING_CALLBACK') return 'Wanx 已接收任务，正在生成视频并等待结果'
    if (task?.status === 'RUNNING') return 'Wanx 正在生成视频，请耐心等待'
    if (task?.status === 'QUEUED') return '视频任务已排队，等待调用 Wanx'
    return '尚未取得关联视频任务状态，请稍后自动刷新'
  }
  if (asset.status === 'DRAFT') return asset.assetType === 'CHARACTER_REFERENCE'
    ? '正面、侧面、背面三视图提示词已准备好，确认后才会调用 Qwen Image'
    : '提示词已准备好，确认后才会调用 Qwen Image'
  if (sourceDrawer.taskLoadError) return sourceDrawer.taskLoadError
  if (task?.errorMessage) return task.errorMessage
  if (asset.status === 'REJECTED') {
    try {
      return JSON.parse(asset.metadataJson || '{}').generationError || '图片生成失败'
    } catch (error) {
      return '图片生成失败'
    }
  }
  if (asset.status === 'GENERATED') return '图片已生成，请检查画面并确认是否同意用于视频生成'
  if (task?.status === 'RETRYING') return '上次调用未正常结束，即将转为失败，请在提示词窗口手动重试'
  if (task?.status === 'RUNNING') return 'Qwen Image 正在生成图片，请耐心等待'
  if (task?.status === 'QUEUED') return '任务已排队，等待调用 Qwen Image'
  return '尚未取得关联任务状态，请稍后自动刷新'
}

function scheduleAssetPolling() {
  stopAssetPolling()
  if (assetDrawer.open && assetDrawer.assets.some(item => item.status === 'GENERATING'
    && assetDrawer.taskByAssetId[item.assetId]?.status !== 'NEEDS_REVIEW')) {
    assetPollTimer = setTimeout(() => loadAssets(), 5000)
  }
}

function scheduleChapterVideoPolling() {
  stopChapterVideoPolling()
  if (chapterVideoDrawer.open && chapterVideoDrawer.allAssets.some(asset => {
    const task = chapterVideoDrawer.taskByAssetId[asset.assetId]
    return isAssetBusy(asset, task) && task?.status !== 'NEEDS_REVIEW'
  })) {
    chapterVideoPollTimer = setTimeout(() => loadChapterVideoAssets(), 5000)
  }
}

function stopChapterVideoPolling() {
  if (chapterVideoPollTimer) {
    clearTimeout(chapterVideoPollTimer)
    chapterVideoPollTimer = null
  }
}

function handleChapterVideoDrawerClosed() {
  chapterVideoLoadRequestId += 1
  chapterVideoDrawer.loading = false
  stopChapterVideoPolling()
}

function stopAssetPolling() {
  if (assetPollTimer) {
    clearTimeout(assetPollTimer)
    assetPollTimer = null
  }
}

function handleAssetDrawerClosed() {
  assetLoadRequestId += 1
  assetDrawer.loading = false
  stopAssetPolling()
}

getProjectList()
</script>

<style scoped>
.studio-page { min-height: calc(100vh - 84px); padding: 32px; color: #edf1f7; background: #101318; }
.studio-header { display: flex; justify-content: space-between; gap: 24px; align-items: flex-end; max-width: 1440px; margin: 0 auto 28px; }
.eyebrow { margin: 0 0 8px; color: #f39a4a; font-size: 12px; font-weight: 700; letter-spacing: .14em; }
h1, h2, h3, p { margin-top: 0; } h1 { margin-bottom: 8px; font-size: 32px; } .subtitle, .style-line, .project-meta, .chapter-content p, .drawer-header p { color: #9aa5b5; }
.toolbar { display: flex; gap: 12px; max-width: 1440px; margin: 0 auto 24px; } .toolbar .el-input { width: 320px; } .toolbar .el-select { width: 140px; }
.project-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(252px, 1fr)); gap: 18px; max-width: 1440px; margin: 0 auto; }
.project-card, .create-card { min-height: 330px; padding: 16px; border: 1px solid #2a3340; border-radius: 16px; text-align: left; background: #181d25; transition: transform .2s, border-color .2s; }
.project-card:hover, .create-card:hover { transform: translateY(-3px); border-color: #f39a4a; } .create-card { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px; color: #edf1f7; cursor: pointer; }
.create-card .el-icon { color: #f39a4a; font-size: 36px; } .create-card span { color: #9aa5b5; font-size: 13px; }
.card-topline, .card-actions, .project-meta { display: flex; align-items: center; justify-content: space-between; gap: 8px; } .ratio { color: #9aa5b5; font-family: monospace; font-size: 12px; }
.poster { position: relative; display: flex; align-items: center; justify-content: center; height: 122px; margin: 14px 0 18px; overflow: hidden; border-radius: 10px; background: linear-gradient(135deg, #343d4c, #17202c); font-size: 54px; font-weight: 700; }
.poster-glow { position: absolute; width: 160px; height: 160px; border-radius: 50%; background: #f39a4a; opacity: .22; filter: blur(25px); transform: translate(58px, 46px); } .poster span { z-index: 1; }
.project-card h2 { overflow: hidden; margin-bottom: 7px; font-size: 18px; text-overflow: ellipsis; white-space: nowrap; } .style-line { min-height: 40px; margin-bottom: 15px; font-size: 13px; line-height: 20px; }
.project-meta { padding-top: 12px; border-top: 1px solid #2a3340; font-size: 11px; } .card-actions { margin-top: 16px; } .card-actions .el-button:first-child { flex: 1; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; } .form-grid .el-form-item { min-width: 0; }
.chapter-workspace { min-height: 100%; padding: 30px; color: #1d2735; } .drawer-header { display: flex; justify-content: space-between; gap: 20px; align-items: flex-start; padding-bottom: 24px; border-bottom: 1px solid #e9edf3; } .drawer-header h2 { margin-bottom: 8px; } .drawer-actions { display: flex; gap: 8px; white-space: nowrap; }
.chapter-list { margin-top: 18px; } .chapter-item { display: flex; align-items: center; gap: 14px; padding: 16px 0; border-bottom: 1px solid #edf0f4; } .chapter-number { min-width: 38px; color: #f39a4a; font-family: monospace; font-size: 17px; font-weight: 700; } .chapter-content { flex: 1; min-width: 130px; } .chapter-content p { margin-bottom: 0; font-size: 12px; } .chapter-title-button { max-width: 100%; overflow: hidden; margin: 0 0 5px; padding: 0; border: 0; color: #1d2735; background: transparent; font: inherit; font-size: 15px; font-weight: 700; text-align: left; text-overflow: ellipsis; white-space: nowrap; cursor: pointer; } .chapter-title-button:hover, .chapter-title-button:focus-visible { color: #d97824; text-decoration: underline; } .chapter-item-actions { display: flex; align-items: center; justify-content: flex-end; gap: 4px; } .chapter-item-actions :deep(.el-button + .el-button) { margin-left: 0; }
.story-bible section { margin-bottom: 28px; } .story-bible h3 { margin-bottom: 10px; color: #1d2735; font-size: 16px; } .bible-summary { padding: 18px; border-radius: 12px; background: #fff7ee; } .bible-summary p { margin: 0; color: #555f6e; line-height: 1.8; } .section-heading { display: flex; justify-content: space-between; align-items: center; } .section-heading span { color: #8491a3; font-size: 12px; } .bible-characters { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; } .bible-card { padding: 14px; border: 1px solid #e8edf4; border-radius: 10px; } .bible-card p { min-height: 40px; margin: 8px 0; color: #657181; font-size: 13px; line-height: 1.55; } .bible-card small { color: #f39a4a; } .bible-scene { margin-bottom: 10px; padding: 16px; border-left: 3px solid #f39a4a; border-radius: 0 10px 10px 0; background: #f7f9fb; } .bible-scene p { margin: 7px 0; color: #657181; font-size: 13px; } .scene-no { display: inline-block; width: 34px; color: #f39a4a; font-family: monospace; } .scene-meta { color: #8491a3; font-size: 12px; }
.asset-filter-context { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; color: #7d8999; font-size: 12px; } .asset-toolbar { display: flex; gap: 10px; margin-bottom: 20px; } .asset-toolbar .el-select { width: 170px; } .asset-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; } .asset-card { overflow: hidden; border: 1px solid #e8edf4; border-radius: 12px; background: #fff; } .asset-preview { display: flex; align-items: center; justify-content: center; height: 210px; color: #8491a3; background: #f3f6fa; font-size: 13px; } .asset-preview :deep(.el-image), .asset-preview video { width: 100%; height: 100%; object-fit: cover; } .asset-body { padding: 12px; } .asset-body .el-tag + .el-tag { margin-left: 6px; } .asset-body h3 { overflow: hidden; margin: 10px 0 5px; color: #1d2735; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; } .asset-body p { margin: 0; color: #8491a3; font-size: 12px; } .character-view-spec { margin-top: 6px !important; color: #b86b23 !important; font-weight: 600; } .asset-prompt-preview { margin-top: 10px; padding: 9px 10px; border-radius: 8px; background: #f6f8fb; } .asset-prompt-preview strong { display: block; margin-bottom: 3px; color: #566173; font-size: 11px; } .asset-prompt-preview p { display: -webkit-box; overflow: hidden; margin-bottom: 7px; line-height: 17px; word-break: break-word; -webkit-box-orient: vertical; -webkit-line-clamp: 2; } .asset-prompt-preview p:last-child { margin-bottom: 0; } .prompt-form { margin-top: 18px; } .prompt-form :deep(textarea) { line-height: 1.6; } .asset-task-status { min-height: 32px; margin-top: 7px !important; color: #d75a4a !important; line-height: 16px; } .video-action { width: 100%; margin-top: 12px; } .asset-version-actions { display: flex; gap: 8px; margin-top: 10px; } .asset-version-actions .el-button { flex: 1; margin-left: 0; }
.regeneration-reference-alert { margin-top: 12px; }
.regeneration-reference-form .el-select { width: 100%; }
.reference-option-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; width: 100%; }
.reference-option-row span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.reference-option-row small { flex-shrink: 0; color: #9099a8; }
.regeneration-reference-help { display: block; margin-top: 7px; color: #8793a3; line-height: 1.5; }
.chapter-material-stats { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
.chapter-material-stats button { display: grid; grid-template-columns: auto 1fr; grid-template-rows: auto auto; column-gap: 10px; padding: 12px 14px; border: 1px solid #e2e8f0; border-radius: 12px; color: #596579; background: #f8fafc; text-align: left; cursor: pointer; transition: border-color .18s, box-shadow .18s, background .18s; }
.chapter-material-stats button:hover, .chapter-material-stats button.active { border-color: #e2944e; background: #fffaf5; box-shadow: 0 6px 18px rgba(130, 82, 40, .08); }
.chapter-material-stats strong { grid-row: 1 / span 2; align-self: center; color: #d97824; font-size: 24px; line-height: 1; }
.chapter-material-stats span { color: #273448; font-size: 13px; font-weight: 700; }
.chapter-material-stats small { color: #8995a6; font-size: 11px; }
.chapter-material-tabs :deep(.el-tabs__header) { position: sticky; z-index: 2; top: 0; margin-bottom: 18px; padding-top: 4px; background: #fff; }
.chapter-material-tabs :deep(.el-tabs__item) { font-weight: 650; }
.chapter-reference-scenes { display: grid; gap: 18px; }
.chapter-material-card-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; padding: 16px 18px 20px; }
.chapter-material-card { min-width: 0; overflow: hidden; border: 1px solid #e3e9f1; border-radius: 12px; background: #fff; }
.chapter-material-preview { display: flex; align-items: center; justify-content: center; height: 196px; color: #8793a3; background: linear-gradient(145deg, #edf2f7, #f7f9fc); font-size: 12px; }
.chapter-material-preview :deep(.el-image) { width: 100%; height: 100%; }
.chapter-material-card-body { padding: 13px; }
.chapter-material-card-body h4 { overflow: hidden; margin: 10px 0 5px; color: #263244; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.chapter-material-card-body > p { margin: 0; color: #8793a3; font-size: 12px; }
.chapter-material-prompt { min-height: 78px; margin-bottom: 8px; }
.chapter-keyframe-shot { background: #fbfcfe; }
.shot-reference-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin: 4px 0 10px; color: #4a5669; }
.shot-reference-heading > div:first-child { display: grid; gap: 4px; }
.shot-reference-heading > div:first-child > span { color: #8a96a7; font-size: 11px; }
.shot-reference-tags { display: flex; flex-shrink: 0; flex-wrap: wrap; justify-content: flex-end; gap: 6px; }
.shot-reference-warning { margin: -2px 0 10px; padding: 8px 10px; border-radius: 7px; color: #a85c22; background: #fff5e9; font-size: 11px; line-height: 1.5; }
.shot-reference-strip { display: grid; grid-template-columns: repeat(auto-fit, minmax(142px, 1fr)); gap: 10px; margin-bottom: 14px; }
.shot-reference-card { min-width: 0; padding: 9px; overflow: hidden; border: 1px solid #dfe6ee; border-radius: 10px; color: #374357; background: #fff; text-align: left; cursor: pointer; }
button.shot-reference-card:hover:not(:disabled) { border-color: #dfa266; box-shadow: 0 5px 15px rgba(76, 52, 31, .08); }
button.shot-reference-card:disabled { cursor: default; }
.shot-reference-card.missing { border-style: dashed; background: #fafbfc; }
.shot-reference-kind { display: block; margin-bottom: 6px; color: #b36c2d; font-size: 10px; font-weight: 800; letter-spacing: .06em; }
.shot-reference-preview { display: flex; align-items: center; justify-content: center; height: 94px; margin-bottom: 8px; overflow: hidden; border-radius: 7px; color: #929dad; background: #eef2f6; font-size: 10px; text-align: center; }
.shot-reference-preview :deep(.el-image) { width: 100%; height: 100%; }
.shot-reference-preview.character { background: #f4f2ef; }
.shot-reference-card > strong { display: block; overflow: hidden; margin-bottom: 7px; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.empty-reference { display: flex; min-height: 142px; flex-direction: column; justify-content: center; cursor: default; }
.empty-reference > strong { overflow: visible; white-space: normal; line-height: 1.45; }
.empty-reference small { color: #929dad; font-size: 10px; }
.keyframe-version-grid { padding: 0; }
.keyframe-preview { height: 180px; }
.keyframe-action-row { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 8px; margin-top: 12px; }
.keyframe-action-row .el-button, .keyframe-generate-button-wrap, .keyframe-generate-button-wrap .el-button { width: 100%; margin-left: 0; }
.chapter-video-toolbar { display: grid; gap: 12px; margin-bottom: 20px; } .chapter-video-toolbar-actions { display: flex; justify-content: flex-end; gap: 8px; } .chapter-video-toolbar-actions :deep(.el-button + .el-button) { margin-left: 0; } .chapter-video-scenes { display: grid; gap: 18px; } .chapter-video-scene { overflow: hidden; border: 1px solid #e5eaf1; border-radius: 14px; background: #f8fafc; } .chapter-video-scene-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 16px 18px; border-bottom: 1px solid #e5eaf1; background: #f0f4f8; } .chapter-video-scene-heading div { display: flex; align-items: baseline; gap: 10px; } .chapter-video-scene-heading span { color: #d97824; font-size: 12px; font-weight: 700; } .chapter-video-scene-heading h3 { margin: 0; color: #263244; font-size: 17px; } .chapter-video-scene-heading small { color: #8793a3; } .chapter-video-shot { padding: 16px 18px 20px; } .chapter-video-shot + .chapter-video-shot { border-top: 1px solid #e5eaf1; } .chapter-video-shot-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; color: #354154; } .chapter-video-shot-heading span { color: #8793a3; font-size: 12px; } .chapter-video-version-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; } .chapter-video-version-card { min-width: 0; overflow: hidden; border: 1px solid #e4eaf1; border-radius: 12px; background: #fff; } .chapter-video-preview { display: flex; align-items: center; justify-content: center; height: 176px; color: #8793a3; background: #edf2f7; font-size: 12px; } .chapter-video-preview video { width: 100%; height: 100%; object-fit: cover; } .chapter-video-version-body { padding: 12px; } .chapter-video-version-tags { display: flex; flex-wrap: wrap; gap: 5px; } .chapter-video-version-body h4 { overflow: hidden; margin: 10px 0 5px; color: #263244; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; } .chapter-video-version-body > p { margin: 0; color: #8793a3; font-size: 12px; } .chapter-video-prompt-preview { margin-bottom: 8px; }
.video-shot-summary { margin-top: 18px; padding: 16px; border: 1px solid #e5eaf1; border-radius: 12px; background: #f7f9fc; }
.video-summary-heading { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; margin-bottom: 12px; color: #263244; }
.video-summary-heading span { color: #8793a3; font-size: 12px; }
.video-summary-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.video-summary-grid > div { min-width: 0; padding: 10px 12px; border-radius: 8px; background: #fff; }
.video-summary-grid small { display: block; margin-bottom: 4px; color: #a1672f; font-weight: 700; }
.video-summary-grid p { margin: 0; color: #566173; font-size: 13px; line-height: 1.55; white-space: pre-wrap; word-break: break-word; }
.video-prompt-form { margin-top: 20px; }
.duration-editor { display: flex; align-items: center; gap: 9px; }
.duration-editor > span { color: #566173; }
.duration-editor > small { color: #8793a3; }
@media (max-width: 700px) { .studio-page { padding: 20px; } .studio-header { align-items: flex-start; flex-direction: column; } .toolbar .el-input { width: 100%; } .toolbar { flex-wrap: wrap; } .form-grid, .video-summary-grid, .chapter-video-version-grid { grid-template-columns: 1fr; gap: 0; } .video-summary-grid, .chapter-video-version-grid { gap: 8px; } .video-summary-heading, .duration-editor, .chapter-video-shot-heading { align-items: flex-start; flex-direction: column; } .chapter-item { align-items: flex-start; flex-wrap: wrap; } .chapter-item-actions { width: 100%; justify-content: flex-start; } .chapter-video-scene-heading { align-items: flex-start; } .chapter-video-scene-heading div { align-items: flex-start; flex-direction: column; gap: 3px; } .chapter-video-toolbar-actions { justify-content: stretch; } .chapter-video-toolbar-actions .el-button { flex: 1; } }
@media (max-width: 700px) { .chapter-material-stats, .chapter-material-card-grid, .keyframe-action-row { grid-template-columns: 1fr; } .chapter-material-card-grid { padding: 12px; } .shot-reference-heading { align-items: flex-start; flex-direction: column; } .shot-reference-tags { justify-content: flex-start; } .shot-reference-strip { grid-template-columns: repeat(2, minmax(0, 1fr)); } .chapter-video-toolbar-actions { flex-wrap: wrap; } }
</style>
