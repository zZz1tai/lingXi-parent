package com.lingXi.aiVedio.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.lingXi.aiVedio.domain.AiVideoAsset;
import com.lingXi.aiVedio.domain.AiVideoGenerationTask;
import com.lingXi.aiVedio.domain.enums.AiVideoTaskStatus;
import com.lingXi.aiVedio.mapper.AiVideoAssetMapper;
import com.lingXi.aiVedio.mapper.AiVideoGenerationTaskMapper;
import com.lingXi.aiVedio.storage.AiVideoLocalAssetStorage;
import com.lingXi.aiVedio.util.AiVideoJsonMetadata;

/**
 * 视频供应商任务结果处理服务。
 * <p>轮询器与供应商回调共用：下载视频转存到存储平台，更新资产和任务状态；
 * 任务必须已处于 RUNNING（已领取）状态才会推进，保证回调与轮询并发安全。</p>
 */
@Service
public class AiVideoProviderTaskOutcomeService
{
    @Autowired
    private AiVideoGenerationTaskMapper taskMapper;
    @Autowired
    private AiVideoAssetMapper assetMapper;
    @Autowired
    private AiVideoLocalAssetStorage localAssetStorage;
    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * 处理视频生成成功的结果，下载视频并更新资产状态。
     *
     * @param task         生成任务实体
     * @param videoUrl     视频下载地址
     * @param providerCode 供应商编码
     * @param updateBy     操作人
     * @throws Exception 处理失败时抛出异常
     */
    public void complete(AiVideoGenerationTask task, String videoUrl,
            String providerCode, String updateBy) throws Exception
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
        asset.setUpdateBy(updateBy);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.execute(status -> {
            if (assetMapper.markAiVideoAssetGenerated(asset) != 1)
            {
                throw new IllegalStateException("视频资产状态已变化，无法登记供应商结果");
            }
            if (taskMapper.updateClaimedVideoProviderTaskStatus(
                    task.getTaskId(), providerCode,
                    AiVideoTaskStatus.SUCCEEDED.name(), 100, null, null) != 1)
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
     * @param updateBy     操作人
     */
    public void fail(AiVideoGenerationTask task, String message,
            String providerCode, String updateBy)
    {
        AiVideoAsset asset = assetMapper.selectAiVideoAssetByAssetId(task.getAssetId());
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.execute(status -> {
            if (asset == null)
            {
                throw new IllegalStateException("视频任务关联资产不存在");
            }
            asset.setMetadataJson(AiVideoJsonMetadata.generationFailure(asset.getMetadataJson(), message));
            asset.setUpdateBy(updateBy);
            if (assetMapper.markAiVideoAssetFailed(asset) != 1)
            {
                throw new IllegalStateException("视频资产状态已变化，无法登记供应商失败结果");
            }
            if (taskMapper.updateClaimedVideoProviderTaskStatus(
                    task.getTaskId(), providerCode, AiVideoTaskStatus.FAILED.name(), 100,
                    "VIDEO_PROVIDER_TASK_FAILED", message) != 1)
            {
                throw new IllegalStateException("视频供应商任务失败状态更新失败");
            }
            return null;
        });
    }
}
