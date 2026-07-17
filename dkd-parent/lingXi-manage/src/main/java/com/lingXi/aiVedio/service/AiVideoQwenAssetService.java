package com.lingXi.aiVedio.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.lingXi.ai.config.DashScopeConfig;
import com.lingXi.aiVedio.domain.AiVideoAsset;
import com.lingXi.aiVedio.domain.AiVideoGenerationTask;
import com.lingXi.aiVedio.domain.AiVideoProject;
import com.lingXi.aiVedio.mapper.AiVideoAssetMapper;
import com.lingXi.aiVedio.mapper.AiVideoGenerationTaskMapper;
import com.lingXi.aiVedio.mapper.AiVideoProjectMapper;
import com.lingXi.aiVedio.provider.QwenImageClient;
import com.lingXi.aiVedio.util.AiVideoJsonMetadata;
import com.lingXi.aiVedio.util.AiVideoCharacterPrompt;
import com.lingXi.aiVedio.service.AiVideoImageReferenceService.ResolvedImageReferences;

/** 创建图片资产草稿，并在用户确认后执行 Qwen Image 生成。 */
@Service
public class AiVideoQwenAssetService
{
    @Autowired
    private AiVideoAssetMapper assetMapper;
    @Autowired
    private AiVideoGenerationTaskMapper taskMapper;
    @Autowired
    private AiVideoProjectMapper projectMapper;
    @Autowired
    private QwenImageClient qwenImageClient;
    @Autowired
    private AiVideoImageCompletionService imageCompletionService;
    @Autowired
    private AiVideoImageReferenceService imageReferenceService;
    @Autowired
    private DashScopeConfig dashScopeConfig;

    /**
     * 章节分析阶段只保存图片资产和提示词，不创建生成任务，也不调用图片模型。
     */
    @Transactional
    public AiVideoAsset createDraftImageAsset(Long projectId, Long chapterId, Long sceneId, Long shotId,
            String assetCode, String assetName, String assetType, String assetScope, Integer canonicalFlag,
            Integer versionNo, String prompt, String negativePrompt, String metadataJson)
    {
        return createDraftImageAsset(projectId, chapterId, sceneId, shotId, assetCode, assetName,
                assetType, assetScope, canonicalFlag, versionNo, prompt, negativePrompt, metadataJson,
                null, null);
    }

    /**
     * 创建可携带人物归属和上游参考资产的图片草稿。
     */
    @Transactional
    public AiVideoAsset createDraftImageAsset(Long projectId, Long chapterId, Long sceneId, Long shotId,
            String assetCode, String assetName, String assetType, String assetScope, Integer canonicalFlag,
            Integer versionNo, String prompt, String negativePrompt, String metadataJson,
            Long characterId, Long sourceAssetId)
    {
        Integer maxVersion = assetMapper.selectMaxAssetVersionForUpdate(projectId, assetCode);
        int resolvedVersion = versionNo == null || versionNo.intValue() < 1 ? 1 : versionNo.intValue();
        if (maxVersion != null)
        {
            if (maxVersion.intValue() == Integer.MAX_VALUE)
            {
                throw new IllegalStateException("资产版本号已达到上限：" + assetCode);
            }
            resolvedVersion = maxVersion.intValue() + 1;
        }
        AiVideoAsset asset = new AiVideoAsset();
        asset.setProjectId(projectId);
        asset.setChapterId(chapterId);
        asset.setSceneId(sceneId);
        asset.setShotId(shotId);
        asset.setCharacterId(characterId);
        asset.setAssetCode(assetCode);
        asset.setAssetName(assetName);
        asset.setAssetType(assetType);
        asset.setAssetScope(assetScope);
        asset.setCanonicalFlag(canonicalFlag);
        asset.setStatus("DRAFT");
        asset.setVersionNo(Integer.valueOf(resolvedVersion));
        asset.setSourceAssetId(sourceAssetId);
        asset.setPromptText(prompt);
        asset.setNegativePromptText(negativePrompt);
        boolean characterReference = AiVideoCharacterPrompt.isCharacterReference(assetType);
        asset.setGenerationParamsJson(AiVideoJsonMetadata.generationParameters(
                "dashscope", dashScopeConfig.getImageModel(),
                characterReference ? AiVideoCharacterPrompt.ASPECT_RATIO : null,
                characterReference ? AiVideoCharacterPrompt.IMAGE_SIZE : null,
                characterReference ? AiVideoCharacterPrompt.CONSTRAINT_VERSION : null));
        asset.setMetadataJson(metadataJson);
        asset.setCreateBy("ai-video-worker");
        assetMapper.insertAiVideoAsset(asset);
        return asset;
    }

