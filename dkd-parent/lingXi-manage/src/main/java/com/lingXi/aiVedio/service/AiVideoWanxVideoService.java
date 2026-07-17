package com.lingXi.aiVedio.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lingXi.ai.client.VideoClient;
import com.lingXi.ai.config.AgentConfig;
import com.lingXi.ai.config.DashScopeConfig;
import com.lingXi.aiVedio.domain.AiVideoAsset;
import com.lingXi.aiVedio.domain.AiVideoGenerationTask;
import com.lingXi.aiVedio.mapper.AiVideoAssetMapper;
import com.lingXi.aiVedio.mapper.AiVideoGenerationTaskMapper;
import com.lingXi.aiVedio.storage.AiVideoPublicAssetUrlResolver;
import com.lingXi.aiVedio.util.AiVideoJsonMetadata;
import com.lingXi.common.exception.ServiceException;

/** 将人工确认后的视频提示词草稿提交到 Wanx。 */
@Service
public class AiVideoWanxVideoService
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
    private DashScopeConfig dashScopeConfig;
    @Autowired
    private AgentConfig agentConfig;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * 先在一个短事务内原子完成草稿状态迁移和任务创建，事务提交后才调用外部服务。
     * 这样并发确认不会重复提交，外部请求失败时任务和草稿也能落为可重试状态。
     */
    public Long submit(final AiVideoAsset video, final AiVideoAsset keyframe, final String username)
    {
        // Validate duration
        if (video.getDurationMs() == null || video.getDurationMs().intValue() <= 0
                || video.getDurationMs().intValue() > MAX_VIDEO_DURATION_MS)
        {
            throw new ServiceException("单条视频时长必须在 1 到 " + MAX_VIDEO_DURATION_MS + " 毫秒之间");
        }
        final Integer normalizedDurationMs = normalizeDurationMs(video.getDurationMs());
        if (!normalizedDurationMs.equals(video.getDurationMs()))
        {
            throw new ServiceException("当前 Wanx 模型不支持该视频时长，请先保存模型支持的时长后再生成");
        }
        final String referenceUrl = publicAssetUrlResolver.resolve(keyframe.getObjectKey());
        final String taskRequestJson = buildTaskRequestJson(video, keyframe, username, referenceUrl);

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        AiVideoGenerationTask task = transaction.execute(status -> prepareTask(video, username, taskRequestJson));
        if (task == null || task.getTaskId() == null)
        {
            throw new ServiceException("视频生成任务创建失败");
        }
        video.setStatus("GENERATING");

        // Call Python Agent API for video submission
        VideoClient.VideoSubmitResult result = videoClient.submitVideo(
                agentConfig.getLlmApiKey(),
                dashScopeConfig.getVideoModel(),
                video.getPromptText(),
                video.getNegativePromptText(),
                referenceUrl,
                dashScopeConfig.getVideoResolution(),
                normalizedDurationMs,
                false);
        
        if (!result.success())
        {
            // Check if error indicates uncertainty (ambiguous submission)
            String error = result.error();
            if (error != null && (error.contains("uncertain") || error.contains("UNCERTAIN")
                    || (result.statusCode() != null && result.statusCode() >= 500)))
            {
                taskMapper.updateAiVideoGenerationTaskStatus(task.getTaskId(), "NEEDS_REVIEW", 20,
                        "WANX_VIDEO_SUBMISSION_UNCERTAIN", error);
                throw new ServiceException("Wanx 提交结果不确定，请勿重复生成，等待人工核对")
                        .setDetailMessage(error);
            }
            
            markDefinitiveSubmissionFailure(video, task, username, error);
            throw new ServiceException("Wanx 图生视频提交失败").setDetailMessage(error);
        }
        
        String providerTaskId = result.taskId();
        if (taskMapper.markWanxVideoTaskWaiting(task.getTaskId(), providerTaskId) != 1)
        {
            taskMapper.markWanxVideoTaskNeedsReviewWithProviderId(task.getTaskId(), providerTaskId,
                    "WANX_VIDEO_LOCAL_STATE_UNCERTAIN",
                    "Wanx 已返回任务ID，但本地等待状态更新失败；请勿重复提交");
            throw new ServiceException("Wanx 已接收任务，但本地状态待核对，请勿重复生成");
        }
        return task.getTaskId();
    }
    
    // ── Constants and Helpers ────────────────────────────────────────────────
    
    private static final int MAX_VIDEO_DURATION_MS = 10000;
    
    /**
     * Model-specific duration normalization.
     */
    private Integer normalizeDurationMs(Integer durationMs) {
        String model = dashScopeConfig.getVideoModel();
        if (model == null) {
            return Math.min(durationMs, MAX_VIDEO_DURATION_MS);
        }
        
        String modelLower = model.toLowerCase();
        int durationS = durationMs / 1000;
        
        if (modelLower.contains("2.1") && modelLower.contains("turbo")) {
            durationS = Math.max(3, Math.min(5, durationS));
        } else if (modelLower.contains("2.1") || modelLower.contains("2.2")) {
            durationS = 5; // Fixed duration
        } else if (modelLower.contains("2.5")) {
            durationS = durationS <= 7 ? 5 : 10;
        } else if (modelLower.contains("2.6")) {
            durationS = Math.max(2, Math.min(10, durationS));
        } else {
            durationS = Math.min(10, durationS);
        }
        
        return durationS * 1000;
    }

    private void markDefinitiveSubmissionFailure(final AiVideoAsset video,
            final AiVideoGenerationTask task, final String username, final String message)
    {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.execute(status -> {
            video.setMetadataJson(AiVideoJsonMetadata.generationFailure(video.getMetadataJson(), message));
            video.setUpdateBy(username);
            if (assetMapper.markAiVideoAssetFailed(video) != 1)
            {
                throw new IllegalStateException("视频资产失败状态更新失败");
            }
            if (taskMapper.failQueuedWanxVideoTask(task.getTaskId(),
                    "WANX_VIDEO_SUBMIT_FAILED", message) != 1)
            {
                throw new IllegalStateException("视频任务失败状态更新失败");
            }
            return null;
        });
    }

    private AiVideoGenerationTask prepareTask(AiVideoAsset video, String username, String requestJson)
    {
        if (assetMapper.markEditableVideoAssetGenerating(video.getAssetId(), video.getPromptText(),
                video.getNegativePromptText(), video.getDurationMs(),
                AiVideoJsonMetadata.videoGenerationParameters("wanx", dashScopeConfig.getVideoModel(),
                        video.getDurationMs(), AiVideoVideoPromptComposer.PROMPT_VERSION),
                username) != 1)
        {
            throw new ServiceException("视频草稿或提示词已变化，请刷新确认后重试");
        }
        AiVideoGenerationTask task = new AiVideoGenerationTask();
        task.setProjectId(video.getProjectId());
        task.setChapterId(video.getChapterId());
        task.setAssetId(video.getAssetId());
        task.setTaskType("VIDEO");
        task.setTaskName("Wanx图生视频：" + video.getAssetName());
        task.setStatus("QUEUED");
        task.setPriority(100);
        task.setIdempotencyKey("wanx-video-" + video.getAssetId() + "-" + System.currentTimeMillis());
        task.setProviderCode("wanx");
        task.setModelCode(dashScopeConfig.getVideoModel());
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

    private String buildTaskRequestJson(AiVideoAsset video, AiVideoAsset keyframe, String username,
            String referenceUrl)
    {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("trigger", "USER_CONFIRMED");
        request.put("confirmedBy", username == null ? "" : username);
        request.put("provider", "wanx");
        request.put("model", dashScopeConfig.getVideoModel());
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
        request.put("referenceImageUrl", referenceUrl);
        return request.toString();
    }

    private void putLong(ObjectNode node, String field, Long value)
    {
        if (value != null)
        {
            node.put(field, value.longValue());
        }
    }
}
