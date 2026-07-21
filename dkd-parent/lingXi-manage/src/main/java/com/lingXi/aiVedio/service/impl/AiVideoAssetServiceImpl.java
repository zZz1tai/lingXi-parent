package com.lingXi.aiVedio.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lingXi.common.exception.ServiceException;
import com.lingXi.common.utils.SecurityUtils;
import com.lingXi.aiVedio.domain.AiVideoAsset;
import com.lingXi.aiVedio.domain.AiVideoAssetRelation;
import com.lingXi.aiVedio.domain.AiVideoChapter;
import com.lingXi.aiVedio.domain.AiVideoGenerationTask;
import com.lingXi.aiVedio.domain.AiVideoProject;
import com.lingXi.aiVedio.domain.AiVideoShot;
import com.lingXi.aiVedio.domain.dto.AiVideoAssetRegenerationDraftRequest;
import com.lingXi.aiVedio.domain.dto.AiVideoKeyframeReferenceBindingRequest;
import com.lingXi.aiVedio.domain.dto.AiVideoVideoSourceBindingRequest;
import com.lingXi.aiVedio.config.AiVideoModelConfigService;
import com.lingXi.aiVedio.mapper.AiVideoAssetMapper;
import com.lingXi.aiVedio.mapper.AiVideoAssetRelationMapper;
import com.lingXi.aiVedio.mapper.AiVideoChapterMapper;
import com.lingXi.aiVedio.mapper.AiVideoGenerationTaskMapper;
import com.lingXi.aiVedio.mapper.AiVideoShotMapper;
import com.lingXi.aiVedio.service.IAiVideoAssetService;
import com.lingXi.aiVedio.service.IAiVideoProjectService;
import com.lingXi.aiVedio.service.AiVideoGenerationService;
import com.lingXi.aiVedio.service.AiVideoImageReferenceService;
import com.lingXi.aiVedio.service.AiVideoImageReferenceService.ResolvedImageReferences;
import com.lingXi.aiVedio.util.AiVideoJsonMetadata;
import com.lingXi.aiVedio.storage.AiVideoLocalAssetStorage;

@Service
public class AiVideoAssetServiceImpl implements IAiVideoAssetService
{
    private static final Logger log = LoggerFactory.getLogger(AiVideoAssetServiceImpl.class);
    private static final int MAX_CHARACTER_REFERENCE_IMAGES = 4;

    @Autowired
    private AiVideoAssetMapper assetMapper;

    @Autowired
    private AiVideoAssetRelationMapper assetRelationMapper;

    @Autowired
    private AiVideoGenerationTaskMapper taskMapper;

    @Autowired
    private AiVideoShotMapper shotMapper;

    @Autowired
    private AiVideoChapterMapper chapterMapper;

    @Autowired
    private IAiVideoProjectService projectService;

    @Autowired
    private AiVideoGenerationService videoGenerationService;

    @Autowired
    private AiVideoImageReferenceService imageReferenceService;

    @Autowired
    private AiVideoLocalAssetStorage assetStorage;

    @Autowired
    private AiVideoModelConfigService modelConfigService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public List<AiVideoAsset> selectAiVideoAssetList(AiVideoAsset asset)
    {
        if (asset.getProjectId() == null)
        {
            throw new ServiceException("项目ID不能为空");
        }
        projectService.checkProjectOwner(asset.getProjectId());
        return assetMapper.selectAiVideoAssetList(asset);
    }

    @Override
    public AiVideoAsset selectAiVideoAssetByAssetId(Long assetId)
    {
        AiVideoAsset asset = assetMapper.selectAiVideoAssetByAssetId(assetId);
        if (asset == null)
        {
            throw new ServiceException("资产不存在");
        }
        projectService.checkProjectOwner(asset.getProjectId());
        return asset;
    }

    @Override
    public void approveAiVideoAsset(Long assetId)
    {
        AiVideoAsset asset = selectAiVideoAssetByAssetId(assetId);
        if (!"SHOT_KEYFRAME".equals(asset.getAssetType()))
        {
            throw new ServiceException("只有镜头关键帧需要视频生成审批");
        }
        if ("APPROVED".equals(asset.getStatus()))
        {
            return;
        }
        if (!"GENERATED".equals(asset.getStatus()))
        {
            throw new ServiceException("关键帧图片尚未生成完成，暂时不能审批");
        }
        if (assetMapper.approveAiVideoAsset(assetId, SecurityUtils.getUsername()) != 1)
        {
            throw new ServiceException("关键帧审批状态已变化，请刷新后重试");
        }
    }

    @Override
    @Transactional
    public void retryImageGeneration(Long assetId)
    {
        AiVideoAsset asset = selectAiVideoAssetByAssetId(assetId);
        if ("VIDEO_CLIP".equals(asset.getAssetType()))
        {
            throw new ServiceException("视频资产不能使用图片重试");
        }
        if (!"REJECTED".equals(asset.getStatus()))
        {
            throw new ServiceException("只有生成失败的图片可以重新生成");
        }
        AiVideoGenerationTask task = taskMapper.selectLatestImageTaskByAssetId(assetId);
        if (task == null)
        {
            throw new ServiceException("未找到该资产对应的图片生成任务");
        }
        final String username = SecurityUtils.getUsername();
        validateImagePrompt(asset.getPromptText());
        ResolvedImageReferences references = imageReferenceService.resolveAndValidate(asset);
        String requestJson = buildImageRequestJson(asset, references.getAssetIds());
        String generationParamsJson = buildImageGenerationParamsJson(asset);
        if (taskMapper.resetFailedImageTaskForRetry(
                task.getTaskId(), requestJson, modelConfigService.getRequiredConfig().getImageModel()) != 1)
        {
            throw new ServiceException("图片任务状态已变化，请刷新后重试");
        }
        if (assetMapper.markAiVideoAssetGenerating(assetId, generationParamsJson, username) != 1)
        {
            throw new ServiceException("图片资产状态更新失败");
        }
    }

    @Override
    @Transactional
    public void updateImagePrompt(Long assetId, String promptText, String negativePromptText)
    {
        AiVideoAsset asset = selectAiVideoAssetByAssetId(assetId);
        if ("VIDEO_CLIP".equals(asset.getAssetType()))
        {
            throw new ServiceException("视频资产不支持修改图片提示词");
        }
        if (!"DRAFT".equals(asset.getStatus()) && !"REJECTED".equals(asset.getStatus()))
        {
            throw new ServiceException("只有待生成或生成失败的图片可以修改提示词");
        }
        String normalizedPrompt = promptText == null ? "" : promptText.trim();
        String normalizedNegativePrompt = negativePromptText == null ? null : negativePromptText.trim();
        validateImagePrompt(normalizedPrompt);
        if (assetMapper.updateAiVideoAssetPrompt(assetId, normalizedPrompt, normalizedNegativePrompt,
                SecurityUtils.getUsername()) != 1)
        {
            throw new ServiceException("提示词状态已变化，请刷新后重试");
        }
    }