    /**
     * 一个项目中的同一人物只由一个活动 CHARACTER_REFERENCE 代表。
     * 查询同时兼容旧 chapter 资产编码和 metadata.characterCode，并将旧资产提升为项目级资产。
     */
    @Transactional
    public AiVideoAsset getOrCreateProjectCharacterReference(Long projectId, Long characterId,
            String characterCode, String characterName, String prompt, String negativePrompt,
            String metadataJson)
    {
        AiVideoAsset existing = assetMapper.selectProjectCharacterReferenceForUpdate(
                projectId, characterId, characterCode);
        if (existing != null)
        {
            if (existing.getChapterId() != null || existing.getCharacterId() == null
                    || !characterId.equals(existing.getCharacterId())
                    || !"PROJECT".equals(existing.getAssetScope())
                    || existing.getCanonicalFlag() == null || existing.getCanonicalFlag().intValue() != 1)
            {
                assetMapper.promoteProjectCharacterReference(existing.getAssetId(), characterId, "ai-video-worker");
                existing.setChapterId(null);
                existing.setCharacterId(characterId);
                existing.setAssetScope("PROJECT");
                existing.setCanonicalFlag(1);
            }
            return existing;
        }
        return createDraftImageAsset(projectId, null, null, null,
                "character-" + characterCode, characterName + "角色三视图",
                "CHARACTER_REFERENCE", "PROJECT", 1, 1,
                prompt, negativePrompt, metadataJson, characterId, null);
    }

    /** 执行已经由队列原子领取的图片生成任务。 */
    public void generateClaimedImage(AiVideoGenerationTask task, AiVideoAsset asset, String updateBy)
    {
        try
        {
            AiVideoProject project = projectMapper.selectAiVideoProjectByProjectId(asset.getProjectId());
            boolean characterReference = AiVideoCharacterPrompt.isCharacterReference(asset.getAssetType());
            if (characterReference)
            {
                asset.setPromptText(AiVideoCharacterPrompt.ensureThreeViewPrompt(asset.getPromptText()));
                asset.setNegativePromptText(
                        AiVideoCharacterPrompt.ensureThreeViewNegativePrompt(asset.getNegativePromptText()));
            }
            String aspectRatio = characterReference
                    ? AiVideoCharacterPrompt.ASPECT_RATIO
                    : project == null || project.getDefaultAspectRatio() == null
                            ? "16:9" : project.getDefaultAspectRatio();
            ResolvedImageReferences references = imageReferenceService.resolveAndValidate(asset);
            String requestJson = AiVideoJsonMetadata.imageGenerationRequest(asset.getPromptText(),
                    asset.getNegativePromptText(), dashScopeConfig.getImageModel(), asset.getAssetType(), aspectRatio,
                    QwenImageClient.toImageSize(aspectRatio),
                    characterReference ? AiVideoCharacterPrompt.CONSTRAINT_VERSION : null,
                    references.getAssetIds());
            if (taskMapper.updateClaimedImageTaskRequest(
                    task.getTaskId(), requestJson, dashScopeConfig.getImageModel()) != 1)
            {
                throw new IllegalStateException("图片任务状态已变化，拒绝调用图片模型");
            }
            String imageUrl = qwenImageClient.generate(asset.getPromptText(), asset.getNegativePromptText(),
                    aspectRatio, references.getImageUrls());
            try
            {
                imageCompletionService.complete(task, asset, imageUrl, updateBy);
            }
            catch (Exception storageEx)
            {
                asset.setMetadataJson(AiVideoJsonMetadata.generationFailure(
                        asset.getMetadataJson(), storageEx.getMessage()));
                asset.setUpdateBy(updateBy);
                assetMapper.markAiVideoAssetFailed(asset);
                taskMapper.failImageTaskIfExpectedStatus(task.getTaskId(), "RUNNING",
                        "IMAGE_STORAGE_FAILED", storageEx.getMessage());
            }
        }
        catch (Exception ex)
        {
            asset.setMetadataJson(AiVideoJsonMetadata.generationFailure(
                    asset.getMetadataJson(), ex.getMessage()));
            asset.setUpdateBy(updateBy);
            assetMapper.markAiVideoAssetFailed(asset);
            boolean retryable = QwenImageClient.isRetryable(ex);
            taskMapper.failImageTaskIfExpectedStatus(task.getTaskId(), "RUNNING",
                    retryable ? "QWEN_IMAGE_MANUAL_RETRY_REQUIRED" : "QWEN_IMAGE_GENERATION_FAILED",
                    retryable ? "图片服务繁忙或连接中断，请检查提示词后手动重试：" + ex.getMessage() : ex.getMessage());
        }
    }
}
