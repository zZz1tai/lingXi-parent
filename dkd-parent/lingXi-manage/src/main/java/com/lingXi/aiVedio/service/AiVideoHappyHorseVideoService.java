package com.lingXi.aiVedio.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.ArrayNode;
import com.lingXi.ai.client.VideoClient;
import com.lingXi.aiVedio.config.AiVideoModelConfigService;
import com.lingXi.aiVedio.domain.AiVideoAsset;
import com.lingXi.aiVedio.domain.AiVideoGenerationTask;
import com.lingXi.aiVedio.domain.dto.AiVideoModelConfig;
import com.lingXi.aiVedio.mapper.AiVideoAssetMapper;
import com.lingXi.aiVedio.mapper.AiVideoGenerationTaskMapper;
import com.lingXi.aiVedio.storage.AiVideoPublicAssetUrlResolver;
import com.lingXi.aiVedio.util.AiVideoJsonMetadata;
import com.lingXi.common.exception.ServiceException;

/**
 * HappyHorse多参考图视频供应商适配器。
 */
@Service
@ConditionalOnProperty(prefix = "aivideo.video", name = "provider",
        havingValue = "happyhorse", matchIfMissing = true)
public class AiVideoHappyHorseVideoService implements AiVideoGenerationService
{
    @Autowired
    private AiVideoAssetMapper assetMapper;
    @Autowired
    private AiVideoGenerationTaskMapper taskMapper;
    @Autowired
    private VideoClient videoClient;
    @Autowired
    private AiVideoPublicAssetUrlResolver publicAssetUrlResolver;
    @Autowired
    private AiVideoModelConfigService modelConfigService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * 获取供应商编码。
     *
     * @return 供应商编码
     */
    @Override
    public String providerCode() { return "happyhorse"; }

    /**
     * 获取模型编码。
     *
     * @return 模型编码
     */
    @Override
    public String modelCode() { return modelConfigService.getRequiredConfig().getVideoModel(); }

