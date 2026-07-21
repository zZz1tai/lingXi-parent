<template>
  <div class="studio-page">
    <header class="studio-header">
      <div class="studio-heading">
        <div class="studio-mark" aria-hidden="true"><span /></div>
        <div>
          <p class="eyebrow">AI video studio</p>
          <h1>小说视频工作台</h1>
          <p class="subtitle">管理项目、章节与视觉资产，让每一步制作都有清晰去向。</p>
        </div>
      </div>
      <div class="header-actions">
        <el-button v-hasPermi="['aivideo:project:edit']" size="large" :icon="Connection" @click="router.push('/aiVedio/model-config/index')">
          模型配置
        </el-button>
        <el-button type="primary" size="large" :icon="Plus" @click="handleAddProject">
          新建项目
        </el-button>
      </div>
    </header>

    <div class="studio-shell">
      <project-rail
        :projects="projectList"
        :selected-id="selectedProject?.projectId"
        :total="total"
        :loading="loading"
        v-model:search="queryParams.projectName"
        v-model:status="queryParams.status"
        @select="selectProject"
        @search="handleQuery"
        @reset="resetQuery"
      />

      <main class="project-workspace">
        <template v-if="selectedProject">
          <section class="project-hero">
            <div class="hero-ambient" aria-hidden="true" />
            <div class="hero-topline">
              <span class="project-status"><i :class="`status-${selectedProject.status?.toLowerCase() || 'draft'}`" />{{ statusLabel(selectedProject.status) }}</span>
              <div class="hero-tools">
                <el-dropdown @command="command => handleProjectCommand(command, selectedProject)">
                  <el-button text :icon="MoreFilled" aria-label="项目更多操作" />
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="edit">编辑项目</el-dropdown-item>
                      <el-dropdown-item command="delete" divided>删除项目</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </div>

            <div class="hero-content">
              <div class="hero-poster">
                <img v-if="selectedProject.coverUrl" :src="resolveProjectCoverUrl(selectedProject.coverUrl)" :alt="`${selectedProject.projectName}项目封面`" />
                <span v-else aria-hidden="true">{{ selectedProject.projectName.slice(0, 1) }}</span>
              </div>
              <div class="hero-copy">
                <p>当前项目</p>
                <h2>{{ selectedProject.projectName }}</h2>
                <span>{{ selectedProject.visualStyle || '尚未设定视觉风格，可在项目设置中补充。' }}</span>
                <div class="hero-meta">
                  <span>{{ selectedProject.defaultLanguage || 'zh-CN' }}</span>
                  <span>更新于 {{ parseTime(selectedProject.updateTime, '{y}-{m}-{d}') }}</span>
                </div>
              </div>
            </div>

            <div class="hero-actions">
              <el-button type="primary" size="large" :icon="VideoPlay" @click="openChapterDrawer(selectedProject)">进入章节工作台</el-button>
              <el-button size="large" @click="handleProjectCommand('edit', selectedProject)">项目设置</el-button>
            </div>
          </section>

          <section class="workflow-panel">
            <div class="section-title">
              <div><p class="eyebrow">Production flow</p><h3>从文本到视频</h3></div>
              <span>按顺序完成，素材引用关系会自动保留</span>
            </div>
            <ol class="workflow-steps">
              <li class="active"><span>01</span><div><strong>导入章节</strong><small>粘贴小说原文</small></div></li>
              <li><span>02</span><div><strong>解析故事</strong><small>人物、场景与分镜</small></div></li>
              <li><span>03</span><div><strong>确认素材</strong><small>参考图与关键帧</small></div></li>
              <li><span>04</span><div><strong>生成视频</strong><small>镜头版本与成片</small></div></li>
            </ol>
          </section>

          <section class="workspace-cards">
            <article class="workspace-card workspace-card-primary">
              <span class="workspace-card-index">NEXT</span>
              <div><h3>继续本项目的章节制作</h3><p>导入新章节、查看解析进度，或进入已有章节的素材工作区。</p></div>
              <el-button type="primary" @click="openChapterDrawer(selectedProject)">打开章节</el-button>
            </article>
            <article class="workspace-card">
              <span class="workspace-card-index">ASSETS</span>
              <div><h3>人物与场景资产</h3><p>集中管理参考图、提示词以及已批准的生成版本。</p></div>
              <el-button text type="primary" @click="openChapterDrawer(selectedProject)">从章节进入</el-button>
            </article>
            <article class="workspace-card">
              <span class="workspace-card-index">OUTPUT</span>
              <div><h3>关键帧与视频</h3><p>逐镜头核对引用关系，再提交视频生成任务。</p></div>
              <el-button text type="primary" @click="openChapterDrawer(selectedProject)">查看制作流程</el-button>
            </article>
          </section>
        </template>

        <section v-else-if="!loading" class="workspace-empty">
          <div class="empty-symbol"><el-icon><Plus /></el-icon></div>
          <p class="eyebrow">Start a project</p>
          <h2>{{ total ? '没有符合条件的项目' : '创建第一个视频项目' }}</h2>
          <p>{{ total ? '尝试清除筛选条件，继续已有项目。' : '上传封面、设置视觉风格，然后导入小说章节开始制作。' }}</p>
          <div>
            <el-button v-if="total" size="large" @click="resetQuery">清除筛选</el-button>
            <el-button type="primary" size="large" :icon="Plus" @click="handleAddProject">新建项目</el-button>
          </div>
        </section>
      </main>
    </div>

    <el-dialog v-model="projectDialog.open" :title="projectDialog.title" width="560px" append-to-body>
      <el-form ref="projectFormRef" :model="projectForm" :rules="projectRules" label-position="top">
        <el-form-item label="项目名称" prop="projectName">
          <el-input v-model="projectForm.projectName" maxlength="128" show-word-limit placeholder="例如：雨夜病历" />
        </el-form-item>
        <el-form-item label="项目封面">
          <image-upload v-model="projectForm.coverUrl" class="project-cover-upload" :limit="1" :file-size="10" :file-type="['png', 'jpg', 'jpeg', 'webp']" />
        </el-form-item>
        <el-form-item label="视觉风格">
          <el-input v-model="projectForm.visualStyle" placeholder="例如：电影写实、冷蓝色雨夜光影" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="projectDialog.submitting" @click="projectDialog.open = false">取消</el-button>
        <el-button type="primary" :loading="projectDialog.submitting" @click="submitProject">保存项目</el-button>
      </template>
    </el-dialog>

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
              @click="resumeVideoSubmission(asset)"
            >填写任务ID并恢复轮询</el-button>
            <el-button
              v-if="asset.assetType === 'VIDEO_CLIP' && assetDrawer.taskByAssetId[asset.assetId]?.status === 'NEEDS_REVIEW' && !assetDrawer.taskByAssetId[asset.assetId]?.providerTaskId"
              class="video-action"
              size="small"
              type="danger"
              plain
              @click="confirmVideoNotSubmitted(asset)"
            >确认未提交并解锁</el-button>
            <div class="asset-version-actions">
              <el-button
                size="small"
                :loading="regeneratingAssetId === asset.assetId"
              :disabled="isAssetBusy(asset, assetDrawer.taskByAssetId[asset.assetId])"
              @click="createRegenerationDraft(asset)"
              >{{ asset.assetType === 'SHOT_KEYFRAME' ? '编辑参考绑定 / 新版本' : (asset.assetType === 'VIDEO_CLIP' ? '更换关键帧 / 新版本' : '重新生成') }}</el-button>
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
        :title="regenerationDialog.editableInPlace ? '当前是草稿/失败版本，保存后直接更新绑定' : '当前版本已有生成结果，保存时会创建关键帧新版本草稿'"
        description="这里只修改人物与场景参考图的具体版本，不会调用 Qwen Image。已生成版本保持不可变。"
        type="info"
        :closable="false"
        show-icon
      />
      <div class="binding-dialog-status">
        <el-tag :type="regenerationDialog.bindingMode === 'MANUAL' ? 'warning' : 'success'" effect="plain">
          当前：{{ bindingModeLabel(regenerationDialog.bindingMode) }}绑定
        </el-tag>
      </div>
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
        <el-form-item :label="`人物三视图（当前分镜固定 ${regenerationDialog.requiredCharacterCount} 人）`">
          <el-select
            v-model="regenerationDialog.characterReferenceAssetIds"
            multiple
            filterable
            collapse-tags
            :multiple-limit="4"
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
          <small class="regeneration-reference-help">只能切换当前分镜中同一人物、同一场景的其他已批准版本，不能在这里增删人物。</small>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="regenerationDialog.submitting" @click="regenerationDialog.open = false">取消</el-button>
        <el-button
          :loading="regenerationDialog.submitting"
          :disabled="regenerationDialog.loading || !!regenerationDialog.loadError"
          @click="resetKeyframeBindingToAuto"
        >恢复自动匹配</el-button>
        <el-button
          type="primary"
          :loading="regenerationDialog.submitting"
          :disabled="regenerationDialog.loading || !!regenerationDialog.loadError || !regenerationDialog.sceneReferenceAssetId"
          @click="submitKeyframeRegenerationDraft"
        >{{ regenerationDialog.editableInPlace ? '保存人工绑定' : '创建新版本并绑定' }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="videoBindingDialog.open"
      :title="videoBindingDialog.assetName + ' · 来源关键帧版本'"
      width="720px"
      append-to-body
      :close-on-click-modal="!videoBindingDialog.submitting"
      :close-on-press-escape="!videoBindingDialog.submitting"
      :show-close="!videoBindingDialog.submitting"
    >
      <el-alert
        :title="videoBindingDialog.editableInPlace ? '当前是草稿/失败视频，保存后直接切换关键帧' : '当前视频已有结果，保存时会创建视频新版本草稿'"
        description="视频生成任务会携带所选关键帧、人物三视图和场景参考图；具体供应商由适配器转换。此操作本身不会提交模型。"
        type="info"
        :closable="false"
        show-icon
      />
      <el-alert v-if="videoBindingDialog.loadError" class="regeneration-reference-alert" :title="videoBindingDialog.loadError" type="error" :closable="false" show-icon />
      <div class="binding-dialog-status">
        <el-tag :type="videoBindingDialog.bindingMode === 'MANUAL' ? 'warning' : 'success'" effect="plain">
          当前：{{ bindingModeLabel(videoBindingDialog.bindingMode) }}绑定
        </el-tag>
      </div>
      <el-form v-loading="videoBindingDialog.loading" label-position="top" class="prompt-form regeneration-reference-form">
        <el-form-item label="来源关键帧（同一分镜的已批准版本）">
          <el-select
            v-model="videoBindingDialog.keyframeAssetId"
            filterable
            placeholder="请选择关键帧版本"
            :disabled="videoBindingDialog.loading || videoBindingDialog.submitting"
            @change="syncVideoBindingSelection"
          >
            <el-option
              v-for="asset in videoBindingDialog.availableKeyframes"
              :key="asset.assetId"
              :label="referenceOptionLabel(asset)"
              :value="asset.assetId"
            />
          </el-select>
        </el-form-item>
        <div v-if="videoBindingDialog.sourceKeyframe" class="video-binding-inheritance">
          <strong>将绑定：{{ referenceOptionLabel(videoBindingDialog.sourceKeyframe) }}</strong>
          <span>{{ bindingReferenceDetailSummary(videoBindingDialog.inheritedReferences) }}</span>
        </div>
      </el-form>
      <template #footer>
        <el-button :disabled="videoBindingDialog.submitting" @click="videoBindingDialog.open = false">取消</el-button>
        <el-button
          :loading="videoBindingDialog.submitting"
          :disabled="videoBindingDialog.loading || !!videoBindingDialog.loadError"
          @click="resetVideoBindingToAuto"
        >恢复自动匹配</el-button>
        <el-button
          type="primary"
          :loading="videoBindingDialog.submitting"
          :disabled="videoBindingDialog.loading || !!videoBindingDialog.loadError || !videoBindingDialog.keyframeAssetId"
          @click="submitVideoSourceBinding"
        >{{ videoBindingDialog.editableInPlace ? '保存人工绑定' : '创建视频新版本并绑定' }}</el-button>
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
        title="这一步只准备视频提示词，不会自动调用视频生成服务"
        description="提示词由章节分镜、关键帧、人物与场景一致性约束自动提炼。模型限制由当前供应商适配器校验；只有点击“保存并生成视频”且再次确认后，才会提交并产生费用。"
        type="info"
        :closable="false"
        show-icon
      />
      <el-alert
        v-else
        title="这是已确认的视频提示词，只读不可修改"
        :description="videoPromptDialog.status === 'GENERATED' ? '该提示词对应的视频已经生成并保存。' : '该提示词已经进入视频供应商提交流程；为保证审计一致性，提交后不能修改。'"
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
              :max="15"
              :step="1"
              :precision="0"
              :disabled="!videoPromptDialog.editable || videoPromptDialog.submitting"
              controls-position="right"
            />
            <span>秒</span>
            <small>将以 {{ Math.round(videoPromptDialog.durationSeconds * 1000) }} ms 保存，最终时长由供应商适配器校验</small>
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
import { Connection, Delete, MoreFilled, Plus, VideoPlay } from '@element-plus/icons-vue'
import { isExternal } from '@/utils/validate'
import ProjectRail from './components/ProjectRail.vue'
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
  getAiVideoKeyframeReferenceBinding,
  getAiVideoVideoSourceBinding,
  listAiVideoChapter,
  listAiVideoAsset,
  listAiVideoTask,
  listAiVideoProject,
  retryAiVideoAssetImage,
  resetAiVideoKeyframeReferenceBinding,
  resetAiVideoVideoSourceBinding,
  resolveAiVideoAssetSubmission,
  updateAiVideoAssetPrompt,
  updateAiVideoAssetVideoPrompt,
  updateAiVideoKeyframeReferenceBinding,
  updateAiVideoVideoSourceBinding,
  updateAiVideoProject
} from '@/api/aiVedio/project'

