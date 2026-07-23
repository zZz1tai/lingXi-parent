package com.lingXi.aiVedio.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.lingXi.ai.client.VideoClient;
import com.lingXi.aiVedio.config.AiVideoModelConfigService;
import com.lingXi.aiVedio.domain.AiVideoAsset;
import com.lingXi.aiVedio.domain.dto.AiVideoModelConfig;
import com.lingXi.aiVedio.domain.AiVideoGenerationTask;
import com.lingXi.aiVedio.mapper.AiVideoAssetMapper;
import com.lingXi.aiVedio.mapper.AiVideoGenerationTaskMapper;
import com.lingXi.aiVedio.util.AiVideoImageAspectRatioPolicy;
import com.lingXi.aiVedio.util.AiVideoJsonMetadata;
import com.lingXi.aiVedio.service.AiVideoImageReferenceService.ResolvedImageReferences;

/**
 * Qwen图片资产服务，创建图片资产草稿，并在用户确认后执行Qwen Image生成。
 */
@Service
public class AiVideoQwenAssetService
{
    @Autowired
    private AiVideoAssetMapper assetMapper;
    @Autowired
    private AiVideoGenerationTaskMapper taskMapper;
    @Autowired
    private VideoClient videoClient;
    @Autowired
    private AiVideoImageCompletionService imageCompletionService;
    @Autowired
    private AiVideoImageReferenceService imageReferenceService;
    @Autowired
    private AiVideoModelConfigService modelConfigService;

    /**
     * 创建图片资产草稿（不含人物归属和上游参考资产）。
     * 章节分析阶段只保存图片资产和提示词，不创建生成任务，也不调用图片模型。
     *
     * @param projectId 项目ID
     * @param chapterId 章节ID
     * @param sceneId 场景ID
     * @param shotId 镜头ID
     * @param assetCode 资产编码
     * @param assetName 资产名称
     * @param assetType 资产类型
     * @param assetScope 资产作用域
     * @param canonicalFlag 规范标记
     * @param versionNo 版本号
     * @param prompt 正向提示词
     * @param negativePrompt 反向提示词
     * @param metadataJson 元数据JSON
     * @return 创建的草稿资产
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
     *
     * @param projectId 项目ID
     * @param chapterId 章节ID
     * @param sceneId 场景ID
     * @param shotId 镜头ID
     * @param assetCode 资产编码
     * @param assetName 资产名称
     * @param assetType 资产类型
     * @param assetScope 资产作用域
     * @param canonicalFlag 规范标记
     * @param versionNo 版本号
     * @param prompt 正向提示词
     * @param negativePrompt 反向提示词
     * @param metadataJson 元数据JSON
     * @param characterId 人物ID
     * @param sourceAssetId 上游来源资产ID
     * @return 创建的草稿资产
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
        asset.setGenerationParamsJson(AiVideoJsonMetadata.generationParameters(
                "dashscope", modelConfigService.getRequiredConfig().getImageModel()));
        asset.setMetadataJson(metadataJson);
        asset.setCreateBy("ai-video-worker");
        assetMapper.insertAiVideoAsset(asset);
        return asset;
    }

    /**
     * 获取或创建项目级人物参考图资产。
     * 一个项目中的同一人物只由一个活动CHARACTER_REFERENCE代表。
     * 查询同时兼容旧chapter资产编码和metadata.characterCode，并将旧资产提升为项目级资产。
     *
     * @param projectId 项目ID
     * @param characterId 人物ID
     * @param characterCode 人物编码
     * @param characterName 人物名称
     * @param prompt 正向提示词
     * @param negativePrompt 反向提示词
     * @param metadataJson 元数据JSON
     * @return 人物参考图资产
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

    /**
     * 执行已经由队列原子领取的图片生成任务。
     *
     * @param task 生成任务
     * @param asset 图片资产
     * @param updateBy 操作人
     */
    public void generateClaimedImage(AiVideoGenerationTask task, AiVideoAsset asset, String updateBy)
    {
        try
        {
            ResolvedImageReferences references = imageReferenceService.resolveAndValidate(asset);
            AiVideoModelConfig runtimeConfig = modelConfigService.getRequiredConfig();
            String aspectRatio = AiVideoImageAspectRatioPolicy.resolve(
                    asset.getAssetType(), runtimeConfig.getVideoRatio());
            String imageModel = runtimeConfig.getImageModel();
            String generationPrompt = resolveGenerationPrompt(asset);
            String generationNegativePrompt = resolveGenerationNegativePrompt(asset);
            String requestJson = AiVideoJsonMetadata.imageGenerationRequest(generationPrompt,
                    generationNegativePrompt, imageModel, asset.getAssetType(), aspectRatio,
                    references.getAssetIds());
            if (taskMapper.updateClaimedImageTaskRequest(
                    task.getTaskId(), requestJson, imageModel) != 1)
            {
                throw new IllegalStateException("图片任务状态已变化，拒绝调用图片模型");
            }
            
            // 统一通过 Python Agent 调用图片模型，Java 侧只负责业务参数和结果落库。
            VideoClient.ImageResult result = videoClient.generateImage(
                    runtimeConfig.getApiKey(),
                    imageModel,
                    runtimeConfig.getWorkspaceBaseUrl(),
                    asset.getAssetType(),
                    generationPrompt,
                    generationNegativePrompt,
                    aspectRatio,
                    references.getImageUrls());
            
            if (!result.success())
            {
                String errorCode = result.errorCode() == null ? "IMAGE_GENERATION_FAILED"
                        : result.errorCode();
                String detail = result.error() == null ? "图片生成失败" : result.error();
                String message = errorCode + "：" + detail;
                if (result.retryable())
                {
                    message = "图片服务暂时不可用，请检查提示词后手动重试：" + message;
                }
                markImageFailure(task, asset, updateBy,
                        result.retryable() ? "QWEN_IMAGE_MANUAL_RETRY_REQUIRED" : errorCode,
                        message);
                return;
            }
            
            String imageUrl = result.imageUrl();
            try
            {
                imageCompletionService.complete(task, asset, imageUrl, updateBy);
            }
            catch (Exception storageEx)
            {
                markImageFailure(task, asset, updateBy,
                        "IMAGE_STORAGE_FAILED", storageEx.getMessage());
            }
        }
        catch (Exception ex)
        {
            markImageFailure(task, asset, updateBy,
                    "QWEN_IMAGE_GENERATION_FAILED", ex.getMessage());
        }
    }