    @Override
    @Transactional
    public AiVideoAsset createVideoPromptDraft(Long keyframeAssetId)
    {
        AiVideoAsset keyframe = assetMapper.selectAiVideoAssetByAssetIdForUpdate(keyframeAssetId);
        if (keyframe == null)
        {
            throw new ServiceException("关键帧资产不存在");
        }
        projectService.checkProjectOwner(keyframe.getProjectId());
        validateApprovedKeyframe(keyframe);

        AiVideoAsset existing = assetMapper.selectLatestEditableVideoDraftBySourceAssetId(keyframeAssetId);
        if (existing != null)
        {
            if (!keyframe.getProjectId().equals(existing.getProjectId()))
            {
                throw new ServiceException("视频草稿与关键帧不属于同一项目");
            }
            return existing;
        }
        if (keyframe.getShotId() == null)
        {
            throw new ServiceException("关键帧未关联分镜，无法提炼视频提示词");
        }
        AiVideoShot shot = shotMapper.selectAiVideoShotByShotId(keyframe.getShotId());
        if (shot == null || !keyframe.getProjectId().equals(shot.getProjectId()))
        {
            throw new ServiceException("关键帧关联的分镜不存在");
        }
        validateCurrentChapterShot(keyframe, shot);
        JsonNode promptContext = parseShotPromptContext(shot);
        String videoPrompt = requirePromptContextText(promptContext, "videoPrompt", "视频正向提示词");
        String videoNegativePrompt = optionalPromptContextText(promptContext, "videoNegativePrompt");
        Integer durationMs = shot.getDurationMs();
        validateVideoPrompt(videoPrompt, durationMs);
        String username = SecurityUtils.getUsername();
        AiVideoAsset draft = new AiVideoAsset();
        draft.setProjectId(keyframe.getProjectId());
        draft.setChapterId(keyframe.getChapterId());
        draft.setSceneId(keyframe.getSceneId());
        draft.setShotId(keyframe.getShotId());
        draft.setAssetCode("video-source-" + keyframe.getAssetId() + "-" + System.currentTimeMillis());
        draft.setAssetName(keyframe.getAssetName() + "视频片段");
        draft.setAssetType("VIDEO_CLIP");
        draft.setAssetScope("SHOT");
        draft.setCanonicalFlag(0);
        draft.setStatus("DRAFT");
        draft.setVersionNo(1);
        draft.setSourceAssetId(keyframe.getAssetId());
        draft.setDurationMs(durationMs);
        draft.setPromptText(videoPrompt);
        draft.setNegativePromptText(videoNegativePrompt);
        draft.setGenerationParamsJson(buildVideoGenerationParamsJson(durationMs));
        Integer analysisVersion = AiVideoJsonMetadata.analysisVersion(keyframe.getMetadataJson());
        String metadataJson = AiVideoJsonMetadata.withAnalysisVersion(shot.getPromptContextJson(),
                analysisVersion == null ? shot.getVersionNo() : analysisVersion);
        draft.setMetadataJson(AiVideoJsonMetadata.withVideoSourceBinding(
                metadataJson, keyframe.getAssetId(), keyframe.getVersionNo(), "AUTO"));
        draft.setCreateBy(username);
        if (assetMapper.insertAiVideoAsset(draft) != 1)
        {
            throw new ServiceException("视频提示词草稿创建失败");
        }
        return draft;
    }

    @Override
    @Transactional
    public AiVideoAsset updateVideoPrompt(Long videoAssetId, String promptText,
            String negativePromptText, Integer durationMs)
    {
        AiVideoAsset video = selectAiVideoAssetByAssetId(videoAssetId);
        if (!"VIDEO_CLIP".equals(video.getAssetType()))
        {
            throw new ServiceException("只有视频草稿可以修改视频提示词");
        }
        if (!"DRAFT".equals(video.getStatus()) && !"REJECTED".equals(video.getStatus()))
        {
            throw new ServiceException("只有待生成或生成失败的视频草稿可以修改提示词");
        }
        String normalizedPrompt = promptText == null ? "" : promptText.trim();
        String normalizedNegativePrompt = negativePromptText == null ? null : negativePromptText.trim();
        if (normalizedNegativePrompt != null && normalizedNegativePrompt.isEmpty())
        {
            normalizedNegativePrompt = null;
        }
        Integer requestedDurationMs = durationMs == null ? video.getDurationMs() : durationMs;
        validateVideoPrompt(normalizedPrompt, requestedDurationMs);
        if (assetMapper.updateAiVideoAssetVideoPrompt(videoAssetId, normalizedPrompt,
                normalizedNegativePrompt, requestedDurationMs,
                buildVideoGenerationParamsJson(requestedDurationMs), SecurityUtils.getUsername()) != 1)
        {
            throw new ServiceException("视频提示词状态已变化，请刷新后重试");
        }
        return selectAiVideoAssetByAssetId(videoAssetId);
    }

