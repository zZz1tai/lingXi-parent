package com.lingXi.aiVedio.worker;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.lingXi.ai.client.VideoClient;
import com.lingXi.ai.client.VideoClient.VideoQueryResult;
import com.lingXi.ai.config.DashScopeConfig;
import com.lingXi.aiVedio.domain.AiVideoAsset;
import com.lingXi.aiVedio.domain.AiVideoGenerationTask;
import com.lingXi.aiVedio.mapper.AiVideoAssetMapper;
import com.lingXi.aiVedio.mapper.AiVideoGenerationTaskMapper;
import com.lingXi.aiVedio.storage.AiVideoLocalAssetStorage;
import com.lingXi.aiVedio.util.AiVideoJsonMetadata;

/** 持久化轮询 Wanx 图生视频任务，并转存视频片段。 */
@Component
public class AiVideoWanxVideoTaskPoller
{
    @Autowired
    private AiVideoGenerationTaskMapper taskMapper;
    @Autowired
    private AiVideoAssetMapper assetMapper;
    @Autowired
    private VideoClient videoClient;
    @Autowired
    private DashScopeConfig dashScopeConfig;
    @Autowired
    private AiVideoLocalAssetStorage localAssetStorage;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Scheduled(fixedDelayString = "${aivideo.wanx.video-poll-interval-ms}")
    public void poll()
    {
        taskMapper.markStaleWanxSubmissionsNeedsReview();
        taskMapper.recoverStaleWanxSubmissionsWithProviderId();
        taskMapper.releaseStaleClaimedWanxVideoTasks();
        List<AiVideoGenerationTask> tasks = taskMapper.selectWaitingWanxVideoTasks();
        for (AiVideoGenerationTask task : tasks)
        {
            if (taskMapper.claimWanxVideoTask(task.getTaskId()) != 1)
            {
                continue;
            }
            try
            {
                VideoQueryResult result = videoClient.queryVideo(
                        dashScopeConfig.getApiKey(), task.getProviderTaskId());
                
                if (!result.success())
                {
                    throw new IllegalStateException("查询视频任务失败：" + result.error());
                }
                
                String status = result.status() != null ? result.status() : "UNKNOWN";
                
                if ("SUCCEEDED".equals(status))
                {
                    if (result.videoUrl() == null || result.videoUrl().trim().isEmpty())
                    {
                        throw new IllegalStateException("Wanx 任务已成功但未返回视频地址");
                    }
                    complete(task, result.videoUrl());
                }
                else if ("FAILED".equals(status) || "CANCELED".equals(status))
                {
                    fail(task, result.error());
                }
                else
                {
                    taskMapper.updateClaimedWanxVideoTaskStatus(
                            task.getTaskId(), "WAITING_CALLBACK", 40, null, null);
                }
            }
            catch (Exception ex)
            {
                taskMapper.updateClaimedWanxVideoTaskStatus(task.getTaskId(), "WAITING_CALLBACK", 40,
                        "WANX_VIDEO_POLL_ERROR", ex.getMessage());
            }
        }
    }

    private void complete(AiVideoGenerationTask task, String videoUrl) throws Exception
    {
        AiVideoAsset asset = assetMapper.selectAiVideoAssetByAssetId(task.getAssetId());
        if (asset == null) throw new IllegalStateException("视频任务关联资产不存在");
        AiVideoLocalAssetStorage.StoredFile stored = localAssetStorage.storeVideo(
                asset.getProjectId(), asset.getAssetId(), asset.getVersionNo(),
                asset.getAssetCode(), videoUrl);
        asset.setStorageProvider(stored.getPlatform());
        asset.setObjectKey(stored.getResourcePath());
        asset.setPreviewObjectKey(stored.getResourcePath());
        asset.setMimeType("video/mp4");
        asset.setFileSize(stored.getSize());
        asset.setContentHash(stored.getSha256());
        asset.setUpdateBy("ai-video-poller");
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.execute(status -> {
            if (assetMapper.markAiVideoAssetGenerated(asset) != 1)
            {
                throw new IllegalStateException("视频资产状态已变化，无法登记 Wanx 结果");
            }
            if (taskMapper.updateClaimedWanxVideoTaskStatus(
                    task.getTaskId(), "SUCCEEDED", 100, null, null) != 1)
            {
                throw new IllegalStateException("Wanx 视频任务完成状态更新失败");
            }
            return null;
        });
    }

    private void fail(AiVideoGenerationTask task, String message)
    {
        AiVideoAsset asset = assetMapper.selectAiVideoAssetByAssetId(task.getAssetId());
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.execute(status -> {
            if (asset == null)
            {
                throw new IllegalStateException("视频任务关联资产不存在");
            }
            asset.setMetadataJson(AiVideoJsonMetadata.generationFailure(asset.getMetadataJson(), message));
            asset.setUpdateBy("ai-video-poller");
            if (assetMapper.markAiVideoAssetFailed(asset) != 1)
            {
                throw new IllegalStateException("视频资产状态已变化，无法登记 Wanx 失败结果");
            }
            if (taskMapper.updateClaimedWanxVideoTaskStatus(task.getTaskId(), "FAILED", 100,
                    "WANX_VIDEO_TASK_FAILED", message) != 1)
            {
                throw new IllegalStateException("Wanx 视频任务失败状态更新失败");
            }
            return null;
        });
    }

}
