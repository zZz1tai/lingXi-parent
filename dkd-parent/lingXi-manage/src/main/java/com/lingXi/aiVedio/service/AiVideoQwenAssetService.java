package com.lingXi.aiVedio.service;

import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
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
import lombok.extern.slf4j.Slf4j;

/**
 * Qwen图片资产服务，创建图片资产草稿，并在用户确认后执行Qwen Image生成。
 */
@Service
@Slf4j
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
     * 由投递派发器调用，在异步线程中领取并执行图片生成任务。
     * <p>领取为条件更新防多实例重复执行；可重试错误按指数退避自动重试，
     * 超过上限或确定性错误落为终态失败，由用户手动重试。</p>
     *
     * @param taskId 图片生成任务ID
     */
    @Async("aiVideoExecutor")
    public void generateQueuedImage(Long taskId)
    {
        AiVideoGenerationTask task = taskMapper.selectAiVideoGenerationTaskByTaskId(taskId);
        if (task == null || !"IMAGE".equals(task.getTaskType())
                || !"dashscope".equals(task.getProviderCode()))
        {
            throw new IllegalStateException("图片任务不存在或类型无效，taskId=" + taskId);
        }
        if (!"QUEUED".equals(task.getStatus())
                && taskMapper.claimImageTask(taskId, "QUEUED") != 1)
        {
            log.info("图片任务已被其他执行者领取或状态已变化，跳过生成，taskId={}", taskId);
            return;
        }
        if (!AiVideoJsonMetadata.isUserConfirmedImageRequest(task.getRequestJson()))
        {
            markImageFailure(task, null, "ai-video-outbox",
                    "IMAGE_CONFIRMATION_MISSING",
                    "图片任务缺少人工确认凭证，请检查提示词后手动生成");
            return;
        }
        AiVideoAsset asset = assetMapper.selectAiVideoAssetByAssetId(task.getAssetId());
        if (asset == null)
        {
            taskMapper.failImageTaskIfExpectedStatus(taskId, "RUNNING",
                    "IMAGE_ASSET_NOT_FOUND", "图片任务关联资产不存在");
            return;
        }
        if (!"GENERATING".equals(asset.getStatus()))
        {
            taskMapper.failImageTaskIfExpectedStatus(taskId, "RUNNING",
                    "IMAGE_ASSET_STATE_INVALID", "图片资产不在生成状态");
            return;
        }

        try
        {
            generateClaimedImageWithRequest(task, asset);
        }
        catch (Exception ex)
        {
            log.error("图片任务生成中断，taskId={}, errorType={}",
                    taskId, ex.getClass().getSimpleName());
            scheduleImageRetry(task, ex.getMessage());
        }
    }

    /**
     * 执行已经由队列原子领取的图片生成任务。
     * 统一通过 Python Agent 调用图片模型，Java 侧只负责业务参数和结果落库。
     *
     * @param task 生成任务
     * @param asset 图片资产
     */
    private void generateClaimedImageWithRequest(AiVideoGenerationTask task, AiVideoAsset asset)
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
            if (result.retryable())
            {
                scheduleImageRetry(task, errorCode + "：" + detail);
                return;
            }
            markImageFailure(task, asset, "ai-video-outbox", errorCode, detail);
            return;
        }

        String imageUrl = result.imageUrl();
        try
        {
            imageCompletionService.complete(task, asset, imageUrl, "ai-video-outbox");
        }
        catch (Exception storageEx)
        {
            log.error("图片转存失败，taskId={}, errorType={}",
                    task.getTaskId(), storageEx.getClass().getSimpleName());
            markImageFailure(task, asset, "ai-video-outbox",
                    "IMAGE_STORAGE_FAILED", storageEx.getMessage());
        }
    }

    /**
     * 按指数退避安排图片任务自动重试，超过上限后标记失败并解锁资产。
     *
     * @param task        图片生成任务
     * @param errorMessage 失败原因
     */
    private void scheduleImageRetry(AiVideoGenerationTask task, String errorMessage)
    {
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        int maxRetry = task.getMaxRetry() == null ? 3 : task.getMaxRetry();
        if (retryCount >= maxRetry)
        {
            AiVideoAsset asset = assetMapper.selectAiVideoAssetByAssetId(task.getAssetId());
            markImageFailure(task, asset, "ai-video-outbox",
                    "QWEN_IMAGE_SUBMIT_FAILED",
                    "自动重试次数超限，请检查提示词后手动重试：" + truncate(errorMessage));
            return;
        }
        long delayMinutes = 1L << retryCount;
        int updated = taskMapper.retryClaimedImageTask(task.getTaskId(), retryCount + 1,
                new Date(System.currentTimeMillis() + delayMinutes * 60_000L),
                "QWEN_IMAGE_SUBMIT_TRANSIENT", truncate(errorMessage));
        if (updated != 1)
        {
            AiVideoAsset asset = assetMapper.selectAiVideoAssetByAssetId(task.getAssetId());
            markImageFailure(task, asset, "ai-video-outbox",
                    "QWEN_IMAGE_LOCAL_STATE_UNCERTAIN",
                    "图片任务自动重试状态更新失败，请人工核对");
            return;
        }
        log.warn("图片任务将自动重试，taskId={}, retryCount={}, delayMinutes={}",
                task.getTaskId(), retryCount + 1, delayMinutes);
    }

    /**
     * 截断过长的错误信息，避免超出字段长度。
     *
     * @param message 错误信息
     * @return 截断后的错误信息
     */
    private static String truncate(String message)
    {
        if (message == null)
        {
            return "image provider generate failed";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
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
        if (asset != null)
        {
            asset.setMetadataJson(AiVideoJsonMetadata.generationFailure(
                    asset.getMetadataJson(), detail));
            asset.setUpdateBy(updateBy);
            assetMapper.markAiVideoAssetFailed(asset);
        }
        taskMapper.failImageTaskIfExpectedStatus(task.getTaskId(), "RUNNING", errorCode, detail);
    }
}