    @Override
    @Transactional
    public AiVideoAsset createRegenerationDraft(Long assetId,
            AiVideoAssetRegenerationDraftRequest request)
    {
        AiVideoAsset source = assetMapper.selectAiVideoAssetByAssetIdForUpdate(assetId);
        if (source == null)
        {
            throw new ServiceException("资产不存在或已删除");
        }
        projectService.checkProjectOwner(source.getProjectId());
        if (!isRegeneratableAssetType(source.getAssetType()))
        {
            throw new ServiceException("只有图片或视频资产可以创建重新生成草稿");
        }
        KeyframeReferenceOverride referenceOverride = resolveKeyframeReferenceOverride(source, request);
        if (isActiveAssetStatus(source.getStatus())
                || taskMapper.countActiveAiVideoGenerationTasksByAssetId(source.getAssetId()) > 0)
        {
            throw new ServiceException("资产正在生成或处理中，请等待任务结束后再创建新版本");
        }
        if (source.getAssetCode() == null || source.getAssetCode().trim().isEmpty())
        {
            throw new ServiceException("资产编码为空，无法建立版本谱系");
        }

        Integer maxVersion = assetMapper.selectMaxAssetVersionForUpdate(
                source.getProjectId(), source.getAssetCode());
        if (maxVersion == null || maxVersion.intValue() < 1)
        {
            throw new ServiceException("资产版本谱系不存在");
        }
        if (maxVersion.intValue() == Integer.MAX_VALUE)
        {
            throw new ServiceException("资产版本号已达到上限");
        }

        Long newSourceAssetId;
        if ("VIDEO_CLIP".equals(source.getAssetType()))
        {
            validateVideoDuration(source.getDurationMs());
            Long requestedKeyframeAssetId = request == null ? null : request.getKeyframeAssetId();
            newSourceAssetId = requestedKeyframeAssetId == null
                    ? source.getSourceAssetId() : requestedKeyframeAssetId;
            AiVideoAsset keyframe = newSourceAssetId == null
                    ? null : assetMapper.selectAiVideoAssetByAssetIdForUpdate(newSourceAssetId);
            validateVideoSourceKeyframe(source, keyframe);
        }
        else if ("SHOT_KEYFRAME".equals(source.getAssetType()))
        {
            // 关键帧的直接来源始终是场景参考图；版本谱系由 metadata.regeneratedFromAssetId 记录。
            newSourceAssetId = referenceOverride == null
                    ? source.getSourceAssetId() : referenceOverride.getSceneReference().getAssetId();
        }
        else
        {
            newSourceAssetId = source.getSourceAssetId() == null
                    ? source.getAssetId() : source.getSourceAssetId();
        }

        String username = SecurityUtils.getUsername();
        AiVideoAsset draft = new AiVideoAsset();
        draft.setProjectId(source.getProjectId());
        draft.setChapterId(source.getChapterId());
        draft.setSceneId(source.getSceneId());
        draft.setShotId(source.getShotId());
        draft.setCharacterId(source.getCharacterId());
        draft.setAssetCode(source.getAssetCode());
        draft.setAssetName(source.getAssetName());
        draft.setAssetType(source.getAssetType());
        draft.setAssetScope(source.getAssetScope());
        // canonical_flag 表示资产语义（人物/场景规范参考），不是“当前最新版本”。
        // 新版本继续保留这一语义；版本新旧由 asset_code + version_no 判断。
        draft.setCanonicalFlag(source.getCanonicalFlag());
        draft.setStatus("DRAFT");
        draft.setVersionNo(Integer.valueOf(maxVersion.intValue() + 1));
        draft.setSourceAssetId(newSourceAssetId);
        draft.setDurationMs(source.getDurationMs());
        draft.setPromptText(source.getPromptText());
        draft.setNegativePromptText(source.getNegativePromptText());
        draft.setGenerationParamsJson(source.getGenerationParamsJson());
        String regenerationMetadata = AiVideoJsonMetadata.regenerationMetadata(
                source.getMetadataJson(), source.getAssetId());
        if (referenceOverride != null)
        {
            List<Long> characterReferenceAssetIds = new java.util.ArrayList<>();
            for (AiVideoAsset characterReference : referenceOverride.getCharacterReferences())
            {
                characterReferenceAssetIds.add(characterReference.getAssetId());
            }
            regenerationMetadata = AiVideoJsonMetadata.withImageReferenceBinding(regenerationMetadata,
                    referenceOverride.getSceneReference().getAssetId(), characterReferenceAssetIds, "MANUAL");
        }
        if ("VIDEO_CLIP".equals(source.getAssetType()))
        {
            AiVideoAsset keyframe = assetMapper.selectAiVideoAssetByAssetId(newSourceAssetId);
            boolean manuallySelected = request != null && request.getKeyframeAssetId() != null;
            regenerationMetadata = AiVideoJsonMetadata.withVideoSourceBinding(regenerationMetadata,
                    newSourceAssetId, keyframe == null ? null : keyframe.getVersionNo(),
                    manuallySelected ? "MANUAL"
                            : metadataMode(source.getMetadataJson(), "sourceBindingMode", "AUTO"));
        }
        draft.setMetadataJson(regenerationMetadata);
        draft.setCreateBy(username);
        if (assetMapper.insertAiVideoAsset(draft) != 1)
        {
            throw new ServiceException("新版本草稿创建失败");
        }
        if ("SHOT_KEYFRAME".equals(source.getAssetType()))
        {
            if (referenceOverride == null)
            {
                assetRelationMapper.copyIncomingReferenceRelations(
                        source.getProjectId(), source.getAssetId(), draft.getAssetId());
            }
            else
            {
                insertKeyframeReferenceOverride(draft, referenceOverride);
            }
        }
        AiVideoAsset created = assetMapper.selectAiVideoAssetByAssetId(draft.getAssetId());
        if (created == null)
        {
            throw new ServiceException("新版本草稿创建后读取失败");
        }
        return created;
    }

    @Override
    public JsonNode getKeyframeReferenceBinding(Long assetId)
    {
        AiVideoAsset keyframe = selectAiVideoAssetByAssetId(assetId);
        validateKeyframeBindingTarget(keyframe);
        return buildKeyframeReferenceBinding(keyframe);
    }

    @Override
    @Transactional
    public AiVideoAsset updateKeyframeReferenceBinding(Long assetId,
            AiVideoKeyframeReferenceBindingRequest request)
    {
        AiVideoAsset keyframe = lockBindingAsset(assetId);
        validateKeyframeBindingTarget(keyframe);
        validateBindingChangeAllowed(keyframe);
        if (request == null)
        {
            throw new ServiceException("关键帧参考版本不能为空");
        }
        if (request.getMode() != null && !"MANUAL".equalsIgnoreCase(request.getMode().trim()))
        {
            throw new ServiceException("人工修改关键帧参考版本时 mode 只能是 MANUAL");
        }
        AiVideoAssetRegenerationDraftRequest regenerationRequest = new AiVideoAssetRegenerationDraftRequest();
        regenerationRequest.setSceneReferenceAssetId(request.getSceneReferenceAssetId());
        regenerationRequest.setCharacterReferenceAssetIds(request.getCharacterReferenceAssetIds());
        KeyframeReferenceOverride binding = resolveKeyframeReferenceOverride(keyframe, regenerationRequest);
        if (isEditableBindingStatus(keyframe.getStatus()))
        {
            applyKeyframeReferenceBinding(keyframe, binding, "MANUAL");
            return selectAiVideoAssetByAssetId(keyframe.getAssetId());
        }
        return createRegenerationDraft(keyframe.getAssetId(), regenerationRequest);
    }

    @Override
    @Transactional
    public AiVideoAsset resetKeyframeReferenceBinding(Long assetId)
    {
        AiVideoAsset keyframe = lockBindingAsset(assetId);
        validateKeyframeBindingTarget(keyframe);
        validateBindingChangeAllowed(keyframe);
        KeyframeReferenceOverride binding = resolveAutomaticKeyframeReferences(keyframe);
        if (isEditableBindingStatus(keyframe.getStatus()))
        {
            applyKeyframeReferenceBinding(keyframe, binding, "AUTO");
            return selectAiVideoAssetByAssetId(keyframe.getAssetId());
        }

        AiVideoAssetRegenerationDraftRequest request = toRegenerationRequest(binding);
        AiVideoAsset draft = createRegenerationDraft(keyframe.getAssetId(), request);
        String metadataJson = keyframeBindingMetadata(draft.getMetadataJson(), binding, "AUTO");
        if (assetMapper.updateAiVideoAssetReferenceBinding(draft.getAssetId(),
                binding.getSceneReference().getAssetId(), metadataJson, SecurityUtils.getUsername()) != 1)
        {
            throw new ServiceException("关键帧自动绑定状态已变化，请刷新后重试");
        }
        return selectAiVideoAssetByAssetId(draft.getAssetId());
    }

    @Override
    public JsonNode getVideoSourceBinding(Long videoAssetId)
    {
        AiVideoAsset video = selectAiVideoAssetByAssetId(videoAssetId);
        validateVideoBindingTarget(video);
        return buildVideoSourceBinding(video);
    }