const { proxy } = getCurrentInstance()
const router = useRouter()
const loading = ref(false)
const projectList = ref([])
const selectedProjectId = ref(null)
const selectedProject = computed(() => projectList.value.find(project => project.projectId === selectedProjectId.value) || null)
const total = ref(0)
const projectFormRef = ref()
const chapterFormRef = ref()

const queryParams = reactive({ projectName: '', status: '' })
const projectDialog = reactive({ open: false, title: '', submitting: false })
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
  currentCharacterReferences: [],
  requiredCharacterCount: 0,
  editableInPlace: false,
  bindingMode: 'AUTO',
  loadError: '',
  selectionMessage: ''
})
const videoBindingDialog = reactive({
  open: false,
  loading: false,
  submitting: false,
  assetId: null,
  assetName: '',
  status: '',
  editableInPlace: false,
  bindingMode: 'AUTO',
  keyframeAssetId: null,
  sourceKeyframe: null,
  availableKeyframes: [],
  inheritedReferences: null,
  loadError: ''
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
const MAX_CHARACTER_REFERENCE_IMAGES = 4
const busyAssetStatuses = new Set([
  'PENDING', 'QUEUED', 'SUBMITTING', 'SUBMITTED', 'PROCESSING', 'GENERATING',
  'RUNNING', 'POLLING', 'WAITING', 'WAITING_CALLBACK', 'RETRYING', 'NEEDS_REVIEW',
  'VALIDATING', 'QUALITY_CHECK'
])
const CHAPTER_ANALYSIS_PHASES = [
  { key: 'prepare', label: '准备' },
  { key: 'planning', label: '规划' },
  { key: 'scenes', label: '场景生成' },
  { key: 'validation', label: '校验' },
  { key: 'persisting', label: '保存' }
]
const chapterAnalysisStageMeta = {
  QUEUED: { title: '等待执行', phaseIndex: 0 },
  PREPARING: { title: '准备章节', phaseIndex: 0 },
  PLANNING: { title: '规划章节骨架', phaseIndex: 1 },
  PLANNING_REPAIR: { title: '修复章节规划', phaseIndex: 1 },
  SCENE_GENERATING: { title: '逐场景生成', phaseIndex: 2 },
  SCENE_REPAIRING: { title: '局部修复场景', phaseIndex: 2 },
  VALIDATING: { title: '校验整章结构', phaseIndex: 3 },
  REPAIRING: { title: '修复全局契约', phaseIndex: 3 },
  FINALIZING: { title: '整理分析结果', phaseIndex: 3 },
  PERSISTING: { title: '保存分析结果', phaseIndex: 4 }
}
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
let videoBindingLoadRequestId = 0
let videoBindingSelectionRequestId = 0
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
    coverUrl: '',
    visualStyle: ''
  })
}

function getProjectList() {
  loading.value = true
  listAiVideoProject(queryParams).then(response => {
    projectList.value = response.rows || []
    total.value = Number(response.total) || 0
    if (!projectList.value.some(project => project.projectId === selectedProjectId.value)) {
      selectedProjectId.value = projectList.value[0]?.projectId || null
    }
  }).finally(() => { loading.value = false })
}

function selectProject(project) {
  selectedProjectId.value = project?.projectId || null
}

