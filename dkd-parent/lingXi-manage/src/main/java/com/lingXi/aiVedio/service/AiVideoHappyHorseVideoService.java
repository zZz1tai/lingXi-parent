package com.lingXi.aiVedio.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
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
import com.lingXi.aiVedio.domain.enums.AiVideoTaskStatus;
import com.lingXi.aiVedio.mapper.AiVideoAssetMapper;
import com.lingXi.aiVedio.mapper.AiVideoGenerationTaskMapper;
import com.lingXi.aiVedio.outbox.AiVideoTaskOutboxPublisher;
import com.lingXi.aiVedio.storage.AiVideoPublicAssetUrlResolver;
import com.lingXi.aiVedio.util.AiVideoJsonMetadata;
import com.lingXi.aiVedio.util.AiVideoReferenceImagePolicy;
import com.lingXi.common.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;

/**
 * HappyHorse多参考图视频供应商适配器。
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "aivideo.video", name = "provider",
        havingValue = "happyhorse", matchIfMissing = true)
public class AiVideoHappyHorseVideoService implements AiVideoGenerationService
{
    /** 外部提交失败后的最大自动重试次数。 */
    private static final int MAX_SUBMIT_RETRY = 3;

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
    @Autowired
    private AiVideoTaskOutboxPublisher outboxPublisher;

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
     * 事务内完成资产状态迁移、任务创建和投递事件写入后立即返回；
     * 供应商外呼由派发器在任务线程中执行，HTTP 请求线程不再阻塞最长 60 秒。
     *
     * @param video 视频资产
     * @param keyframe 可选关键帧资产；文生视频时为空
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
        validateReferenceImages(keyframe, boundReferenceAssets);
        final String referenceUrl = keyframe == null
                ? null : publicAssetUrlResolver.resolve(keyframe.getObjectKey());
        final VideoReferenceUrls referenceUrls = resolveReferenceUrls(boundReferenceAssets);
        final String taskRequestJson = buildTaskRequestJson(video, keyframe, username,
                referenceUrl, referenceUrls, runtimeConfig.getVideoModel());

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        AiVideoGenerationTask task = transaction.execute(status -> {
            AiVideoGenerationTask created = prepareTask(
                    video, username, taskRequestJson, runtimeConfig.getVideoModel());
            outboxPublisher.publish(created.getTaskId(),
                    AiVideoTaskOutboxPublisher.EVENT_TASK_CREATED);
            return created;
        });
        if (task == null || task.getTaskId() == null)
        {
            throw new ServiceException("视频生成任务创建失败");
        }
        video.setStatus("GENERATING");
        return task.getTaskId();
    }

    /**
     * 由投递派发器调用，把排队中的视频任务提交给供应商。
     * <p>从任务表和请求 JSON 重建外呼参数，成功后进入等待回调状态；
     * 提交被供应商明确拒绝时按确定性失败处理；网络等异常按指数退避自动重试。
     * 在独立异步线程执行外部提交，避免阻塞派发扫描。</p>
     *
     * @param taskId 视频生成任务ID
     */
    @Async("aiVideoExecutor")
    public void submitQueuedVideoTask(Long taskId)
    {
        AiVideoGenerationTask task = taskMapper.selectAiVideoGenerationTaskByTaskId(taskId);
        if (task == null || !"VIDEO".equals(task.getTaskType()))
        {
            throw new IllegalStateException("视频任务不存在或类型无效，taskId=" + taskId);
        }
        if (!AiVideoTaskStatus.QUEUED.is(task.getStatus())
                && taskMapper.claimQueuedVideoTaskForSubmission(taskId, providerCode()) != 1)
        {
            log.info("视频任务已被其他执行者领取或状态已变化，跳过提交，taskId={}", taskId);
            return;
        }
        AiVideoAsset video = assetMapper.selectAiVideoAssetByAssetId(task.getAssetId());
        if (video == null)
        {
            taskMapper.failClaimedVideoTask(taskId, providerCode(),
                    "VIDEO_ASSET_NOT_FOUND", "视频任务关联资产不存在");
            return;
        }
        if (!"GENERATING".equals(video.getStatus()))
        {
            taskMapper.markClaimedVideoTaskNeedsReview(taskId, providerCode(),
                    "VIDEO_ASSET_STATE_INVALID", "视频资产不在生成状态，请人工核对");
            return;
        }

        try
        {
            AiVideoModelConfig runtimeConfig = modelConfigService.getRequiredConfig();
            JsonNode request = objectMapper.readTree(task.getRequestJson());
            VideoClient.VideoSubmitResult result = videoClient.submitVideo(
                    runtimeConfig.getApiKey(),
                    providerCode(),
                    runtimeConfig.getWorkspaceBaseUrl(),
                    firstNonBlank(request.path("model").asText(""), runtimeConfig.getVideoModel()),
                    request.path("prompt").asText(""),
                    request.path("negativePrompt").asText(""),
                    blankToNull(request.path("keyframeImageUrl").asText("")),
                    stringArray(request.path("characterReferenceImageUrls")),
                    blankToNull(request.path("sceneReferenceImageUrl").asText("")),
                    runtimeConfig.getVideoResolution(),
                    runtimeConfig.getVideoRatio(),
                    Boolean.TRUE.equals(runtimeConfig.getVideoWatermark()),
                    request.path("durationMs").asInt(),
                    task.getIdempotencyKey());
            handleSubmissionResult(task, video, result);
        }
        catch (Exception ex)
        {
            log.error("视频任务外部提交失败，taskId={}, errorType={}",
                    taskId, ex.getClass().getSimpleName());
            scheduleAutomaticRetry(task, ex.getMessage());
        }
    }

    /**
     * 按提交结果推进任务状态：成功进入等待回调，不确定转人工核对，明确失败则终态失败。
     *
     * @param task   视频生成任务
     * @param video  视频资产
     * @param result 供应商提交结果
     */
    private void handleSubmissionResult(final AiVideoGenerationTask task,
            final AiVideoAsset video, final VideoClient.VideoSubmitResult result)
    {
        if (!result.success())
        {
            String error = result.error() == null ? "视频提交失败" : result.error();
            if (result.submissionUncertain())
            {
                taskMapper.markClaimedVideoTaskNeedsReview(task.getTaskId(), providerCode(),
                        "VIDEO_PROVIDER_SUBMISSION_UNCERTAIN", error);
                log.warn("视频供应商提交结果不确定，转人工核对，taskId={}, error={}",
                        task.getTaskId(), error);
                return;
            }
            String agentErrorCode = result.errorCode() == null
                    || result.errorCode().trim().isEmpty()
                            ? "VIDEO_PROVIDER_SUBMIT_FAILED" : result.errorCode().trim();
            if (result.retryable() && isRetryAllowed(task))
            {
                scheduleAutomaticRetry(task, agentErrorCode + "：" + error);
                return;
            }
            String taskErrorCode = result.retryable()
                    ? "VIDEO_PROVIDER_MANUAL_RETRY_REQUIRED" : agentErrorCode;
            markDefinitiveSubmissionFailure(video, task,
                    "ai-video-outbox", taskErrorCode,
                    (result.retryable()
                            ? "视频服务暂时不可用，请手动重试：" : "")
                            + agentErrorCode + "：" + error);
            return;
        }

        String providerTaskId = result.taskId();
        Integer normalizedDurationMs = result.normalizedDurationMs();
        if (providerTaskId == null || providerTaskId.trim().isEmpty()
                || normalizedDurationMs == null || normalizedDurationMs.intValue() <= 0)
        {
            taskMapper.markClaimedVideoTaskNeedsReview(task.getTaskId(), providerCode(),
                    "VIDEO_PROVIDER_INVALID_AGENT_RESPONSE",
                    "Agent 未返回有效任务ID或归一化时长，请人工核对");
            return;
        }
        if (!finalizeSuccessfulSubmission(video, task, providerTaskId,
                normalizedDurationMs, firstNonBlank(task.getModelCode(), "video"), "ai-video-outbox"))
        {
            taskMapper.markClaimedVideoTaskNeedsReview(task.getTaskId(), providerCode(),
                    "VIDEO_PROVIDER_LOCAL_STATE_UNCERTAIN",
                    "视频供应商已返回任务ID，但本地等待状态更新失败；请勿重复提交");
        }
        video.setDurationMs(normalizedDurationMs);
    }

    /**
     * 按指数退避安排任务自动重试，超过上限后标记待人工处理。
     *
     * @param task        视频生成任务
     * @param errorMessage 失败原因
     */
    private void scheduleAutomaticRetry(AiVideoGenerationTask task, String errorMessage)
    {
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        if (retryCount >= MAX_SUBMIT_RETRY)
        {
            taskMapper.markClaimedVideoTaskNeedsReview(task.getTaskId(), providerCode(),
                    "VIDEO_PROVIDER_SUBMIT_FAILED",
                    "自动重试次数超限，请手动重试：" + truncate(errorMessage));
            return;
        }
        long delayMinutes = 1L << retryCount;
        int updated = taskMapper.retryClaimedVideoTask(task.getTaskId(), providerCode(),
                retryCount + 1,
                new java.util.Date(System.currentTimeMillis() + delayMinutes * 60_000L),
                "VIDEO_PROVIDER_SUBMIT_TRANSIENT", truncate(errorMessage));
        if (updated != 1)
        {
            taskMapper.markClaimedVideoTaskNeedsReview(task.getTaskId(), providerCode(),
                    "VIDEO_PROVIDER_LOCAL_STATE_UNCERTAIN",
                    "视频任务自动重试状态更新失败，请人工核对");
        }
        log.warn("视频任务将自动重试，taskId={}, retryCount={}, delayMinutes={}",
                task.getTaskId(), retryCount + 1, delayMinutes);
    }

    /**
     * 判断任务是否允许自动重试。
     *
     * @param task 视频生成任务
     * @return 是否允许
     */
    private boolean isRetryAllowed(AiVideoGenerationTask task)
    {
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        return retryCount < MAX_SUBMIT_RETRY;
    }

    /** 在产生外部任务费用前校验 HappyHorse 所有参考图的最低分辨率。 */
    void validateReferenceImages(final AiVideoAsset keyframe,
            final List<AiVideoAsset> boundReferenceAssets)
    {
        if (keyframe != null)
        {
            AiVideoReferenceImagePolicy.validateDimensions(
                    keyframe.getWidth(), keyframe.getHeight(), "起始关键帧");
        }
        if (boundReferenceAssets == null)
        {
            return;
        }
        for (int index = 0; index < boundReferenceAssets.size(); index++)
        {
            AiVideoAsset reference = boundReferenceAssets.get(index);
            if (reference != null)
            {
                AiVideoReferenceImagePolicy.validateDimensions(reference.getWidth(),
                        reference.getHeight(), "第" + (index + 1) + "张参考图片");
            }
        }
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
            if (taskMapper.markClaimedVideoTaskWaiting(
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
            if (taskMapper.failClaimedVideoTask(
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
        task.setStatus(AiVideoTaskStatus.QUEUED.name());
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
        if (keyframe != null)
        {
            putLong(request, "sourceAssetId", keyframe.getAssetId());
        }
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
        request.put("generationMode", keyframe == null ? "TEXT_TO_VIDEO" : "IMAGE_TO_VIDEO");
        if (keyframeUrl != null && !keyframeUrl.trim().isEmpty())
        {
            request.put("keyframeImageUrl", keyframeUrl);
            request.put("referenceImageUrl", keyframeUrl);
        }
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

    /**
     * 返回第一个非空字符串；全部为空时返回默认值。
     *
     * @param values 候选值
     * @return 选中的值
     */
    private static String firstNonBlank(String... values)
    {
        for (String value : values)
        {
            if (value != null && !value.trim().isEmpty())
            {
                return value;
            }
        }
        return values.length == 0 ? "" : values[values.length - 1];
    }

    /**
     * 空白字符串转为 null。
     *
     * @param value 原始值
     * @return 转换后的值
     */
    private static String blankToNull(String value)
    {
        return value == null || value.trim().isEmpty() ? null : value;
    }

    /**
     * 把 JSON 数组节点转为字符串列表。
     *
     * @param node JSON数组节点
     * @return 字符串列表，节点缺失或为空时返回空列表
     */
    private static List<String> stringArray(JsonNode node)
    {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray())
        {
            for (JsonNode item : node)
            {
                String value = blankToNull(item.asText(""));
                if (value != null)
                {
                    values.add(value);
                }
            }
        }
        return values;
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
            return "video provider submit failed";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