    @Override
    @Transactional
    public AiVideoAsset updateVideoSourceBinding(Long videoAssetId,
            AiVideoVideoSourceBindingRequest request)
    {
        AiVideoAsset video = lockBindingAsset(videoAssetId);
        validateVideoBindingTarget(video);
        validateBindingChangeAllowed(video);
        if (request == null || request.getKeyframeAssetId() == null)
        {
            throw new ServiceException("请选择要绑定的关键帧版本");
        }
        AiVideoAsset keyframe = assetMapper.selectAiVideoAssetByAssetIdForUpdate(request.getKeyframeAssetId());
        validateVideoSourceKeyframe(video, keyframe);
        if (isEditableBindingStatus(video.getStatus()))
        {
            applyVideoSourceBinding(video, keyframe, "MANUAL");
            return selectAiVideoAssetByAssetId(video.getAssetId());
        }
        AiVideoAssetRegenerationDraftRequest regenerationRequest = new AiVideoAssetRegenerationDraftRequest();
        regenerationRequest.setKeyframeAssetId(keyframe.getAssetId());
        return createRegenerationDraft(video.getAssetId(), regenerationRequest);
    }

    @Override
    @Transactional
    public AiVideoAsset resetVideoSourceBinding(Long videoAssetId)
    {
        AiVideoAsset video = lockBindingAsset(videoAssetId);
        validateVideoBindingTarget(video);
        validateBindingChangeAllowed(video);
        AiVideoAsset keyframe = resolveAutomaticVideoKeyframe(video);
        if (isEditableBindingStatus(video.getStatus()))
        {
            applyVideoSourceBinding(video, keyframe, "AUTO");
            return selectAiVideoAssetByAssetId(video.getAssetId());
        }

        AiVideoAssetRegenerationDraftRequest request = new AiVideoAssetRegenerationDraftRequest();
        request.setKeyframeAssetId(keyframe.getAssetId());
        AiVideoAsset draft = createRegenerationDraft(video.getAssetId(), request);
        String metadataJson = AiVideoJsonMetadata.withVideoSourceBinding(draft.getMetadataJson(),
                keyframe.getAssetId(), keyframe.getVersionNo(), "AUTO");
        if (assetMapper.updateVideoSourceBinding(draft.getAssetId(), keyframe.getAssetId(),
                metadataJson, SecurityUtils.getUsername()) != 1)
        {
            throw new ServiceException("视频自动绑定状态已变化，请刷新后重试");
        }
        return selectAiVideoAssetByAssetId(draft.getAssetId());
    }

    @Override
    @Transactional
    public void deleteAiVideoAsset(Long assetId)
    {
        AiVideoAsset asset = assetMapper.selectAiVideoAssetByAssetIdForUpdate(assetId);
        if (asset == null)
        {
            throw new ServiceException("资产不存在或已删除");
        }
        projectService.checkProjectOwner(asset.getProjectId());
        if (isActiveAssetStatus(asset.getStatus()))
        {
            throw new ServiceException("资产正在生成或处理中，不能删除");
        }
        if (taskMapper.countActiveAiVideoGenerationTasksByAssetId(assetId) > 0)
        {
            throw new ServiceException("资产仍有关联的活动生成任务，不能删除");
        }
        if (assetRelationMapper.countActiveKeyframeReferences(assetId) > 0)
        {
            throw new ServiceException("该参考图仍被未删除的分镜关键帧引用，不能删除");
        }
        if ("SHOT_KEYFRAME".equals(asset.getAssetType())
                && assetMapper.countActiveVideoAssetsBySourceAssetId(assetId) > 0)
        {
            throw new ServiceException("该关键帧仍被视频版本引用，请先删除对应视频版本");
        }
        final Set<String> storagePathsToDelete = collectUnreferencedStoragePaths(asset);
        if (assetMapper.logicallyDeleteAiVideoAsset(assetId, SecurityUtils.getUsername()) != 1)
        {
            throw new ServiceException("资产状态已变化，请刷新后重试");
        }
        scheduleStorageCleanupAfterCommit(storagePathsToDelete, assetId);
    }

    @Override
    @Transactional
    public Long startImageGeneration(Long assetId)
    {
        final AiVideoAsset asset = selectAiVideoAssetByAssetId(assetId);
        if ("VIDEO_CLIP".equals(asset.getAssetType()))
        {
            throw new ServiceException("视频资产不能生成图片");
        }
        if (!"DRAFT".equals(asset.getStatus()))
        {
            throw new ServiceException("只有待生成图片可以开始生成");
        }
        if (asset.getPromptText() == null || asset.getPromptText().trim().isEmpty())
        {
            throw new ServiceException("请先填写图片提示词");
        }
        final String username = SecurityUtils.getUsername();
        validateImagePrompt(asset.getPromptText());
        ResolvedImageReferences references = imageReferenceService.resolveAndValidate(asset);
        String generationParamsJson = buildImageGenerationParamsJson(asset);
        if (assetMapper.markDraftAiVideoAssetGenerating(assetId, generationParamsJson, username) != 1)
        {
            throw new ServiceException("图片资产状态已变化，请刷新后重试");
        }
        asset.setStatus("GENERATING");

        final AiVideoGenerationTask task = new AiVideoGenerationTask();
        task.setProjectId(asset.getProjectId());
        task.setChapterId(asset.getChapterId());
        task.setAssetId(asset.getAssetId());
        task.setTaskType("IMAGE");
        task.setTaskName("Qwen Image图片生成：" + asset.getAssetName());
        task.setStatus("QUEUED");
        task.setPriority(100);
        task.setIdempotencyKey("qwen-image-" + asset.getAssetCode() + "-" + asset.getVersionNo());
        task.setProviderCode("dashscope");
        task.setModelCode(modelConfigService.getRequiredConfig().getImageModel());
        task.setProgress(5);
        task.setMaxRetry(0);
        task.setRequestJson(buildImageRequestJson(asset, references.getAssetIds()));
        task.setCreateBy(username);
        if (taskMapper.insertAiVideoGenerationTask(task) != 1 || task.getTaskId() == null)
        {
            throw new ServiceException("图片生成任务创建失败");
        }

        return task.getTaskId();
    }

    @Override
    public Long startVideoGeneration(Long videoAssetId)
    {
        AiVideoAsset video = selectAiVideoAssetByAssetId(videoAssetId);
        if (!"VIDEO_CLIP".equals(video.getAssetType()))
        {
            throw new ServiceException("请先从已批准关键帧创建视频提示词草稿");
        }
        if (!"DRAFT".equals(video.getStatus()) && !"REJECTED".equals(video.getStatus()))
        {
            throw new ServiceException("只有待生成或生成失败的视频草稿可以提交生成");
        }
        if (video.getSourceAssetId() == null)
        {
            throw new ServiceException("视频草稿未关联来源关键帧");
        }
        AiVideoAsset keyframe = assetMapper.selectAiVideoAssetByAssetId(video.getSourceAssetId());
        validateApprovedKeyframe(keyframe);
        if (!video.getProjectId().equals(keyframe.getProjectId()))
        {
            throw new ServiceException("视频草稿与来源关键帧不属于同一项目");
        }
        validateVideoPrompt(video.getPromptText(), video.getDurationMs());
        // 视频任务继续使用关键帧已锁定的精确人物/场景版本；提交前再次校验引用可访问性。
        imageReferenceService.resolveAndValidate(keyframe);
        List<AiVideoAsset> boundReferences = assetRelationMapper
                .selectActiveReferenceAssetsByTargetAssetId(keyframe.getAssetId());
        return videoGenerationService.submit(video, keyframe, boundReferences,
                SecurityUtils.getUsername());
    }

