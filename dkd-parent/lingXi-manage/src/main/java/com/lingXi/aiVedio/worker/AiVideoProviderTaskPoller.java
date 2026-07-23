package com.lingXi.aiVedio.worker;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.lingXi.ai.client.VideoClient;
import com.lingXi.ai.client.VideoClient.VideoQueryResult;
import com.lingXi.aiVedio.config.AiVideoModelConfigService;
import com.lingXi.aiVedio.domain.AiVideoAsset;
import com.lingXi.aiVedio.domain.AiVideoGenerationTask;
import com.lingXi.aiVedio.domain.dto.AiVideoModelConfig;
import com.lingXi.aiVedio.mapper.AiVideoAssetMapper;
import com.lingXi.aiVedio.mapper.AiVideoGenerationTaskMapper;
import com.lingXi.aiVedio.storage.AiVideoLocalAssetStorage;
import com.lingXi.aiVedio.util.AiVideoJsonMetadata;
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
    private AiVideoAssetMapper assetMapper;
    @Autowired
    private VideoClient videoClient;
    @Autowired
    private AiVideoModelConfigService modelConfigService;
    @Autowired
    private AiVideoLocalAssetStorage localAssetStorage;
    @Autowired
    private PlatformTransactionManager transactionManager;

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
            if (taskMapper.claimVideoProviderTask(task.getTaskId(), providerCode) != 1)
            {
                continue;
            }
            try
            {
                VideoQueryResult result = videoClient.queryVideo(
                        runtimeConfig.getApiKey(), runtimeConfig.getWorkspaceBaseUrl(),
                        task.getProviderTaskId());
                
                if (!result.success())
                {
                    throw new IllegalStateException("查询视频任务失败：" + result.error());
                }
                
                String status = result.status() != null ? result.status() : "UNKNOWN";
                
                if ("SUCCEEDED".equals(status))
                {
                    if (result.videoUrl() == null || result.videoUrl().trim().isEmpty())
                    {
                        throw new IllegalStateException("视频供应商任务已成功但未返回视频地址");
                    }
                    complete(task, result.videoUrl(), providerCode);
                }
                else if ("FAILED".equals(status) || "CANCELED".equals(status))
                {
                    fail(task, result.error(), providerCode);
                }
                else
                {
                    taskMapper.updateClaimedVideoProviderTaskStatus(
                            task.getTaskId(), providerCode, "WAITING_CALLBACK", 40, null, null);
                }
            }
            catch (Exception ex)
            {
                taskMapper.updateClaimedVideoProviderTaskStatus(
                        task.getTaskId(), providerCode, "WAITING_CALLBACK", 40,
                        "VIDEO_PROVIDER_POLL_ERROR", ex.getMessage());
            }
        }
    }

    /**
     * 处理视频生成成功的结果，下载视频并更新资产状态。
     *
     * @param task         生成任务实体
     * @param videoUrl     视频下载地址
     * @param providerCode 供应商编码
     * @throws Exception 处理失败时抛出异常
     */
    private void complete(AiVideoGenerationTask task, String videoUrl,
            String providerCode) throws Exception
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
                throw new IllegalStateException("视频资产状态已变化，无法登记供应商结果");
            }
            if (taskMapper.updateClaimedVideoProviderTaskStatus(
                    task.getTaskId(), providerCode,
                    "SUCCEEDED", 100, null, null) != 1)
            {
                throw new IllegalStateException("视频任务完成状态更新失败");
            }
            return null;
        });
    }

    /**
     * 处理视频生成失败的结果，更新资产和任务状态。
     *
     * @param task         生成任务实体
     * @param message      错误信息
     * @param providerCode 供应商编码
     */
    private void fail(AiVideoGenerationTask task, String message, String providerCode)
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
                throw new IllegalStateException("视频资产状态已变化，无法登记供应商失败结果");
            }
            if (taskMapper.updateClaimedVideoProviderTaskStatus(
                    task.getTaskId(), providerCode, "FAILED", 100,
                    "VIDEO_PROVIDER_TASK_FAILED", message) != 1)
            {
                throw new IllegalStateException("视频供应商任务失败状态更新失败");
            }
            return null;
        });
    }

}