    /**
     * 解析图片生成提示词，人物参考图会追加规范性约束后缀。
     *
     * @param asset 图片资产
     * @return 生成用提示词
     */
    private String resolveGenerationPrompt(AiVideoAsset asset)
    {
        String prompt = asset.getPromptText() == null ? "" : asset.getPromptText().trim();
        if (!"CHARACTER_REFERENCE".equals(asset.getAssetType()))
        {
            return prompt;
        }
        return prompt + ". Strict clean character turnaround only: exactly the same single person in front, "
                + "side and back views on a plain studio background. Empty relaxed hands. No story action, "
                + "no environment, no scenery, no furniture, no weapon, no prop, no held object, no blood, "
                + "no wounds, no magic, no glow, no smoke and no visual effects.";
    }

    /**
     * 解析图片生成反向提示词，人物参考图会追加防护性约束。
     *
     * @param asset 图片资产
     * @return 生成用反向提示词
     */
    private String resolveGenerationNegativePrompt(AiVideoAsset asset)
    {
        String negative = asset.getNegativePromptText() == null ? "" : asset.getNegativePromptText().trim();
        if (!"CHARACTER_REFERENCE".equals(asset.getAssetType()))
        {
            return negative;
        }
        String guardrail = "weapon, sword, gun, tool, held object, blood, wound, injured hands, glowing hands, "
                + "magic, energy effect, smoke, fire, story action, environment, scenery, furniture, vehicle";
        return negative.isEmpty() ? guardrail : negative + ", " + guardrail;
    }

    /**
     * 标记图片生成失败，更新资产和任务状态。
     *
     * @param task 生成任务
     * @param asset 图片资产
     * @param updateBy 操作人
     * @param errorCode 错误码
     * @param message 错误消息
     */
    private void markImageFailure(AiVideoGenerationTask task, AiVideoAsset asset,
            String updateBy, String errorCode, String message)
    {
        String detail = message == null || message.trim().isEmpty() ? "图片生成失败" : message;
        asset.setMetadataJson(AiVideoJsonMetadata.generationFailure(
                asset.getMetadataJson(), detail));
        asset.setUpdateBy(updateBy);
        assetMapper.markAiVideoAssetFailed(asset);
        taskMapper.failImageTaskIfExpectedStatus(task.getTaskId(), "RUNNING", errorCode, detail);
    }
}