function handleQuery() {
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
      resetProjectForm()
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
    const payload = {
      projectId: projectForm.projectId || undefined,
      projectName: projectForm.projectName,
      coverUrl: projectForm.coverUrl || null,
      visualStyle: projectForm.visualStyle
    }
    projectDialog.submitting = true
    request(payload).then(() => {
      proxy.$modal.msgSuccess('项目已保存')
      projectDialog.open = false
      getProjectList()
    }).finally(() => { projectDialog.submitting = false })
  })
}

function resolveProjectCoverUrl(value) {
  if (!value) return ''
  const cover = String(value).split(',')[0]
  return isExternal(cover) ? cover : import.meta.env.VITE_APP_BASE_API + cover
}

async function openChapterDrawer(project) {
  if (!project?.projectId) return
  chapterDrawer.project = project
  chapterDrawer.open = false
  chapterDrawer.loading = true
  try {
    const response = await listAiVideoChapter(project.projectId)
    const chapters = response.rows || response.data || []
    chapterDrawer.chapters = chapters
    if (!chapters.length) {
      openChapterDialog()
      return
    }
    const firstChapter = [...chapters].sort((left, right) => Number(left.chapterNo || 0) - Number(right.chapterNo || 0))[0]
    router.push({
      name: 'AiVedioChapterWorkspace',
      params: { projectId: project.projectId, chapterId: firstChapter.chapterId }
    })
  } catch (error) {
    proxy.$modal.msgError(error?.response?.data?.msg || error?.message || '章节列表读取失败')
  } finally {
    chapterDrawer.loading = false
  }
}

function loadChapters({ silent = false } = {}) {
  if (!chapterDrawer.project) return
  const projectId = chapterDrawer.project.projectId
  if (!silent) chapterDrawer.loading = true
  Promise.allSettled([
    listAiVideoChapter(projectId),
    listAiVideoTask(projectId)
  ]).then(([chapterResult, taskResult]) => {
    if (chapterResult.status !== 'fulfilled') throw chapterResult.reason
    const tasks = taskResult.status === 'fulfilled' ? (taskResult.value.data || []) : []
    const latestTaskByChapter = new Map()
    tasks.forEach(task => {
      if (task.taskType !== 'STORY_BIBLE' || !task.chapterId || latestTaskByChapter.has(task.chapterId)) return
      latestTaskByChapter.set(task.chapterId, normalizeChapterAnalysisTask(task))
    })
    chapterDrawer.chapters = (chapterResult.value.data || []).map(chapter => ({
      ...chapter,
      analysisTask: latestTaskByChapter.get(chapter.chapterId) || null
    }))
    scheduleChapterPolling()
  }).finally(() => {
    if (!silent) chapterDrawer.loading = false
  })
}

function normalizeChapterAnalysisTask(task) {
  let stage = {}
  if (task.requestJson) {
    try {
      stage = typeof task.requestJson === 'string' ? JSON.parse(task.requestJson) : task.requestJson
    } catch (error) {
      stage = {}
    }
  }
  return {
    ...task,
    progress: Math.max(0, Math.min(100, Number(task.progress) || 0)),
    stageCode: stage.stageCode || (task.status === 'QUEUED' ? 'QUEUED' : ''),
    stageLabel: stage.stageLabel || chapterStageFallback(task.status),
    errorMessage: String(task.errorMessage || '').replace(/^retryable=(true|false)\s*\|\s*/i, '')
  }
}

function chapterAnalysisStageTitle(task) {
  return chapterAnalysisStageMeta[task?.stageCode]?.title || '章节分析'
}

function chapterAnalysisProgress(task) {
  const progress = Number(task?.progress)
  return Number.isFinite(progress) ? Math.max(0, Math.min(100, progress)) : 0
}

function chapterAnalysisPhaseState(task, phaseIndex) {
  const currentPhase = chapterAnalysisStageMeta[task?.stageCode]?.phaseIndex ?? 0
  if (phaseIndex < currentPhase) return 'complete'
  if (phaseIndex === currentPhase) return 'active'
  return 'pending'
}

function chapterStageFallback(status) {
  if (status === 'QUEUED') return '等待分析任务执行'
  if (status === 'SUCCEEDED') return '章节分析已完成'
  if (status === 'FAILED') return '章节分析失败'
  return '章节分析进行中'
}

function openChapterDialog() {
  Object.assign(chapterForm, { chapterNo: chapterDrawer.chapters.length + 1, chapterTitle: '', sourceText: '' })
  chapterDialog.open = true
}