    @Override
    @Transactional
    public void resolveVideoSubmission(Long videoAssetId, String action, String providerTaskId)
    {
        AiVideoAsset video = selectAiVideoAssetByAssetId(videoAssetId);
        if (!"VIDEO_CLIP".equals(video.getAssetType()) || !"GENERATING".equals(video.getStatus()))
        {
            throw new ServiceException("只有提交结果待核对的视频资产可以执行此操作");
        }
        AiVideoGenerationTask task = taskMapper.selectLatestNeedsReviewVideoTaskByAssetId(videoAssetId);
        if (task == null)
        {
            throw new ServiceException("未找到待人工核对的视频供应商任务");
        }
        String normalizedAction = action == null ? "" : action.trim().toUpperCase(java.util.Locale.ROOT);
        String username = SecurityUtils.getUsername();
        if ("RESUME_WITH_PROVIDER_TASK_ID".equals(normalizedAction))
        {
            String resolvedTaskId = providerTaskId == null ? "" : providerTaskId.trim();
            if (resolvedTaskId.isEmpty() && task.getProviderTaskId() != null)
            {
                resolvedTaskId = task.getProviderTaskId().trim();
            }
            if (resolvedTaskId.length() < 8 || resolvedTaskId.length() > 255
                    || !resolvedTaskId.matches("[A-Za-z0-9_-]+"))
            {
                throw new ServiceException("请填写有效的视频供应商任务ID");
            }
            if (taskMapper.resolveNeedsReviewVideoTaskWithProviderId(
                    task.getTaskId(), resolvedTaskId, username) != 1)
            {
                throw new ServiceException("视频供应商待核对任务状态已变化，请刷新后重试");
            }
            return;
        }
        if ("CONFIRM_NOT_SUBMITTED".equals(normalizedAction))
        {
            if (task.getProviderTaskId() != null && !task.getProviderTaskId().trim().isEmpty())
            {
                throw new ServiceException("该任务已有供应商任务ID，不能标记为未提交，请恢复轮询");
            }
            video.setMetadataJson(AiVideoJsonMetadata.generationFailure(video.getMetadataJson(),
                    "人工核对确认视频供应商未受理，草稿已解锁"));
            video.setUpdateBy(username);
            if (assetMapper.markAiVideoAssetFailed(video) != 1
                    || taskMapper.resolveNeedsReviewVideoTaskAsNotSubmitted(task.getTaskId(), username) != 1)
            {
                throw new ServiceException("视频供应商待核对任务状态已变化，请刷新后重试");
            }
            return;
        }
        throw new ServiceException("不支持的视频供应商核对操作");
    }

    private void validateApprovedKeyframe(AiVideoAsset keyframe)
    {
        if (keyframe == null || !"SHOT_KEYFRAME".equals(keyframe.getAssetType()))
        {
            throw new ServiceException("只有镜头关键帧可以创建视频提示词草稿");
        }
        if ("GENERATED".equals(keyframe.getStatus()))
        {
            throw new ServiceException("关键帧图片等待你的同意，请先审批");
        }
        if (!"APPROVED".equals(keyframe.getStatus()) || keyframe.getApprovedBy() == null
                || keyframe.getApprovedTime() == null)
        {
            throw new ServiceException("关键帧图片尚未经过你的人工审批");
        }
        if (keyframe.getObjectKey() == null || keyframe.getObjectKey().trim().isEmpty())
        {
            throw new ServiceException("关键帧图片尚未转存完成");
        }
    }

    private boolean isRegeneratableAssetType(String assetType)
    {
        return "VIDEO_CLIP".equals(assetType) || "SHOT_KEYFRAME".equals(assetType)
                || (assetType != null && assetType.endsWith("_REFERENCE"));
    }

    private AiVideoAsset lockBindingAsset(Long assetId)
    {
        AiVideoAsset asset = assetMapper.selectAiVideoAssetByAssetIdForUpdate(assetId);
        if (asset == null)
        {
            throw new ServiceException("资产不存在或已删除");
        }
        projectService.checkProjectOwner(asset.getProjectId());
        return asset;
    }

    private void validateKeyframeBindingTarget(AiVideoAsset asset)
    {
        if (asset == null || !"SHOT_KEYFRAME".equals(asset.getAssetType()))
        {
            throw new ServiceException("只有分镜关键帧可以绑定人物和场景参考版本");
        }
    }

    private void validateVideoBindingTarget(AiVideoAsset asset)
    {
        if (asset == null || !"VIDEO_CLIP".equals(asset.getAssetType()))
        {
            throw new ServiceException("只有视频资产可以绑定来源关键帧版本");
        }
    }

    private void validateBindingChangeAllowed(AiVideoAsset asset)
    {
        if (isActiveAssetStatus(asset.getStatus())
                || taskMapper.countActiveAiVideoGenerationTasksByAssetId(asset.getAssetId()) > 0)
        {
            throw new ServiceException("资产正在生成或处理中，请等待任务结束后再修改绑定");
        }
    }

    private boolean isEditableBindingStatus(String status)
    {
        return "DRAFT".equals(status) || "REJECTED".equals(status);
    }

    private ObjectNode buildKeyframeReferenceBinding(AiVideoAsset keyframe)
    {
        List<AiVideoAsset> references = assetRelationMapper
                .selectActiveReferenceAssetsByTargetAssetId(keyframe.getAssetId());
        AiVideoAsset sceneReference = null;
        ArrayNode characterReferences = objectMapper.createArrayNode();
        for (AiVideoAsset reference : references)
        {
            if ("SCENE_REFERENCE".equals(reference.getAssetType()))
            {
                sceneReference = reference;
            }
            else if ("CHARACTER_REFERENCE".equals(reference.getAssetType()))
            {
                characterReferences.add(objectMapper.valueToTree(reference));
            }
        }
        ObjectNode detail = objectMapper.createObjectNode();
        detail.set("asset", objectMapper.valueToTree(keyframe));
        detail.put("bindingMode", metadataMode(
                keyframe.getMetadataJson(), "referenceBindingMode", "AUTO"));
        detail.put("editableInPlace", isEditableBindingStatus(keyframe.getStatus()));
        if (sceneReference == null)
        {
            detail.putNull("sceneReference");
        }
        else
        {
            detail.set("sceneReference", objectMapper.valueToTree(sceneReference));
        }
        detail.set("characterReferences", characterReferences);
        return detail;
    }

