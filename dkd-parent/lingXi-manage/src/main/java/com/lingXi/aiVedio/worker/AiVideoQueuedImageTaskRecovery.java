package com.lingXi.aiVedio.worker;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.lingXi.aiVedio.domain.AiVideoAsset;
import com.lingXi.aiVedio.domain.AiVideoGenerationTask;
import com.lingXi.aiVedio.mapper.AiVideoAssetMapper;
import com.lingXi.aiVedio.mapper.AiVideoGenerationTaskMapper;
import com.lingXi.aiVedio.service.AiVideoQwenAssetService;
import com.lingXi.aiVedio.util.AiVideoCharacterPrompt;
import com.lingXi.aiVedio.util.AiVideoJsonMetadata;

/** 串行领取用户确认过的图片任务；异常中断任务只转人工重试，不自动再次调用模型。 */
@Component
public class AiVideoQueuedImageTaskRecovery
{
    @Autowired
    private AiVideoGenerationTaskMapper taskMapper;
    @Autowired
    private AiVideoAssetMapper assetMapper;
    @Autowired
    private AiVideoQwenAssetService qwenAssetService;

    @Scheduled(fixedDelayString = "${aivideo.image.queued-recovery-interval-ms}")
    public void recover()
    {
        assetMapper.rejectOrphanedGeneratingAssets();
        List<AiVideoGenerationTask> tasks = taskMapper.selectQueuedImageTasksForRecovery();
        for (AiVideoGenerationTask task : tasks)
        {
            if (!AiVideoJsonMetadata.isUserConfirmedImageRequest(task.getRequestJson()))
            {
                failWithoutGeneration(task, "IMAGE_CONFIRMATION_MISSING",
                        "旧图片任务缺少人工确认凭证，请检查提示词后手动生成");
                continue;
            }
            AiVideoAsset queuedAsset = assetMapper.selectAiVideoAssetByAssetId(task.getAssetId());
            if (queuedAsset != null
                    && AiVideoCharacterPrompt.isCharacterReference(queuedAsset.getAssetType())
                    && !AiVideoJsonMetadata.hasCharacterThreeViewConstraint(task.getRequestJson()))
            {
                failWithoutGeneration(task, "CHARACTER_THREE_VIEW_RECONFIRM_REQUIRED",
                        "人物图片规范已升级为正面、侧面、背面三视图，请重新查看提示词并手动确认生成");
                continue;
            }
            if (!"QUEUED".equals(task.getStatus()))
            {
                failWithoutGeneration(task, "IMAGE_MANUAL_RETRY_REQUIRED",
                        "上次图片生成未正常结束，请检查提示词后手动重试");
                continue;
            }
            if (taskMapper.claimImageTask(task.getTaskId(), "QUEUED") != 1)
            {
                continue;
            }

            AiVideoAsset asset = assetMapper.selectAiVideoAssetByAssetId(task.getAssetId());
            if (asset == null)
            {
                taskMapper.failImageTaskIfExpectedStatus(task.getTaskId(), "RUNNING",
                        "IMAGE_ASSET_NOT_FOUND", "图片任务关联资产不存在");
                continue;
            }
            if (!"GENERATING".equals(asset.getStatus()))
            {
                taskMapper.failImageTaskIfExpectedStatus(task.getTaskId(), "RUNNING",
                        "IMAGE_ASSET_STATE_INVALID", "图片资产不在生成状态");
                continue;
            }
            try
            {
                qwenAssetService.generateClaimedImage(task, asset, "ai-video-image-queue");
            }
            catch (Exception ex)
            {
                asset.setMetadataJson(AiVideoJsonMetadata.generationFailure(
                        asset.getMetadataJson(), ex.getMessage()));
                asset.setUpdateBy("ai-video-image-queue");
                assetMapper.markAiVideoAssetFailed(asset);
                taskMapper.failImageTaskIfExpectedStatus(task.getTaskId(), "RUNNING",
                        "IMAGE_QUEUE_EXECUTION_FAILED", ex.getMessage());
            }
        }
    }

    private void failWithoutGeneration(AiVideoGenerationTask task, String errorCode, String message)
    {
        if (taskMapper.failImageTaskIfExpectedStatus(
                task.getTaskId(), task.getStatus(), errorCode, message) != 1)
        {
            return;
        }
        AiVideoAsset asset = assetMapper.selectAiVideoAssetByAssetId(task.getAssetId());
        if (asset == null || !"GENERATING".equals(asset.getStatus()))
        {
            return;
        }
        asset.setMetadataJson(AiVideoJsonMetadata.generationFailure(
                asset.getMetadataJson(), message));
        asset.setUpdateBy("ai-video-image-queue");
        assetMapper.markAiVideoAssetFailed(asset);
    }
}