function submitChapter() {
  chapterFormRef.value.validate(valid => {
    if (!valid) return
    const projectId = chapterDrawer.project.projectId
    addAiVideoChapter(projectId, chapterForm).then(() => {
      proxy.$modal.msgSuccess('章节已导入，等待解析')
      chapterDialog.open = false
      return listAiVideoChapter(projectId)
    }).then(response => {
      const chapters = response.rows || response.data || []
      chapterDrawer.chapters = chapters
      const newestChapter = [...chapters].sort((left, right) => Number(right.chapterNo || 0) - Number(left.chapterNo || 0))[0]
      if (newestChapter?.chapterId) {
        router.push({ name: 'AiVedioChapterWorkspace', params: { projectId, chapterId: newestChapter.chapterId } })
      }
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
    chapter.parseStatus = 'RUNNING'
    chapter.analysisTask = {
      taskId: response.taskId,
      status: 'QUEUED',
      progress: 0,
      stageCode: 'QUEUED',
      stageLabel: '任务已提交，等待分析线程执行',
      errorCode: '',
      errorMessage: ''
    }
    scheduleChapterPolling()
    loadChapters({ silent: true })
  })
}

function scheduleChapterPolling() {
  stopChapterPolling()
  if (chapterDrawer.open && chapterDrawer.chapters.some(item => item.parseStatus === 'RUNNING')) {
    chapterPollTimer = setTimeout(() => loadChapters({ silent: true }), 2500)
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
  const projectId = chapter?.projectId || chapterDrawer.project?.projectId
  if (!projectId || !chapter?.chapterId) return
  router.push({ name: 'AiVedioChapterWorkspace', params: { projectId, chapterId: chapter.chapterId } })
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
  const projectId = chapter?.projectId || chapterDrawer.project?.projectId
  if (!projectId || !chapter?.chapterId) return
  preparingChapterId.value = chapter.chapterId
  router.push({
    name: 'AiVedioChapterWorkspace',
    params: { projectId, chapterId: chapter.chapterId },
    query: { prepare: '1' }
  }).finally(() => {
    preparingChapterId.value = null
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
  if (!characterState.valid || characterState.ids.length > MAX_CHARACTER_REFERENCE_IMAGES) {
    return {
      valid: false,
      message: `关键帧的人物参考 metadata 必须是 0 至 ${MAX_CHARACTER_REFERENCE_IMAGES} 个不重复的有效资产 ID，当前禁止生成`,
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
  if (asset.assetType === 'VIDEO_CLIP') {
    openVideoBindingDialog(asset)
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
    characterReferenceAssetIds: referenceAssetIds(metadata.characterReferenceAssetIds)
  }
}

function bindingMode(asset, fieldName) {
  const mode = String(parseJsonObject(asset?.metadataJson)?.[fieldName] || 'AUTO').toUpperCase()
  return mode === 'MANUAL' ? 'MANUAL' : 'AUTO'
}

function bindingModeLabel(mode) {
  return String(mode || '').toUpperCase() === 'MANUAL' ? '人工' : '自动'
}

function loadedBindingAssets() {
  const assets = [
    ...(chapterVideoDrawer.allAssets || []),
    ...(chapterVideoDrawer.keyframeAssets || []),
    ...(assetDrawer.assets || [])
  ]
  return Array.from(assets.reduce((result, asset) => {
    if (asset?.assetId) result.set(String(asset.assetId), asset)
    return result
  }, new Map()).values())
}

function compactAssetVersion(asset, fallbackId) {
  return asset
    ? `${asset.assetName || `资产 #${asset.assetId}`} v${asset.versionNo || 1}`
    : `资产 #${fallbackId}`
}

function keyframeBindingSummary(asset) {
  const selection = currentKeyframeReferenceSelection(asset)
  const loadedAssets = loadedBindingAssets()
  const scene = findReferenceAssetById(loadedAssets, selection.sceneReferenceAssetId)
  const characters = selection.characterReferenceAssetIds.map(assetId =>
    compactAssetVersion(findReferenceAssetById(loadedAssets, assetId), assetId))
  const sceneText = selection.sceneReferenceAssetId
    ? compactAssetVersion(scene, selection.sceneReferenceAssetId)
    : '未绑定'
  return `场景：${sceneText} · 人物：${characters.length ? characters.join('、') : '无'}`
}

function videoBindingSummary(asset) {
  const metadata = parseJsonObject(asset?.metadataJson)
  const keyframeId = asset?.sourceAssetId || metadata.sourceKeyframeAssetId
  const keyframe = findReferenceAssetById(loadedBindingAssets(), keyframeId)
  const fallbackVersion = metadata.sourceKeyframeVersionNo
  return keyframe
    ? `来源关键帧：${compactAssetVersion(keyframe, keyframeId)}`
    : `来源关键帧：#${keyframeId || '未绑定'}${fallbackVersion ? ` v${fallbackVersion}` : ''}`
}

function videoInheritedReferenceSummary(asset) {
  const keyframe = findReferenceAssetById(loadedBindingAssets(), asset?.sourceAssetId)
  return keyframe ? `继承参考：${keyframeBindingSummary(keyframe)}` : '继承参考：等待读取来源关键帧'
}

function bindingReferenceDetailSummary(detail) {
  if (!detail) return '正在读取该关键帧继承的人物与场景版本…'
  const scene = detail.sceneReference
  const characters = Array.isArray(detail.characterReferences) ? detail.characterReferences : []
  return `场景：${scene ? compactAssetVersion(scene, scene.assetId) : '未绑定'} · 人物：${characters.length
    ? characters.map(item => compactAssetVersion(item, item.assetId)).join('、')
    : '无'}`
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
    currentCharacterReferences: [],
    requiredCharacterCount: 0,
    editableInPlace: asset.status === 'DRAFT' || asset.status === 'REJECTED',
    bindingMode: bindingMode(asset, 'referenceBindingMode'),
    loadError: '',
    selectionMessage: ''
  })
  Promise.all([
    getAiVideoKeyframeReferenceBinding(asset.assetId),
    fetchAllAiVideoAssets({ projectId, assetType: 'SCENE_REFERENCE', status: 'APPROVED' }),
    fetchAllAiVideoAssets({ projectId, assetType: 'CHARACTER_REFERENCE', status: 'APPROVED' })
  ]).then(([bindingResponse, sceneAssets, characterAssets]) => {
    if (requestId !== regenerationLoadRequestId || regenerationDialog.assetId !== asset.assetId) return
    const detail = bindingResponse?.data || {}
    const currentScene = detail.sceneReference || null
    const currentCharacters = Array.isArray(detail.characterReferences) ? detail.characterReferences : []
    const sceneIdentity = detail.asset?.sceneId ?? currentScene?.sceneId
    regenerationDialog.sceneAssets = sortReferenceOptions(sceneAssets.filter(item =>
      sceneIdentity !== null && sceneIdentity !== undefined && String(item.sceneId) === String(sceneIdentity)
    ))
    regenerationDialog.characterAssets = sortReferenceOptions(characterAssets.filter(candidate =>
      currentCharacters.some(current => sameCharacterReferenceIdentity(current, candidate))
    ))
    regenerationDialog.sceneReferenceAssetId = currentScene?.assetId || null
    regenerationDialog.characterReferenceAssetIds = currentCharacters.map(item => item.assetId)
    regenerationDialog.currentCharacterReferences = currentCharacters
    regenerationDialog.requiredCharacterCount = currentCharacters.length
    regenerationDialog.editableInPlace = detail.editableInPlace === true
    regenerationDialog.bindingMode = detail.bindingMode || 'AUTO'
    if (!currentScene) regenerationDialog.selectionMessage = '后端关系表中缺少当前场景引用，无法安全切换版本'
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
    || characterReferences.length > MAX_CHARACTER_REFERENCE_IMAGES) {
    proxy.$modal.msgWarning(`人物参考图必须是 0 至 ${MAX_CHARACTER_REFERENCE_IMAGES} 张同项目且已 APPROVED 的人物三视图`)
    return
  }
  if (characterReferences.length !== regenerationDialog.requiredCharacterCount) {
    proxy.$modal.msgWarning(`当前分镜固定需要 ${regenerationDialog.requiredCharacterCount} 个人物参考，只能换版本，不能增删人物`)
    return
  }
  const identitySelectionValid = regenerationDialog.currentCharacterReferences.every(current =>
    characterReferences.filter(selected => sameCharacterReferenceIdentity(current, selected)).length === 1
  )
  if (!identitySelectionValid) {
    proxy.$modal.msgWarning('每个当前人物必须且只能选择一个对应版本，不能重复选择同一人物的多个版本')
    return
  }
  const payload = {
    mode: 'MANUAL',
    sceneReferenceAssetId: sceneReference.assetId,
    characterReferenceAssetIds: characterReferences.map(asset => asset.assetId)
  }
  regenerationDialog.submitting = true
  regeneratingAssetId.value = assetId
  updateAiVideoKeyframeReferenceBinding(assetId, payload).then(response => {
    const draft = response?.data || response?.asset || null
    if (!draft?.assetId) throw new Error('绑定更新响应缺少资产信息')
    keyframeReferenceOverrides.set(String(draft.assetId), payload)
    regenerationDialog.open = false
    openPromptDialog(draft)
    proxy.$modal.msgSuccess(String(draft.assetId) === String(assetId)
      ? '已更新当前关键帧草稿的人工参考绑定；尚未调用任何生成模型'
      : `已创建 ${draft.assetName || regenerationDialog.assetName} v${draft.versionNo || ''} 草稿并绑定所选参考版本；旧版本保持不变`)
    refreshAssetViews()
  }).catch(error => {
    if (error?.message === '绑定更新响应缺少资产信息') proxy.$modal.msgError(error.message)
  }).finally(() => {
    regenerationDialog.submitting = false
    if (regeneratingAssetId.value === assetId) regeneratingAssetId.value = null
  })
}

function sameCharacterReferenceIdentity(left, right) {
  if (left?.characterId !== null && left?.characterId !== undefined
    || right?.characterId !== null && right?.characterId !== undefined) {
    return left?.characterId !== null && left?.characterId !== undefined
      && String(left.characterId) === String(right?.characterId)
  }
  return !!left?.assetCode && left.assetCode === right?.assetCode
}

function resetKeyframeBindingToAuto() {
  const assetId = regenerationDialog.assetId
  if (!assetId) return
  regenerationDialog.submitting = true
  regeneratingAssetId.value = assetId
  resetAiVideoKeyframeReferenceBinding(assetId).then(response => {
    const draft = response?.data || null
    if (!draft?.assetId) throw new Error('自动绑定响应缺少资产信息')
    regenerationDialog.open = false
    proxy.$modal.msgSuccess(String(draft.assetId) === String(assetId)
      ? '已将当前关键帧草稿恢复为自动匹配的最新可用参考版本'
      : `已创建关键帧 v${draft.versionNo || ''} 草稿并恢复自动匹配；旧版本保持不变`)
    refreshAssetViews()
  }).catch(error => {
    if (error?.message === '自动绑定响应缺少资产信息') proxy.$modal.msgError(error.message)
  }).finally(() => {
    regenerationDialog.submitting = false
    if (regeneratingAssetId.value === assetId) regeneratingAssetId.value = null
  })
}

function openVideoBindingDialog(asset) {
  const requestId = ++videoBindingLoadRequestId
  Object.assign(videoBindingDialog, {
    open: true,
    loading: true,
    submitting: false,
    assetId: asset.assetId,
    assetName: asset.assetName || '视频片段',
    status: asset.status,
    editableInPlace: asset.status === 'DRAFT' || asset.status === 'REJECTED',
    bindingMode: bindingMode(asset, 'sourceBindingMode'),
    keyframeAssetId: null,
    sourceKeyframe: null,
    availableKeyframes: [],
    inheritedReferences: null,
    loadError: ''
  })
  getAiVideoVideoSourceBinding(asset.assetId).then(response => {
    if (requestId !== videoBindingLoadRequestId || videoBindingDialog.assetId !== asset.assetId) return
    const detail = response?.data || {}
    videoBindingDialog.editableInPlace = detail.editableInPlace === true
    videoBindingDialog.bindingMode = detail.bindingMode || 'AUTO'
    videoBindingDialog.sourceKeyframe = detail.sourceKeyframe || null
    videoBindingDialog.keyframeAssetId = detail.sourceKeyframe?.assetId || null
    videoBindingDialog.availableKeyframes = detail.availableKeyframes || []
    videoBindingDialog.inheritedReferences = detail.inheritedReferences || null
    if (!videoBindingDialog.availableKeyframes.length) {
      videoBindingDialog.loadError = '当前分镜没有可用的已批准关键帧版本'
    }
  }).catch(() => {
    if (requestId === videoBindingLoadRequestId && videoBindingDialog.assetId === asset.assetId) {
      videoBindingDialog.loadError = '来源关键帧绑定加载失败，请检查服务状态后重试'
    }
  }).finally(() => {
    if (requestId === videoBindingLoadRequestId && videoBindingDialog.assetId === asset.assetId) {
      videoBindingDialog.loading = false
    }
  })
}

function syncVideoBindingSelection() {
  const keyframe = referenceOptionById(
    videoBindingDialog.availableKeyframes, videoBindingDialog.keyframeAssetId)
  videoBindingDialog.sourceKeyframe = keyframe
  videoBindingDialog.inheritedReferences = null
  if (!keyframe?.assetId) return
  const requestId = ++videoBindingSelectionRequestId
  getAiVideoKeyframeReferenceBinding(keyframe.assetId).then(response => {
    if (requestId === videoBindingSelectionRequestId
      && String(videoBindingDialog.keyframeAssetId) === String(keyframe.assetId)) {
      videoBindingDialog.inheritedReferences = response?.data || null
    }
  }).catch(() => {
    if (requestId === videoBindingSelectionRequestId) {
      proxy.$modal.msgError('所选关键帧的人物/场景继承关系读取失败')
    }
  })
}

function submitVideoSourceBinding() {
  const assetId = videoBindingDialog.assetId
  if (!assetId || !videoBindingDialog.keyframeAssetId) return
  videoBindingDialog.submitting = true
  regeneratingAssetId.value = assetId
  updateAiVideoVideoSourceBinding(assetId, {
    keyframeAssetId: videoBindingDialog.keyframeAssetId
  }).then(response => {
    const draft = response?.data || null
    if (!draft?.assetId) throw new Error('视频绑定响应缺少资产信息')
    videoBindingDialog.open = false
    openVideoPromptDialog(draft)
    proxy.$modal.msgSuccess(String(draft.assetId) === String(assetId)
      ? '已更新当前视频草稿绑定的关键帧版本；尚未调用视频生成服务'
      : `已创建视频 v${draft.versionNo || ''} 草稿并绑定所选关键帧；旧视频版本保持不变`)
    refreshAssetViews()
  }).catch(error => {
    if (error?.message === '视频绑定响应缺少资产信息') proxy.$modal.msgError(error.message)
  }).finally(() => {
    videoBindingDialog.submitting = false
    if (regeneratingAssetId.value === assetId) regeneratingAssetId.value = null
  })
}

function resetVideoBindingToAuto() {
  const assetId = videoBindingDialog.assetId
  if (!assetId) return
  videoBindingDialog.submitting = true
  regeneratingAssetId.value = assetId
  resetAiVideoVideoSourceBinding(assetId).then(response => {
    const draft = response?.data || null
    if (!draft?.assetId) throw new Error('视频自动绑定响应缺少资产信息')
    videoBindingDialog.open = false
    openVideoPromptDialog(draft)
    proxy.$modal.msgSuccess(String(draft.assetId) === String(assetId)
      ? '已将当前视频草稿恢复为自动匹配的最新已批准关键帧'
      : `已创建视频 v${draft.versionNo || ''} 草稿并恢复自动关键帧匹配；旧版本保持不变`)
    refreshAssetViews()
  }).catch(error => {
    if (error?.message === '视频自动绑定响应缺少资产信息') proxy.$modal.msgError(error.message)
  }).finally(() => {
    videoBindingDialog.submitting = false
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
  proxy.$modal.confirm(`请确认“${asset.assetName}”的关键帧画面可以用于视频生成。确认后只会同意图片并创建可编辑的视频提示词草稿，不会提交视频生成任务。`).then(() => {
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
      ? '关键帧已同意，视频提示词草稿已准备好；当前尚未调用视频生成服务'
      : '视频提示词草稿已准备好；当前尚未调用视频生成服务')
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
    durationSeconds: Math.min(15, Math.max(3, Math.round(durationMs / 1000))),
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
  if (!Number.isInteger(durationSeconds) || durationSeconds < 3 || durationSeconds > 15) {
    proxy.$modal.msgWarning('HappyHorse 视频时长必须是 3 至 15 秒的整数')
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
        proxy.$modal.msgSuccess('视频提示词已保存，尚未调用视频生成服务')
        if (videoPromptDialog.assetId === assetId) videoPromptDialog.open = false
        return null
      }
      return generateAiVideoAssetVideo(assetId)
    }).then(response => {
      if (!shouldGenerate || !response) return
      const taskId = response.taskId ?? response.data?.taskId ?? response.data
      proxy.$modal.msgSuccess(taskId !== undefined && taskId !== null
        ? `已提交视频生成任务 #${taskId}`
        : '已提交视频生成任务')
      if (videoPromptDialog.assetId === assetId) videoPromptDialog.open = false
    }).finally(() => {
      if (promptSaved) refreshAssetViews()
      if (videoPromptDialog.assetId === assetId) videoPromptDialog.submitting = false
    })
  }
  if (shouldGenerate) {
    proxy.$modal.confirm(`确认使用当前提示词生成 ${durationSeconds} 秒视频吗？任务会携带关键帧、人物三视图和场景参考图，并调用当前视频供应商，产生一次模型调用及相应费用。`).then(submit).catch(() => {})
  } else {
    submit().catch(() => {})
  }
}

function resumeVideoSubmission(asset, sourceDrawer = assetDrawer) {
  const task = sourceDrawer.taskByAssetId[asset.assetId]
  const resume = providerTaskId => resolveAiVideoAssetSubmission(asset.assetId, {
    action: 'RESUME_WITH_PROVIDER_TASK_ID',
    providerTaskId
  }).then(() => {
    proxy.$modal.msgSuccess('已恢复视频供应商任务轮询，不会重复创建视频任务')
    refreshAssetViews()
  })
  if (task?.providerTaskId) {
    proxy.$modal.confirm(`检测到已保存的供应商任务ID：${task.providerTaskId}。确认恢复该任务的结果轮询吗？此操作不会重新生成或重复计费。`)
      .then(() => resume(task.providerTaskId)).catch(() => {})
    return
  }
  proxy.$modal.prompt('请先在当前视频供应商控制台核对本次调用，再粘贴对应的供应商任务ID。系统只会恢复结果轮询，不会重新提交生成。')
    .then(({ value }) => resume(String(value || '').trim())).catch(() => {})
}

function confirmVideoNotSubmitted(asset) {
  proxy.$modal.confirm('只有在当前视频供应商控制台确认没有对应任务时才能解锁。错误确认可能导致重复生成和重复计费，是否继续？')
    .then(() => proxy.$modal.confirm('再次确认：视频供应商确实未受理本次请求。确认后草稿会变为失败状态，可修改后重新提交。'))
    .then(() => resolveAiVideoAssetSubmission(asset.assetId, {
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
  if (asset.status === 'GENERATING') return asset.assetType === 'VIDEO_CLIP' ? '正在等待视频生成结果…' : '正在等待图片模型结果…'
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
    if (asset.status === 'DRAFT') return '视频提示词草稿已准备好；保存不会调用视频供应商，生成前仍需你二次确认'
    if (sourceDrawer.taskLoadError && (asset.status === 'GENERATING' || asset.status === 'REJECTED')) return sourceDrawer.taskLoadError
    if (task?.errorMessage) return task.errorMessage
    if (asset.status === 'REJECTED') {
      try {
        return JSON.parse(asset.metadataJson || '{}').generationError || '视频生成失败，可修改提示词后手动重试'
      } catch (error) {
        return '视频生成失败，可修改提示词后手动重试'
      }
    }
    if (asset.status === 'GENERATED') return '视频片段已生成并保存到资产库'
    if (task?.status === 'RETRYING') return '上次视频供应商调用未正常结束，任务正在等待恢复'
    if (task?.status === 'NEEDS_REVIEW') return task.errorMessage || '视频供应商提交结果待人工核对，请勿重复生成'
    if (task?.status === 'WAITING_CALLBACK') return '视频供应商已接收任务，正在生成并等待结果'
    if (task?.status === 'RUNNING') return '视频供应商正在生成，请耐心等待'
    if (task?.status === 'QUEUED') return '视频任务已排队，等待调用当前供应商'
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
.studio-page {
  --studio-canvas: #0c1118;
  --studio-surface: #141b24;
  --studio-surface-raised: #1a222d;
  --studio-border: #273241;
  --studio-border-soft: #202a36;
  --studio-text: #f3f5f7;
  --studio-muted: #919dac;
  --studio-accent: #e5904a;
  --studio-accent-strong: #f0a15d;
  position: relative;
  min-height: calc(100dvh - 84px);
  padding: clamp(22px, 3vw, 42px);
  overflow: hidden;
  color: var(--studio-text);
  background:
    radial-gradient(circle at 88% -8%, rgb(229 144 74 / 12%), transparent 30rem),
    radial-gradient(circle at 15% 50%, rgb(75 101 132 / 8%), transparent 36rem),
    var(--studio-canvas);
  font-family: "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
}
.studio-page::before { position: absolute; inset: 0; opacity: .28; background-image: linear-gradient(rgb(255 255 255 / 2%) 1px, transparent 1px), linear-gradient(90deg, rgb(255 255 255 / 2%) 1px, transparent 1px); background-size: 38px 38px; content: ''; pointer-events: none; mask-image: linear-gradient(to bottom, #000, transparent 76%); }
.studio-page > * { position: relative; z-index: 1; }
.studio-page :deep(.el-button) { --el-button-bg-color: transparent; --el-button-border-color: #354153; --el-button-text-color: #dce2e9; --el-button-hover-bg-color: #202a36; --el-button-hover-border-color: #566478; --el-button-hover-text-color: #fff; }
.studio-page :deep(.el-button--primary) { --el-button-bg-color: var(--studio-accent); --el-button-border-color: var(--studio-accent); --el-button-text-color: #17120e; --el-button-hover-bg-color: var(--studio-accent-strong); --el-button-hover-border-color: var(--studio-accent-strong); --el-button-hover-text-color: #17120e; box-shadow: 0 10px 26px rgb(229 144 74 / 18%); }
.studio-page :deep(.el-input__wrapper), .studio-page :deep(.el-select__wrapper) { color: var(--studio-text); background: #101720; box-shadow: 0 0 0 1px var(--studio-border) inset; }
.studio-page :deep(.el-input__inner), .studio-page :deep(.el-select__placeholder), .studio-page :deep(.el-select__selected-item) { color: #d8dee7; }
.studio-page :deep(.el-input__inner::placeholder) { color: #697687; }
.studio-header { display: flex; max-width: 1500px; align-items: flex-end; justify-content: space-between; gap: 30px; margin: 0 auto 26px; }
.studio-heading { display: flex; align-items: center; gap: 16px; }
.studio-mark { display: grid; width: 48px; height: 56px; place-items: center; border: 1px solid #3a312a; border-radius: 9px 20px 9px 9px; background: linear-gradient(145deg, #29231e, #161b22); box-shadow: inset 0 1px rgb(255 255 255 / 7%), 0 16px 40px rgb(0 0 0 / 22%); }
.studio-mark span { width: 9px; height: 25px; border-radius: 2px; background: var(--studio-accent); box-shadow: 13px 7px 0 rgb(229 144 74 / 35%), -13px -6px 0 rgb(229 144 74 / 16%); transform: rotate(28deg); }
.header-actions { display: flex; align-items: center; gap: 10px; }
.eyebrow { margin: 0 0 7px; color: var(--studio-accent); font-size: 11px; font-weight: 750; letter-spacing: .13em; text-transform: uppercase; }
h1, h2, h3, p { margin-top: 0; }
h1 { margin-bottom: 5px; font-size: clamp(27px, 2.4vw, 36px); font-weight: 720; line-height: 1.08; letter-spacing: -.045em; }
.subtitle, .chapter-content p, .drawer-header p { color: var(--studio-muted); }
.subtitle { max-width: 42rem; margin-bottom: 0; font-size: 14px; line-height: 1.65; }
.studio-shell { display: grid; grid-template-columns: minmax(270px, 310px) minmax(0, 1fr); max-width: 1500px; min-height: 650px; gap: 18px; margin: 0 auto; }
.project-rail, .project-workspace { border: 1px solid var(--studio-border); background: rgb(20 27 36 / 88%); box-shadow: inset 0 1px rgb(255 255 255 / 4%), 0 24px 70px rgb(0 0 0 / 22%); backdrop-filter: blur(18px); }
.project-rail { display: flex; min-height: 0; flex-direction: column; padding: 17px; border-radius: 18px 10px 10px 18px; }
.rail-heading { padding: 3px 3px 16px; border-bottom: 1px solid var(--studio-border-soft); }
.rail-heading > div { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; }
.rail-heading span { font-size: 15px; font-weight: 700; }
.rail-heading strong { color: var(--studio-accent); font-size: 22px; font-variant-numeric: tabular-nums; }
.rail-heading small { display: block; margin-top: 3px; color: #748193; font-size: 11px; }
.toolbar { display: grid; gap: 9px; padding: 14px 0; }
.toolbar-row { display: grid; grid-template-columns: minmax(0, 1fr) 40px; gap: 8px; }
.toolbar .el-input, .toolbar .el-select { width: 100%; }
.project-list { display: grid; min-height: 180px; max-height: calc(100dvh - 390px); flex: 1; align-content: start; gap: 7px; padding-right: 3px; overflow-y: auto; }
.project-list-item { display: grid; grid-template-columns: 45px minmax(0, 1fr) auto; align-items: center; gap: 10px; width: 100%; padding: 10px; border: 1px solid transparent; border-radius: 10px; color: #dce2e9; background: transparent; font: inherit; text-align: left; cursor: pointer; transition: transform .2s ease, border-color .2s ease, background-color .2s ease; }
.project-list-item:hover { border-color: #303b4a; background: #19212c; transform: translateX(2px); }
.project-list-item.active { border-color: #4b3b2f; background: linear-gradient(90deg, rgb(229 144 74 / 14%), rgb(25 33 44 / 86%) 58%); box-shadow: inset 3px 0 var(--studio-accent); }
.project-monogram { display: grid; width: 45px; height: 52px; place-items: center; border-radius: 7px 14px 7px 7px; color: #f4ece5; background: radial-gradient(circle at 80% 85%, rgb(229 144 74 / 24%), transparent 45%), #242d39; font-size: 19px; font-weight: 750; }
.project-list-copy { display: grid; min-width: 0; gap: 3px; }
.project-list-copy > strong, .project-list-copy > small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.project-list-copy > strong { font-size: 13px; }
.project-list-copy > small { color: #758295; font-size: 10px; }
.project-list-meta { display: flex; align-items: center; gap: 5px; color: #9aa5b3; font-size: 10px; font-variant-numeric: tabular-nums; }
.project-list-meta i, .project-status i { display: inline-block; width: 6px; height: 6px; border-radius: 50%; background: #7d8997; box-shadow: 0 0 0 3px rgb(125 137 151 / 10%); }
.project-list-meta i.status-active, .project-status i.status-active { background: #7aaa91; box-shadow: 0 0 0 3px rgb(122 170 145 / 12%); }
.project-list-meta i.status-paused, .project-status i.status-paused { background: var(--studio-accent); box-shadow: 0 0 0 3px rgb(229 144 74 / 12%); }
.rail-empty { display: grid; place-items: center; gap: 4px; padding: 34px 12px; color: #a3adba; text-align: center; }
.rail-empty small { color: #697687; line-height: 1.5; }
.project-workspace { min-width: 0; padding: clamp(18px, 2.2vw, 30px); border-radius: 10px 18px 18px 10px; }
.project-hero { position: relative; min-height: 340px; padding: clamp(20px, 2.8vw, 34px); overflow: hidden; border: 1px solid #303b4a; border-radius: 14px; background: radial-gradient(circle at 79% 80%, rgb(229 144 74 / 17%), transparent 24rem), linear-gradient(145deg, #1b2430, #121820 68%); box-shadow: inset 0 1px rgb(255 255 255 / 5%); }
.hero-ambient { position: absolute; right: -90px; bottom: -160px; width: 410px; height: 410px; border: 1px solid rgb(229 144 74 / 10%); border-radius: 47% 53% 55% 45%; box-shadow: 0 0 0 42px rgb(229 144 74 / 3%), 0 0 0 88px rgb(229 144 74 / 2%); transform: rotate(-18deg); }
.hero-topline { position: relative; display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.project-status { display: inline-flex; align-items: center; gap: 8px; color: #bac3cf; font-size: 12px; font-weight: 650; }
.hero-tools { display: flex; align-items: center; gap: 8px; }
.hero-content { position: relative; display: flex; max-width: 820px; align-items: center; gap: clamp(22px, 3vw, 42px); margin-top: 32px; }
.hero-poster { position: relative; display: grid; width: 148px; aspect-ratio: .82; flex: 0 0 auto; place-items: center; overflow: hidden; border: 1px solid #3b4655; border-radius: 12px 30px 12px 12px; background: radial-gradient(circle at 70% 75%, rgb(229 144 74 / 34%), transparent 43%), linear-gradient(145deg, #333e4c, #17202a 70%); box-shadow: 0 24px 48px rgb(0 0 0 / 32%); }
.hero-poster::before { position: absolute; z-index: 1; inset: 10px; border: 1px solid rgb(255 255 255 / 10%); border-radius: 7px 22px 7px 7px; content: ''; pointer-events: none; }
.hero-poster > img { width: 100%; height: 100%; object-fit: cover; }
.hero-poster > span { font-size: 62px; font-weight: 780; text-shadow: 0 8px 25px rgb(0 0 0 / 35%); }
.hero-copy { min-width: 0; }
.hero-copy > p { margin-bottom: 8px; color: var(--studio-accent); font-size: 11px; font-weight: 700; letter-spacing: .08em; }
.hero-copy h2 { overflow: hidden; margin-bottom: 11px; font-size: clamp(29px, 3.4vw, 48px); line-height: 1.05; letter-spacing: -.045em; text-overflow: ellipsis; white-space: nowrap; }
.hero-copy > span { display: block; max-width: 55ch; color: #9ba7b6; font-size: 14px; line-height: 1.65; }
.hero-meta { display: flex; flex-wrap: wrap; gap: 8px 18px; margin-top: 20px; color: #778496; font-size: 11px; }
.hero-meta span + span { position: relative; }
.hero-meta span + span::before { position: absolute; top: 50%; left: -10px; width: 2px; height: 2px; border-radius: 50%; background: #566273; content: ''; }
.hero-actions { position: relative; display: flex; gap: 10px; margin-top: 28px; margin-left: 190px; }
.workflow-panel { margin-top: 18px; padding: 20px 22px 22px; border: 1px solid var(--studio-border-soft); border-radius: 12px; background: rgb(17 23 31 / 76%); }
.section-title { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; margin-bottom: 18px; }
.section-title h3 { margin-bottom: 0; font-size: 18px; }
.section-title > span { color: #6f7c8d; font-size: 11px; }
.workflow-steps { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 0; margin: 0; padding: 0; list-style: none; }
.workflow-steps li { position: relative; display: flex; align-items: center; gap: 10px; min-width: 0; padding-right: 20px; color: #778496; }
.workflow-steps li::after { position: absolute; top: 16px; right: 7px; left: 42px; height: 1px; background: #313b49; content: ''; }
.workflow-steps li:last-child::after { display: none; }
.workflow-steps li > span { z-index: 1; display: grid; width: 32px; height: 32px; flex: 0 0 auto; place-items: center; border: 1px solid #3b4654; border-radius: 9px; background: #161e27; font-family: monospace; font-size: 9px; }
.workflow-steps li.active > span { border-color: #74543b; color: #17120e; background: var(--studio-accent); box-shadow: 0 0 0 5px rgb(229 144 74 / 8%); }
.workflow-steps li > div { z-index: 1; display: grid; min-width: 0; gap: 2px; padding-right: 8px; background: #11171f; }
.workflow-steps strong { overflow: hidden; color: #cbd2da; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.workflow-steps small { overflow: hidden; font-size: 9px; text-overflow: ellipsis; white-space: nowrap; }
.workspace-cards { display: grid; grid-template-columns: 1.35fr 1fr 1fr; gap: 12px; margin-top: 12px; }
.workspace-card { display: flex; min-width: 0; min-height: 166px; flex-direction: column; align-items: flex-start; justify-content: space-between; gap: 14px; padding: 18px; border: 1px solid var(--studio-border-soft); border-radius: 10px; background: #121922; transition: border-color .2s ease, transform .2s ease, background-color .2s ease; }
.workspace-card:hover { border-color: #3b4654; background: #161e28; transform: translateY(-2px); }
.workspace-card-primary { background: linear-gradient(135deg, rgb(229 144 74 / 10%), #151b23 68%); }
.workspace-card-index { color: var(--studio-accent); font-family: monospace; font-size: 9px; font-weight: 700; letter-spacing: .1em; }
.workspace-card h3 { margin-bottom: 6px; color: #e9edf1; font-size: 14px; }
.workspace-card p { margin-bottom: 0; color: #7f8b9b; font-size: 11px; line-height: 1.6; }
.workspace-empty { display: grid; min-height: 580px; place-items: center; align-content: center; padding: 40px; text-align: center; }
.empty-symbol { display: grid; width: 76px; height: 76px; place-items: center; margin-bottom: 22px; border: 1px solid #574333; border-radius: 18px 34px 18px 18px; color: #19130f; background: var(--studio-accent); box-shadow: 0 20px 60px rgb(229 144 74 / 20%); font-size: 30px; }
.workspace-empty h2 { margin-bottom: 10px; font-size: 28px; }
.workspace-empty > p:not(.eyebrow) { max-width: 34rem; margin-bottom: 24px; color: #8490a0; line-height: 1.7; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; } .form-grid .el-form-item { min-width: 0; }
.project-cover-upload :deep(.el-upload--picture-card), .project-cover-upload :deep(.el-upload-list__item) { width: 126px; height: 154px; }
.chapter-workspace { min-height: 100%; padding: 30px; color: #1d2735; } .drawer-header { display: flex; justify-content: space-between; gap: 20px; align-items: flex-start; padding-bottom: 24px; border-bottom: 1px solid #e9edf3; } .drawer-header h2 { margin-bottom: 8px; } .drawer-actions { display: flex; gap: 8px; white-space: nowrap; }
.chapter-list { margin-top: 18px; } .chapter-item { display: flex; align-items: center; gap: 14px; padding: 16px 0; border-bottom: 1px solid #edf0f4; } .chapter-number { min-width: 38px; color: #f39a4a; font-family: monospace; font-size: 17px; font-weight: 700; } .chapter-content { flex: 1; min-width: 180px; } .chapter-content p { margin-bottom: 0; font-size: 12px; } .chapter-title-button { max-width: 100%; overflow: hidden; margin: 0 0 5px; padding: 0; border: 0; color: #1d2735; background: transparent; font: inherit; font-size: 15px; font-weight: 700; text-align: left; text-overflow: ellipsis; white-space: nowrap; cursor: pointer; } .chapter-title-button:hover, .chapter-title-button:focus-visible { color: #d97824; text-decoration: underline; } .chapter-item-actions { display: flex; align-items: center; justify-content: flex-end; gap: 4px; } .chapter-item-actions :deep(.el-button + .el-button) { margin-left: 0; }
.chapter-analysis-progress { max-width: 520px; margin-top: 10px; padding: 12px 14px; border: 1px solid #f1dfcc; border-radius: 12px; background: linear-gradient(135deg, #fffaf4, #fff); box-shadow: 0 7px 20px rgb(126 77 31 / 7%); }
.chapter-analysis-progress__heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.chapter-analysis-progress__heading strong { color: #c9681f; font-size: 14px; font-variant-numeric: tabular-nums; }
.chapter-analysis-progress__stage { display: inline-flex; align-items: center; min-height: 24px; padding: 3px 9px; border-radius: 999px; color: #a65319; background: #ffead5; font-size: 11px; font-weight: 700; letter-spacing: .03em; }
.chapter-analysis-progress__detail { overflow: hidden; margin: 7px 0 8px !important; color: #687587 !important; font-size: 12px !important; line-height: 1.45; text-overflow: ellipsis; white-space: nowrap; }
.chapter-analysis-progress :deep(.el-progress-bar__outer) { background: #f0e7df; }
.chapter-analysis-progress :deep(.el-progress-bar__inner) { background: linear-gradient(90deg, #f4a057, #df6f2b); transition: width .45s ease; }
.chapter-analysis-phases { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 4px; margin: 10px 0 0; padding: 0; list-style: none; }
.chapter-analysis-phases li { position: relative; display: flex; align-items: center; gap: 5px; min-width: 0; color: #a4abb5; font-size: 10px; }
.chapter-analysis-phases li::after { position: absolute; top: 9px; right: 2px; left: 23px; height: 1px; background: #eadfd4; content: ''; }
.chapter-analysis-phases li:last-child::after { display: none; }
.chapter-analysis-phases li > span { z-index: 1; display: inline-flex; flex: 0 0 auto; align-items: center; justify-content: center; width: 18px; height: 18px; border: 1px solid #d8dce2; border-radius: 50%; background: #fff; font-size: 9px; font-weight: 700; }
.chapter-analysis-phases li small { z-index: 1; overflow: hidden; padding-right: 3px; background: #fffaf7; text-overflow: ellipsis; white-space: nowrap; }
.chapter-analysis-phases li.is-complete { color: #6c8b58; }
.chapter-analysis-phases li.is-complete > span { border-color: #8eaf78; color: #fff; background: #8eaf78; }
.chapter-analysis-phases li.is-complete::after { background: #a9c497; }
.chapter-analysis-phases li.is-active { color: #c9681f; font-weight: 700; }
.chapter-analysis-phases li.is-active > span { border-color: #e9893d; color: #fff; background: #e9893d; box-shadow: 0 0 0 4px rgb(233 137 61 / 13%); }
.chapter-analysis-error { display: grid; max-width: 520px; gap: 4px; margin-top: 9px; padding: 10px 12px; border: 1px solid #f1caca; border-radius: 9px; color: #b63e3e; background: #fff6f6; font-size: 12px; line-height: 1.45; }
.chapter-analysis-error strong { font-size: 12px; }
.chapter-analysis-error small { color: #be7474; font-family: monospace; }
.story-bible section { margin-bottom: 28px; } .story-bible h3 { margin-bottom: 10px; color: #1d2735; font-size: 16px; } .bible-summary { padding: 18px; border-radius: 12px; background: #fff7ee; } .bible-summary p { margin: 0; color: #555f6e; line-height: 1.8; } .section-heading { display: flex; justify-content: space-between; align-items: center; } .section-heading span { color: #8491a3; font-size: 12px; } .bible-characters { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; } .bible-card { padding: 14px; border: 1px solid #e8edf4; border-radius: 10px; } .bible-card p { min-height: 40px; margin: 8px 0; color: #657181; font-size: 13px; line-height: 1.55; } .bible-card small { color: #f39a4a; } .bible-scene { margin-bottom: 10px; padding: 16px; border-left: 3px solid #f39a4a; border-radius: 0 10px 10px 0; background: #f7f9fb; } .bible-scene p { margin: 7px 0; color: #657181; font-size: 13px; } .scene-no { display: inline-block; width: 34px; color: #f39a4a; font-family: monospace; } .scene-meta { color: #8491a3; font-size: 12px; }
.asset-filter-context { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; color: #7d8999; font-size: 12px; } .asset-toolbar { display: flex; gap: 10px; margin-bottom: 20px; } .asset-toolbar .el-select { width: 170px; } .asset-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; } .asset-card { overflow: hidden; border: 1px solid #e8edf4; border-radius: 12px; background: #fff; } .asset-preview { display: flex; align-items: center; justify-content: center; height: 210px; color: #8491a3; background: #f3f6fa; font-size: 13px; } .asset-preview :deep(.el-image), .asset-preview video { width: 100%; height: 100%; object-fit: cover; } .asset-body { padding: 12px; } .asset-body .el-tag + .el-tag { margin-left: 6px; } .asset-body h3 { overflow: hidden; margin: 10px 0 5px; color: #1d2735; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; } .asset-body p { margin: 0; color: #8491a3; font-size: 12px; } .character-view-spec { margin-top: 6px !important; color: #b86b23 !important; font-weight: 600; } .asset-prompt-preview { margin-top: 10px; padding: 9px 10px; border-radius: 8px; background: #f6f8fb; } .asset-prompt-preview strong { display: block; margin-bottom: 3px; color: #566173; font-size: 11px; } .asset-prompt-preview p { display: -webkit-box; overflow: hidden; margin-bottom: 7px; line-height: 17px; word-break: break-word; -webkit-box-orient: vertical; -webkit-line-clamp: 2; } .asset-prompt-preview p:last-child { margin-bottom: 0; } .prompt-form { margin-top: 18px; } .prompt-form :deep(textarea) { line-height: 1.6; } .asset-task-status { min-height: 32px; margin-top: 7px !important; color: #d75a4a !important; line-height: 16px; } .video-action { width: 100%; margin-top: 12px; } .asset-version-actions { display: flex; gap: 8px; margin-top: 10px; } .asset-version-actions .el-button { flex: 1; margin-left: 0; }
.regeneration-reference-alert { margin-top: 12px; }
.regeneration-reference-form .el-select { width: 100%; }
.reference-option-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; width: 100%; }
.reference-option-row span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.reference-option-row small { flex-shrink: 0; color: #9099a8; }
.regeneration-reference-help { display: block; margin-top: 7px; color: #8793a3; line-height: 1.5; }
.binding-dialog-status { margin-top: 12px; }
.video-binding-inheritance { display: grid; gap: 7px; padding: 12px 14px; border: 1px solid #e4eaf1; border-radius: 9px; color: #4f5d70; background: #f8fafc; }
.video-binding-inheritance span { color: #7f8b9b; font-size: 12px; line-height: 1.55; }
.asset-binding-summary { margin-top: 6px !important; padding: 6px 8px; border-radius: 6px; color: #9b612d !important; background: #fff7ee; line-height: 1.45; word-break: break-word; }
.asset-binding-summary.inherited { color: #647286 !important; background: #f4f7fa; }
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
@media (max-width: 1100px) {
  .studio-shell { grid-template-columns: 260px minmax(0, 1fr); }
  .hero-poster { width: 116px; }
  .hero-actions { margin-left: 150px; }
  .workspace-cards { grid-template-columns: 1fr 1fr; }
  .workspace-card-primary { grid-column: 1 / -1; min-height: 138px; }
  .workflow-steps { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px 0; }
  .workflow-steps li:nth-child(2)::after { display: none; }
}
@media (max-width: 820px) {
  .studio-shell { grid-template-columns: 1fr; }
  .project-rail, .project-workspace { border-radius: 15px; }
  .project-list { grid-template-columns: repeat(2, minmax(0, 1fr)); max-height: 300px; }
}
@media (max-width: 700px) { .studio-page { padding: 20px; } .studio-header { align-items: flex-start; flex-direction: column; } .header-actions { width: 100%; } .header-actions .el-button { flex: 1; } .toolbar .el-input { width: 100%; } .toolbar { flex-wrap: wrap; } .form-grid, .video-summary-grid, .chapter-video-version-grid { grid-template-columns: 1fr; gap: 0; } .video-summary-grid, .chapter-video-version-grid { gap: 8px; } .video-summary-heading, .duration-editor, .chapter-video-shot-heading { align-items: flex-start; flex-direction: column; } .chapter-item { align-items: flex-start; flex-wrap: wrap; } .chapter-item-actions { width: 100%; justify-content: flex-start; } .chapter-video-scene-heading { align-items: flex-start; } .chapter-video-scene-heading div { align-items: flex-start; flex-direction: column; gap: 3px; } .chapter-video-toolbar-actions { justify-content: stretch; } .chapter-video-toolbar-actions .el-button { flex: 1; } }
@media (max-width: 700px) { .studio-heading { align-items: flex-start; } .studio-mark { width: 42px; height: 48px; flex: 0 0 auto; } .studio-header { margin-bottom: 18px; } .project-rail, .project-workspace { padding: 14px; } .project-list { grid-template-columns: 1fr; max-height: 330px; } .project-hero { min-height: 0; padding: 20px; } .hero-content { align-items: flex-start; gap: 18px; margin-top: 24px; } .hero-poster { width: 82px; border-radius: 9px 20px 9px 9px; } .hero-poster > span { font-size: 36px; } .hero-copy h2 { font-size: 27px; white-space: normal; } .hero-copy > span { font-size: 12px; } .hero-meta { gap: 6px 14px; } .hero-actions { flex-wrap: wrap; margin: 22px 0 0; } .hero-actions .el-button:first-child { flex: 1; } .section-title { align-items: flex-start; flex-direction: column; gap: 4px; } .workflow-steps { grid-template-columns: 1fr; gap: 14px; } .workflow-steps li::after { top: 31px; right: auto; bottom: -15px; left: 16px; width: 1px; height: 16px; } .workflow-steps li:nth-child(2)::after { display: block; } .workflow-steps li:last-child::after { display: none; } .workspace-cards { grid-template-columns: 1fr; } .workspace-card-primary { grid-column: auto; } }
@media (max-width: 700px) { .chapter-content { width: calc(100% - 52px); } .chapter-analysis-progress { max-width: none; } .chapter-analysis-phases li { align-items: center; flex-direction: column; gap: 3px; text-align: center; } .chapter-analysis-phases li::after { top: 9px; right: -50%; left: 50%; } .chapter-analysis-phases li small { width: 100%; padding: 0; background: transparent; } }
@media (max-width: 700px) { .chapter-material-stats, .chapter-material-card-grid, .keyframe-action-row { grid-template-columns: 1fr; } .chapter-material-card-grid { padding: 12px; } .shot-reference-heading { align-items: flex-start; flex-direction: column; } .shot-reference-tags { justify-content: flex-start; } .shot-reference-strip { grid-template-columns: repeat(2, minmax(0, 1fr)); } .chapter-video-toolbar-actions { flex-wrap: wrap; } }
</style>