    private ObjectNode buildVideoSourceBinding(AiVideoAsset video)
    {
        AiVideoAsset keyframe = video.getSourceAssetId() == null
                ? null : assetMapper.selectAiVideoAssetByAssetId(video.getSourceAssetId());
        ObjectNode detail = objectMapper.createObjectNode();
        detail.set("asset", objectMapper.valueToTree(video));
        detail.put("bindingMode", metadataMode(video.getMetadataJson(), "sourceBindingMode", "AUTO"));
        detail.put("editableInPlace", isEditableBindingStatus(video.getStatus()));
        if (keyframe == null)
        {
            detail.putNull("sourceKeyframe");
            detail.putNull("inheritedReferences");
        }
        else
        {
            detail.set("sourceKeyframe", objectMapper.valueToTree(keyframe));
            detail.set("inheritedReferences", buildKeyframeReferenceBinding(keyframe));
        }
        ArrayNode availableKeyframes = detail.putArray("availableKeyframes");
        if (video.getShotId() != null)
        {
            for (AiVideoAsset candidate : assetMapper.selectKeyframeVersionsByShotId(
                    video.getProjectId(), video.getShotId()))
            {
                if ("APPROVED".equals(candidate.getStatus())
                        && candidate.getObjectKey() != null && !candidate.getObjectKey().trim().isEmpty())
                {
                    availableKeyframes.add(objectMapper.valueToTree(candidate));
                }
            }
        }
        return detail;
    }

    private void applyKeyframeReferenceBinding(AiVideoAsset keyframe,
            KeyframeReferenceOverride binding, String mode)
    {
        assetRelationMapper.deleteIncomingReferenceRelations(
                keyframe.getProjectId(), keyframe.getAssetId());
        insertKeyframeReferenceOverride(keyframe, binding);
        String metadataJson = keyframeBindingMetadata(keyframe.getMetadataJson(), binding, mode);
        if (assetMapper.updateAiVideoAssetReferenceBinding(keyframe.getAssetId(),
                binding.getSceneReference().getAssetId(), metadataJson, SecurityUtils.getUsername()) != 1)
        {
            throw new ServiceException("关键帧绑定状态已变化，请刷新后重试");
        }
    }

    private void applyVideoSourceBinding(AiVideoAsset video, AiVideoAsset keyframe, String mode)
    {
        String metadataJson = AiVideoJsonMetadata.withVideoSourceBinding(video.getMetadataJson(),
                keyframe.getAssetId(), keyframe.getVersionNo(), mode);
        if (assetMapper.updateVideoSourceBinding(video.getAssetId(), keyframe.getAssetId(),
                metadataJson, SecurityUtils.getUsername()) != 1)
        {
            throw new ServiceException("视频来源关键帧状态已变化，请刷新后重试");
        }
    }

    private String keyframeBindingMetadata(String metadataJson,
            KeyframeReferenceOverride binding, String mode)
    {
        List<Long> characterAssetIds = new ArrayList<>();
        for (AiVideoAsset characterReference : binding.getCharacterReferences())
        {
            characterAssetIds.add(characterReference.getAssetId());
        }
        return AiVideoJsonMetadata.withImageReferenceBinding(metadataJson,
                binding.getSceneReference().getAssetId(), characterAssetIds, mode);
    }

    private AiVideoAssetRegenerationDraftRequest toRegenerationRequest(
            KeyframeReferenceOverride binding)
    {
        AiVideoAssetRegenerationDraftRequest request = new AiVideoAssetRegenerationDraftRequest();
        request.setSceneReferenceAssetId(binding.getSceneReference().getAssetId());
        List<Long> characterAssetIds = new ArrayList<>();
        for (AiVideoAsset characterReference : binding.getCharacterReferences())
        {
            characterAssetIds.add(characterReference.getAssetId());
        }
        request.setCharacterReferenceAssetIds(characterAssetIds);
        return request;
    }

    private KeyframeReferenceOverride resolveAutomaticKeyframeReferences(AiVideoAsset keyframe)
    {
        List<AiVideoAsset> currentReferences = assetRelationMapper
                .selectActiveReferenceAssetsByTargetAssetId(keyframe.getAssetId());
        AiVideoAsset currentScene = null;
        List<AiVideoAsset> currentCharacters = new ArrayList<>();
        for (AiVideoAsset reference : currentReferences)
        {
            if ("SCENE_REFERENCE".equals(reference.getAssetType())) currentScene = reference;
            if ("CHARACTER_REFERENCE".equals(reference.getAssetType())) currentCharacters.add(reference);
        }
        Long sceneId = keyframe.getSceneId() != null
                ? keyframe.getSceneId() : currentScene == null ? null : currentScene.getSceneId();
        String sceneAssetCode = sceneId == null && currentScene != null
                ? currentScene.getAssetCode() : null;
        if (sceneId == null && (sceneAssetCode == null || sceneAssetCode.trim().isEmpty()))
        {
            throw new ServiceException("关键帧缺少场景身份，无法恢复自动匹配");
        }
        AiVideoAsset latestScene = assetMapper.selectLatestReferenceAssetVersion(
                keyframe.getProjectId(), "SCENE_REFERENCE", sceneId, null, sceneAssetCode);
        if (latestScene == null)
        {
            throw new ServiceException("当前场景没有可用的已批准参考图版本");
        }
        if (currentCharacters.size() > MAX_CHARACTER_REFERENCE_IMAGES)
        {
            throw new ServiceException("当前关键帧人物引用超过 " + MAX_CHARACTER_REFERENCE_IMAGES + " 张");
        }
        List<AiVideoAsset> latestCharacters = new ArrayList<>();
        for (AiVideoAsset currentCharacter : currentCharacters)
        {
            String assetCode = currentCharacter.getCharacterId() == null
                    ? currentCharacter.getAssetCode() : null;
            AiVideoAsset latestCharacter = assetMapper.selectLatestReferenceAssetVersion(
                    keyframe.getProjectId(), "CHARACTER_REFERENCE", null,
                    currentCharacter.getCharacterId(), assetCode);
            if (latestCharacter == null)
            {
                throw new ServiceException("人物“" + currentCharacter.getAssetName()
                        + "”没有可用的已批准参考图版本");
            }
            latestCharacters.add(latestCharacter);
        }
        return new KeyframeReferenceOverride(latestScene, latestCharacters);
    }

    private AiVideoAsset resolveAutomaticVideoKeyframe(AiVideoAsset video)
    {
        if (video.getShotId() == null)
        {
            throw new ServiceException("视频未关联分镜，无法恢复自动匹配");
        }
        for (AiVideoAsset candidate : assetMapper.selectKeyframeVersionsByShotId(
                video.getProjectId(), video.getShotId()))
        {
            if ("APPROVED".equals(candidate.getStatus())
                    && candidate.getObjectKey() != null && !candidate.getObjectKey().trim().isEmpty())
            {
                return candidate;
            }
        }
        throw new ServiceException("当前分镜没有可用的已批准关键帧版本");
    }