    /**
     * 提交视频生成任务。
     * 先在一个短事务内原子完成草稿状态迁移和任务创建，事务提交后才调用外部服务。
     * 这样并发确认不会重复提交，外部请求失败时任务和草稿也能落为可重试状态。
     *
     * @param video 视频资产
     * @param keyframe 关键帧资产
     * @param boundReferenceAssets 绑定的参考图资产列表
     * @param username 操作用户
     * @return 生成任务ID
     */
    @Override
    public Long submit(final AiVideoAsset video, final AiVideoAsset keyframe,
            final List<AiVideoAsset> boundReferenceAssets, final String username)
    {
        final AiVideoModelConfig runtimeConfig = modelConfigService.getRequiredConfig();
        if (video.getDurationMs() == null || video.getDurationMs().intValue() <= 0)
        {
            throw new ServiceException("视频时长必须大于 0 毫秒");
        }
        final String referenceUrl = publicAssetUrlResolver.resolve(keyframe.getObjectKey());
        final VideoReferenceUrls referenceUrls = resolveReferenceUrls(boundReferenceAssets);
        final String taskRequestJson = buildTaskRequestJson(video, keyframe, username,
                referenceUrl, referenceUrls, runtimeConfig.getVideoModel());

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        AiVideoGenerationTask task = transaction.execute(status -> prepareTask(
                video, username, taskRequestJson, runtimeConfig.getVideoModel()));
        if (task == null || task.getTaskId() == null)
        {
            throw new ServiceException("视频生成任务创建失败");
        }
        video.setStatus("GENERATING");

        // 通过 Python Agent 提交视频任务，隔离具体模型提供方的协议差异。
        VideoClient.VideoSubmitResult result = videoClient.submitVideo(
                runtimeConfig.getApiKey(),
                providerCode(),
                runtimeConfig.getWorkspaceBaseUrl(),
                runtimeConfig.getVideoModel(),
                video.getPromptText(),
                video.getNegativePromptText(),
                referenceUrl,
                referenceUrls.getCharacterUrls(),
                referenceUrls.getSceneUrl(),
                runtimeConfig.getVideoResolution(),
                runtimeConfig.getVideoRatio(),
                Boolean.TRUE.equals(runtimeConfig.getVideoWatermark()),
                video.getDurationMs(),
                task.getIdempotencyKey());
        
        if (!result.success())
        {
            String error = result.error() == null ? "视频提交失败" : result.error();
            if (result.submissionUncertain())
            {
                taskMapper.updateAiVideoGenerationTaskStatus(task.getTaskId(), "NEEDS_REVIEW", 20,
                        "VIDEO_PROVIDER_SUBMISSION_UNCERTAIN", error);
                throw new ServiceException("视频供应商提交结果不确定，请勿重复生成，等待人工核对")
                        .setDetailMessage(error);
            }

            String agentErrorCode = result.errorCode() == null
                    || result.errorCode().trim().isEmpty()
                            ? "VIDEO_PROVIDER_SUBMIT_FAILED" : result.errorCode().trim();
            String taskErrorCode = result.retryable()
                    ? "VIDEO_PROVIDER_MANUAL_RETRY_REQUIRED" : agentErrorCode;
            String failureMessage = result.retryable()
                    ? "视频服务暂时不可用，请手动重试：" + agentErrorCode + "：" + error
                    : agentErrorCode + "：" + error;
            markDefinitiveSubmissionFailure(video, task, username,
                    taskErrorCode, failureMessage);
            throw new ServiceException(result.retryable()
                    ? "视频生成提交失败，可手动重试" : "视频生成提交失败")
                            .setDetailMessage(failureMessage);
        }
        
        String providerTaskId = result.taskId();
        Integer normalizedDurationMs = result.normalizedDurationMs();
        if (providerTaskId == null || providerTaskId.trim().isEmpty()
                || normalizedDurationMs == null || normalizedDurationMs.intValue() <= 0)
        {
            taskMapper.updateAiVideoGenerationTaskStatus(task.getTaskId(), "NEEDS_REVIEW", 20,
                    "VIDEO_PROVIDER_INVALID_AGENT_RESPONSE",
                    "Agent 未返回有效任务ID或归一化时长，请人工核对");
            throw new ServiceException("视频供应商已返回结果，但实际时长或任务ID无效，请人工核对");
        }
        if (!finalizeSuccessfulSubmission(video, task, providerTaskId,
                normalizedDurationMs, runtimeConfig.getVideoModel(), username))
        {
            taskMapper.markVideoProviderTaskNeedsReviewWithProviderId(
                    task.getTaskId(), providerCode(), providerTaskId,
                    "VIDEO_PROVIDER_LOCAL_STATE_UNCERTAIN",
                    "视频供应商已返回任务ID，但本地等待状态更新失败；请勿重复提交");
            throw new ServiceException("视频供应商已接收任务，但本地状态待核对，请勿重复生成");
        }
        video.setDurationMs(normalizedDurationMs);
        return task.getTaskId();
    }
    
    /**
     * 在事务提交后最终确认成功的提交结果，更新视频时长和任务等待状态。
     *
     * @param video 视频资产
     * @param task 生成任务
     * @param providerTaskId 供应商任务ID
     * @param normalizedDurationMs 归一化后的视频时长
     * @param videoModel 视频模型编码
     * @param username 操作用户
     * @return 是否确认成功
     */
    private boolean finalizeSuccessfulSubmission(final AiVideoAsset video,
            final AiVideoGenerationTask task, final String providerTaskId,
            final Integer normalizedDurationMs, final String videoModel, final String username)
    {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Boolean result = transaction.execute(status -> {
            if (!normalizedDurationMs.equals(video.getDurationMs())
                    && assetMapper.updateGeneratingVideoDuration(video.getAssetId(), normalizedDurationMs,
                            AiVideoJsonMetadata.videoGenerationParameters(providerCode(),
                                    videoModel, normalizedDurationMs, null),
                            username) != 1)
            {
                status.setRollbackOnly();
                return Boolean.FALSE;
            }
            if (taskMapper.markVideoProviderTaskWaiting(
                    task.getTaskId(), providerCode(), providerTaskId,
                    normalizedDurationMs) != 1)
            {
                status.setRollbackOnly();
                return Boolean.FALSE;
            }
            return Boolean.TRUE;
        });
        return Boolean.TRUE.equals(result);
    }

