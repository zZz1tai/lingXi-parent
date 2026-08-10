package com.lingXi.aiVedio.worker;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.lingXi.aiVedio.domain.AiVideoAsset;
import com.lingXi.aiVedio.domain.AiVideoGenerationTask;
import com.lingXi.aiVedio.domain.enums.AiVideoTaskStatus;
import com.lingXi.aiVedio.mapper.AiVideoAssetMapper;
import com.lingXi.aiVedio.mapper.AiVideoGenerationTaskMapper;
import com.lingXi.aiVedio.outbox.AiVideoTaskOutboxPublisher;
import com.lingXi.aiVedio.util.AiVideoJsonMetadata;
import lombok.extern.slf4j.Slf4j;

/**
 * 图片任务恢复扫描器。
 * <p>图片生成经投递事件在异步线程执行，正常由派发器每 2 秒领取；
 * 此扫描器只兜底处理派发器漏掉的任务：滞留超过 60 秒的 QUEUED/RETRYING
 * 任务重新发布投递事件，运行中超过 5 分钟的任务判定为上次生成中断，
 * 转人工重试而不自动再次调用模型，避免产生重复生成费用。</p>
 */
@Component
@Slf4j
public class AiVideoQueuedImageTaskRecovery
{
    @Autowired
    private AiVideoGenerationTaskMapper taskMapper;
    @Autowired
    private AiVideoAssetMapper assetMapper;
    @Autowired
    private AiVideoTaskOutboxPublisher outboxPublisher;

    /**
     * 定时恢复滞留排队中的图片任务，处理异常中断的任务。
     */
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
            if (AiVideoTaskStatus.QUEUED.is(task.getStatus())
                    || AiVideoTaskStatus.RETRYING.is(task.getStatus()))
            {
                outboxPublisher.publish(task.getTaskId(),
                        AiVideoTaskOutboxPublisher.EVENT_TASK_RETRY);
            }
            else
            {
                failWithoutGeneration(task, "IMAGE_MANUAL_RETRY_REQUIRED",
                        "上次图片生成未正常结束，请检查提示词后手动重试");
            }
        }
        if (!tasks.isEmpty())
        {
            log.info("AI视频图片任务恢复扫描完成，处理数量={}", tasks.size());
        }
    }

    /**
     * 将无效或中断的图片任务标记为失败，不触发模型重新生成。
     *
     * @param task     生成任务实体
     * @param errorCode 错误码
     * @param message  错误信息
     */
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