    private void validateVideoSourceKeyframe(AiVideoAsset video, AiVideoAsset keyframe)
    {
        if (keyframe == null || !"SHOT_KEYFRAME".equals(keyframe.getAssetType()))
        {
            throw new ServiceException("所选来源关键帧不存在或类型不正确");
        }
        if (!video.getProjectId().equals(keyframe.getProjectId())
                || video.getShotId() == null || !video.getShotId().equals(keyframe.getShotId()))
        {
            throw new ServiceException("视频只能绑定同一项目、同一分镜的关键帧版本");
        }
        validateApprovedKeyframe(keyframe);
    }

    private String metadataMode(String metadataJson, String fieldName, String defaultMode)
    {
        if (metadataJson == null || metadataJson.trim().isEmpty()) return defaultMode;
        try
        {
            String mode = objectMapper.readTree(metadataJson).path(fieldName).asText("")
                    .trim().toUpperCase(java.util.Locale.ROOT);
            return "MANUAL".equals(mode) || "AUTO".equals(mode) ? mode : defaultMode;
        }
        catch (Exception ignored)
        {
            return defaultMode;
        }
    }

    private KeyframeReferenceOverride resolveKeyframeReferenceOverride(AiVideoAsset source,
            AiVideoAssetRegenerationDraftRequest request)
    {
        boolean overrideRequested = request != null
                && (request.getSceneReferenceAssetId() != null
                        || request.getCharacterReferenceAssetIds() != null);
        if (!overrideRequested)
        {
            return null;
        }
        if (!"SHOT_KEYFRAME".equals(source.getAssetType()))
        {
            throw new ServiceException("只有分镜关键帧可以覆盖场景或人物参考图");
        }
        if (request.getSceneReferenceAssetId() == null)
        {
            throw new ServiceException("覆盖关键帧参考图时必须且只能选择1张场景参考图");
        }

        List<Long> characterAssetIds = request.getCharacterReferenceAssetIds();
        int characterCount = characterAssetIds == null ? 0 : characterAssetIds.size();
        if (characterCount > MAX_CHARACTER_REFERENCE_IMAGES)
        {
            throw new ServiceException("关键帧人物参考图最多选择 "
                    + MAX_CHARACTER_REFERENCE_IMAGES + " 张");
        }
        Set<Long> uniqueReferenceIds = new LinkedHashSet<>();
        Long sceneAssetId = request.getSceneReferenceAssetId();
        uniqueReferenceIds.add(sceneAssetId);
        AiVideoAsset sceneReference = lockAndValidateOverrideReference(
                source, sceneAssetId, "SCENE_REFERENCE", "场景");

        List<AiVideoAsset> characterReferences = new ArrayList<>(characterCount);
        if (characterAssetIds != null)
        {
            for (Long characterAssetId : characterAssetIds)
            {
                if (characterAssetId == null)
                {
                    throw new ServiceException("人物参考图ID不能为空");
                }
                if (!uniqueReferenceIds.add(characterAssetId))
                {
                    throw new ServiceException("关键帧参考图不能重复选择同一资产");
                }
                characterReferences.add(lockAndValidateOverrideReference(
                        source, characterAssetId, "CHARACTER_REFERENCE", "人物"));
            }
        }
        KeyframeReferenceOverride override = new KeyframeReferenceOverride(
                sceneReference, characterReferences);
        validateReferenceVersionIdentities(source, override);
        return override;
    }

    private void validateReferenceVersionIdentities(AiVideoAsset keyframe,
            KeyframeReferenceOverride binding)
    {
        if (keyframe.getSceneId() == null
                || !keyframe.getSceneId().equals(binding.getSceneReference().getSceneId()))
        {
            throw new ServiceException("场景参考图必须属于当前分镜的同一场景");
        }
        List<AiVideoAsset> currentCharacters = new ArrayList<>();
        for (AiVideoAsset current : assetRelationMapper
                .selectActiveReferenceAssetsByTargetAssetId(keyframe.getAssetId()))
        {
            if ("CHARACTER_REFERENCE".equals(current.getAssetType())) currentCharacters.add(current);
        }
        if (binding.getCharacterReferences().size() != currentCharacters.size())
        {
            throw new ServiceException("只能为当前分镜中的人物切换图片版本，不能增删人物");
        }
        List<AiVideoAsset> unmatchedCharacters = new ArrayList<>(currentCharacters);
        for (AiVideoAsset selected : binding.getCharacterReferences())
        {
            int matchedIndex = -1;
            for (int i = 0; i < unmatchedCharacters.size(); i++)
            {
                if (sameCharacterIdentity(unmatchedCharacters.get(i), selected))
                {
                    matchedIndex = i;
                    break;
                }
            }
            if (matchedIndex < 0)
            {
                throw new ServiceException("人物参考图只能切换为同一人物的其他版本");
            }
            unmatchedCharacters.remove(matchedIndex);
        }
    }

    private boolean sameCharacterIdentity(AiVideoAsset current, AiVideoAsset selected)
    {
        if (current.getCharacterId() != null || selected.getCharacterId() != null)
        {
            return current.getCharacterId() != null
                    && current.getCharacterId().equals(selected.getCharacterId());
        }
        return current.getAssetCode() != null
                && current.getAssetCode().equals(selected.getAssetCode());
    }

    private AiVideoAsset lockAndValidateOverrideReference(AiVideoAsset target,
            Long referenceAssetId, String expectedAssetType, String referenceLabel)
    {
        AiVideoAsset reference = assetMapper.selectAiVideoAssetByAssetIdForUpdate(referenceAssetId);
        if (reference == null)
        {
            throw new ServiceException(referenceLabel + "参考图不存在或已删除");
        }
        if (target.getProjectId() == null
                || !target.getProjectId().equals(reference.getProjectId()))
        {
            throw new ServiceException(referenceLabel + "参考图与关键帧不属于同一项目");
        }
        if (!expectedAssetType.equals(reference.getAssetType()))
        {
            throw new ServiceException("所选" + referenceLabel + "参考资产类型不正确");
        }
        if (!"APPROVED".equals(reference.getStatus()))
        {
            throw new ServiceException("所选" + referenceLabel + "参考图尚未批准");
        }
        if (reference.getObjectKey() == null || reference.getObjectKey().trim().isEmpty())
        {
            throw new ServiceException("所选" + referenceLabel + "参考图尚未生成或转存完成");
        }
        return reference;
    }

    private void insertKeyframeReferenceOverride(AiVideoAsset draft,
            KeyframeReferenceOverride referenceOverride)
    {
        int relationOrder = 0;
        for (AiVideoAsset characterReference : referenceOverride.getCharacterReferences())
        {
            insertReferenceRelation(draft.getProjectId(), characterReference.getAssetId(),
                    draft.getAssetId(), relationOrder++, "CHARACTER_REFERENCE");
        }
        // 按稳定的资产关系顺序保存人物与场景引用。
        insertReferenceRelation(draft.getProjectId(),
                referenceOverride.getSceneReference().getAssetId(), draft.getAssetId(),
                relationOrder, "SCENE_REFERENCE");
    }