    /**
     * 标记视频提交为最终失败状态。
     *
     * @param video 视频资产
     * @param task 生成任务
     * @param username 操作用户
     * @param errorCode 错误码
     * @param message 错误消息
     */
    private void markDefinitiveSubmissionFailure(final AiVideoAsset video,
            final AiVideoGenerationTask task, final String username,
            final String errorCode, final String message)
    {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.execute(status -> {
            video.setMetadataJson(AiVideoJsonMetadata.generationFailure(video.getMetadataJson(), message));
            video.setUpdateBy(username);
            if (assetMapper.markAiVideoAssetFailed(video) != 1)
            {
                throw new IllegalStateException("视频资产失败状态更新失败");
            }
            if (taskMapper.failQueuedVideoProviderTask(
                    task.getTaskId(), providerCode(), errorCode, message) != 1)
            {
                throw new IllegalStateException("视频任务失败状态更新失败");
            }
            return null;
        });
    }

    /**
     * 准备视频生成任务，更新资产状态并创建任务记录。
     *
     * @param video 视频资产
     * @param username 操作用户
     * @param requestJson 请求JSON
     * @param videoModel 视频模型编码
     * @return 生成任务
     */
    private AiVideoGenerationTask prepareTask(AiVideoAsset video, String username,
            String requestJson, String videoModel)
    {
        if (assetMapper.markEditableVideoAssetGenerating(video.getAssetId(), video.getPromptText(),
                video.getNegativePromptText(), video.getDurationMs(),
                AiVideoJsonMetadata.videoGenerationParameters(providerCode(), videoModel,
                        video.getDurationMs(), null),
                username) != 1)
        {
            throw new ServiceException("视频草稿或提示词已变化，请刷新确认后重试");
        }
        AiVideoGenerationTask task = new AiVideoGenerationTask();
        task.setProjectId(video.getProjectId());
        task.setChapterId(video.getChapterId());
        task.setAssetId(video.getAssetId());
        task.setTaskType("VIDEO");
        task.setTaskName("视频生成：" + video.getAssetName());
        task.setStatus("QUEUED");
        task.setPriority(100);
        task.setIdempotencyKey(providerCode() + "-video-" + video.getAssetId()
                + "-" + System.currentTimeMillis());
        task.setProviderCode(providerCode());
        task.setModelCode(videoModel);
        task.setProgress(5);
        task.setMaxRetry(0);
        task.setRequestJson(requestJson);
        task.setCreateBy(username);
        if (taskMapper.insertAiVideoGenerationTask(task) != 1)
        {
            throw new ServiceException("视频生成任务创建失败");
        }
        return task;
    }

