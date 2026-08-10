package com.lingXi.aiVedio.worker;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.lingXi.ai.client.VideoClient;
import com.lingXi.ai.client.VideoClient.VideoQueryResult;
import com.lingXi.aiVedio.config.AiVideoModelConfigService;
import com.lingXi.aiVedio.domain.AiVideoGenerationTask;
import com.lingXi.aiVedio.domain.dto.AiVideoModelConfig;
import com.lingXi.aiVedio.domain.enums.AiVideoTaskStatus;
import com.lingXi.aiVedio.mapper.AiVideoGenerationTaskMapper;
import com.lingXi.aiVedio.service.AiVideoProviderTaskOutcomeService;
import com.lingXi.aiVedio.util.AiVideoWorkerIdentity;
import com.lingXi.common.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;

/** 持久化轮询异步视频供应商任务，并转存视频片段。 */
@Slf4j
@Component
public class AiVideoProviderTaskPoller
{
    @Autowired
    private AiVideoGenerationTaskMapper taskMapper;
    @Autowired
    private VideoClient videoClient;
    @Autowired
    private AiVideoModelConfigService modelConfigService;
    @Autowired
    private AiVideoProviderTaskOutcomeService outcomeService;

    /**
     * 定时轮询视频供应商异步任务状态并处理结果。
     */
    @Scheduled(fixedDelayString = "${aivideo.video.poll-interval-ms:15000}")
    public void poll()
    {
        AiVideoModelConfig runtimeConfig;
        try
        {
            runtimeConfig = modelConfigService.getRequiredConfig();
        }
        catch (ServiceException ex)
        {
            log.debug("AI 模型配置未完成，跳过视频供应商任务轮询：{}", ex.getMessage());
            return;
        }
        String providerCode = runtimeConfig.getVideoProvider();
        taskMapper.markStaleVideoProviderSubmissionsNeedsReview(providerCode);
        taskMapper.recoverStaleVideoProviderSubmissionsWithProviderId(providerCode);
        taskMapper.releaseStaleClaimedVideoProviderTasks(providerCode);
        List<AiVideoGenerationTask> tasks = taskMapper.selectWaitingVideoProviderTasks(providerCode);
        for (AiVideoGenerationTask task : tasks)
        {
            if (taskMapper.claimVideoProviderTask(task.getTaskId(), providerCode,
                    AiVideoWorkerIdentity.WORKER_ID, AiVideoWorkerIdentity.DEFAULT_LEASE_SECONDS) != 1)
            {
                continue;
            }
            try
            {
                taskMapper.renewTaskLease(task.getTaskId(), AiVideoWorkerIdentity.WORKER_ID,
                        AiVideoWorkerIdentity.DEFAULT_LEASE_SECONDS);
                VideoQueryResult result = videoClient.queryVideo(
                        runtimeConfig.getApiKey(), runtimeConfig.getWorkspaceBaseUrl(),
                        task.getProviderTaskId());
                
                if (!result.success())
                {
                    throw new IllegalStateException("查询视频任务失败：" + result.error());
                }
                
                String status = result.status() != null ? result.status() : "UNKNOWN";
                
                if (AiVideoTaskStatus.SUCCEEDED.is(status))
                {
                    if (result.videoUrl() == null || result.videoUrl().trim().isEmpty())
                    {
                        throw new IllegalStateException("视频供应商任务已成功但未返回视频地址");
                    }
                    outcomeService.complete(task, result.videoUrl(), providerCode, "ai-video-poller");
                }
                else if (AiVideoTaskStatus.FAILED.is(status) || AiVideoTaskStatus.CANCELED.is(status))
                {
                    outcomeService.fail(task, result.error(), providerCode, "ai-video-poller");
                }
                else
                {
                    taskMapper.updateClaimedVideoProviderTaskStatus(
                            task.getTaskId(), providerCode, AiVideoTaskStatus.WAITING_CALLBACK.name(), 40, null, null);
                }
            }
            catch (Exception ex)
            {
                taskMapper.updateClaimedVideoProviderTaskStatus(
                        task.getTaskId(), providerCode, AiVideoTaskStatus.WAITING_CALLBACK.name(), 40,
                        "VIDEO_PROVIDER_POLL_ERROR", ex.getMessage());
            }
        }
    }
}