    private void insertReferenceRelation(Long projectId, Long fromAssetId, Long toAssetId,
            int relationOrder, String referenceRole)
    {
        AiVideoAssetRelation relation = new AiVideoAssetRelation();
        relation.setProjectId(projectId);
        relation.setFromAssetId(fromAssetId);
        relation.setToAssetId(toAssetId);
        relation.setRelationType("REFERENCE_IMAGE");
        relation.setRelationOrder(relationOrder);
        relation.setMetadataJson("{\"referenceRole\":\"" + referenceRole + "\"}");
        if (assetRelationMapper.insertAiVideoAssetRelation(relation) < 1)
        {
            throw new ServiceException("关键帧参考图关系创建失败");
        }
    }

    private static final class KeyframeReferenceOverride
    {
        private final AiVideoAsset sceneReference;
        private final List<AiVideoAsset> characterReferences;

        private KeyframeReferenceOverride(AiVideoAsset sceneReference,
                List<AiVideoAsset> characterReferences)
        {
            this.sceneReference = sceneReference;
            this.characterReferences = characterReferences;
        }

        private AiVideoAsset getSceneReference()
        {
            return sceneReference;
        }

        private List<AiVideoAsset> getCharacterReferences()
        {
            return characterReferences;
        }
    }

    private void validateCurrentChapterShot(AiVideoAsset keyframe, AiVideoShot shot)
    {
        AiVideoChapter chapter = keyframe.getChapterId() == null
                ? null : chapterMapper.selectAiVideoChapterByChapterId(keyframe.getChapterId());
        if (chapter == null || chapter.getCurrentBibleVersion() == null || shot.getVersionNo() == null
                || !chapter.getCurrentBibleVersion().equals(shot.getVersionNo()))
        {
            throw new ServiceException("该关键帧属于旧版章节分析，请使用当前分析版本的关键帧生成视频");
        }
    }

    private Set<String> collectUnreferencedStoragePaths(AiVideoAsset asset)
    {
        Set<String> paths = new LinkedHashSet<>();
        addUnreferencedStoragePath(paths, asset, asset.getObjectKey());
        addUnreferencedStoragePath(paths, asset, asset.getPreviewObjectKey());
        return paths;
    }

    private void addUnreferencedStoragePath(Set<String> paths, AiVideoAsset asset, String storagePath)
    {
        if (storagePath != null && !storagePath.trim().isEmpty()
                && assetMapper.countOtherActiveAssetsByStoragePath(asset.getAssetId(), storagePath.trim()) == 0)
        {
            paths.add(storagePath.trim());
        }
    }

    private void scheduleStorageCleanupAfterCommit(final Set<String> storagePaths, final Long assetId)
    {
        if (storagePaths == null || storagePaths.isEmpty())
        {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
        {
            @Override
            public void afterCommit()
            {
                for (String storagePath : storagePaths)
                {
                    try
                    {
                        if (!assetStorage.delete(storagePath))
                        {
                            log.warn("AI视频资产OSS对象删除返回失败，assetId={}, path={}", assetId, storagePath);
                        }
                    }
                    catch (Exception ex)
                    {
                        log.warn("AI视频资产已软删，但OSS对象清理失败，assetId={}, path={}",
                                assetId, storagePath, ex);
                    }
                }
            }
        });
    }

    private boolean isActiveAssetStatus(String status)
    {
        return "GENERATING".equals(status) || "SUBMITTED".equals(status)
                || "PROCESSING".equals(status) || "VALIDATING".equals(status)
                || "QUEUED".equals(status) || "RUNNING".equals(status)
                || "WAITING_CALLBACK".equals(status) || "RETRYING".equals(status)
                || "NEEDS_REVIEW".equals(status) || "QUALITY_CHECK".equals(status);
    }

    private JsonNode parseShotPromptContext(AiVideoShot shot)
    {
        if (shot.getPromptContextJson() == null || shot.getPromptContextJson().trim().isEmpty())
        {
            throw new ServiceException("分镜缺少 Python 已最终化的提示词上下文");
        }
        try
        {
            JsonNode context = objectMapper.readTree(shot.getPromptContextJson());
            if (context == null || !context.isObject())
            {
                throw new ServiceException("分镜提示词上下文必须是 JSON 对象");
            }
            return context;
        }
        catch (ServiceException ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            throw new ServiceException("分镜提示词上下文 JSON 无法解析")
                    .setDetailMessage(ex.getMessage());
        }
    }

    private String requirePromptContextText(JsonNode context, String fieldName, String displayName)
    {
        JsonNode value = context.get(fieldName);
        if (value == null || !value.isTextual() || value.asText().trim().isEmpty())
        {
            throw new ServiceException("分镜缺少 Python 已最终化的" + displayName);
        }
        return value.asText();
    }

    private String optionalPromptContextText(JsonNode context, String fieldName)
    {
        JsonNode value = context.get(fieldName);
        if (value == null || value.isNull())
        {
            return null;
        }
        if (!value.isTextual())
        {
            throw new ServiceException("分镜 " + fieldName + " 必须是文本");
        }
        return value.asText();
    }

    private void validateImagePrompt(String promptText)
    {
        if (promptText == null || promptText.trim().isEmpty())
        {
            throw new ServiceException("图片正向提示词不能为空");
        }
    }

    private void validateVideoPrompt(String promptText, Integer durationMs)
    {
        validateVideoDuration(durationMs);
        if (promptText == null || promptText.trim().isEmpty())
        {
            throw new ServiceException("视频正向提示词不能为空");
        }
    }

    private void validateVideoDuration(Integer durationMs)
    {
        if (durationMs == null || durationMs.intValue() <= 0)
        {
            throw new ServiceException("视频时长必须大于 0 毫秒");
        }
    }

    private String buildVideoGenerationParamsJson(Integer durationMs)
    {
        return AiVideoJsonMetadata.videoGenerationParameters(videoGenerationService.providerCode(),
                videoGenerationService.modelCode(), durationMs, null);
    }

    private String buildImageRequestJson(AiVideoAsset asset, List<Long> referenceAssetIds)
    {
        String aspectRatio = resolveImageAspectRatio(asset);
        return AiVideoJsonMetadata.imageGenerationRequest(asset.getPromptText(), asset.getNegativePromptText(),
                modelConfigService.getRequiredConfig().getImageModel(), asset.getAssetType(), aspectRatio,
                referenceAssetIds);
    }

    private String buildImageGenerationParamsJson(AiVideoAsset asset)
    {
        String aspectRatio = resolveImageAspectRatio(asset);
        return AiVideoJsonMetadata.generationParameters("dashscope",
                modelConfigService.getRequiredConfig().getImageModel(),
                aspectRatio);
    }

    private String resolveImageAspectRatio(AiVideoAsset asset)
    {
        AiVideoProject project = projectService.selectAiVideoProjectByProjectId(asset.getProjectId());
        if (project == null || project.getDefaultAspectRatio() == null)
        {
            return null;
        }
        String aspectRatio = project.getDefaultAspectRatio().trim();
        return aspectRatio.isEmpty() ? null : aspectRatio;
    }
}