    /**
     * 构建视频生成任务的请求JSON。
     *
     * @param video 视频资产
     * @param keyframe 关键帧资产
     * @param username 操作用户
     * @param keyframeUrl 关键帧图片URL
     * @param referenceUrls 参考图URL集合
     * @param videoModel 视频模型编码
     * @return 请求JSON字符串
     */
    private String buildTaskRequestJson(AiVideoAsset video, AiVideoAsset keyframe, String username,
            String keyframeUrl, VideoReferenceUrls referenceUrls, String videoModel)
    {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("trigger", "USER_CONFIRMED");
        request.put("confirmedBy", username == null ? "" : username);
        request.put("provider", providerCode());
        request.put("model", videoModel);
        putLong(request, "videoAssetId", video.getAssetId());
        putLong(request, "sourceAssetId", keyframe.getAssetId());
        putLong(request, "projectId", video.getProjectId());
        putLong(request, "chapterId", video.getChapterId());
        putLong(request, "sceneId", video.getSceneId());
        putLong(request, "shotId", video.getShotId());
        request.put("prompt", video.getPromptText());
        if (video.getNegativePromptText() != null && !video.getNegativePromptText().trim().isEmpty())
        {
            request.put("negativePrompt", video.getNegativePromptText());
        }
        if (video.getDurationMs() != null)
        {
            request.put("durationMs", video.getDurationMs().intValue());
        }
        request.put("keyframeImageUrl", keyframeUrl);
        request.put("referenceImageUrl", keyframeUrl);
        ArrayNode characters = request.putArray("characterReferenceImageUrls");
        for (String characterUrl : referenceUrls.getCharacterUrls()) characters.add(characterUrl);
        ArrayNode characterAssetIds = request.putArray("characterReferenceAssetIds");
        for (Long characterAssetId : referenceUrls.getCharacterAssetIds())
        {
            characterAssetIds.add(characterAssetId.longValue());
        }
        if (referenceUrls.getSceneUrl() != null)
        {
            request.put("sceneReferenceImageUrl", referenceUrls.getSceneUrl());
            putLong(request, "sceneReferenceAssetId", referenceUrls.getSceneAssetId());
        }
        return request.toString();
    }

    /**
     * 解析参考图资产列表为URL和ID集合。
     *
     * @param references 参考图资产列表
     * @return 视频参考图URL集合
     */
    private VideoReferenceUrls resolveReferenceUrls(List<AiVideoAsset> references)
    {
        List<String> characterUrls = new ArrayList<>();
        List<Long> characterAssetIds = new ArrayList<>();
        String sceneUrl = null;
        Long sceneAssetId = null;
        if (references != null)
        {
            for (AiVideoAsset reference : references)
            {
                if (reference == null || reference.getObjectKey() == null) continue;
                String url = publicAssetUrlResolver.resolve(reference.getObjectKey());
                if ("CHARACTER_REFERENCE".equals(reference.getAssetType()))
                {
                    characterUrls.add(url);
                    characterAssetIds.add(reference.getAssetId());
                }
                if ("SCENE_REFERENCE".equals(reference.getAssetType()))
                {
                    sceneUrl = url;
                    sceneAssetId = reference.getAssetId();
                }
            }
        }
        return new VideoReferenceUrls(characterUrls, characterAssetIds, sceneUrl, sceneAssetId);
    }

    /**
     * 视频参考图URL和ID的聚合容器。
     */
    private static final class VideoReferenceUrls
    {
        private final List<String> characterUrls;
        private final List<Long> characterAssetIds;
        private final String sceneUrl;
        private final Long sceneAssetId;

        private VideoReferenceUrls(List<String> characterUrls, List<Long> characterAssetIds,
                String sceneUrl, Long sceneAssetId)
        {
            this.characterUrls = characterUrls;
            this.characterAssetIds = characterAssetIds;
            this.sceneUrl = sceneUrl;
            this.sceneAssetId = sceneAssetId;
        }

        private List<String> getCharacterUrls() { return characterUrls; }
        private List<Long> getCharacterAssetIds() { return characterAssetIds; }
        private String getSceneUrl() { return sceneUrl; }
        private Long getSceneAssetId() { return sceneAssetId; }
    }

    /**
     * 向JSON节点安全写入Long字段。
     *
     * @param node JSON对象节点
     * @param field 字段名
     * @param value 字段值
     */
    private void putLong(ObjectNode node, String field, Long value)
    {
        if (value != null)
        {
            node.put(field, value.longValue());
        }
    }
}
