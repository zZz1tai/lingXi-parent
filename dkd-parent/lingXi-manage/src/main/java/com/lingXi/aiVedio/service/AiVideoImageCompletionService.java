package com.lingXi.aiVedio.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.lingXi.aiVedio.domain.AiVideoAsset;
import com.lingXi.aiVedio.domain.AiVideoGenerationTask;
import com.lingXi.aiVedio.mapper.AiVideoAssetMapper;
import com.lingXi.aiVedio.mapper.AiVideoGenerationTaskMapper;
import com.lingXi.aiVedio.storage.AiVideoLocalAssetStorage;

/** 将模型临时图片转存到默认文件存储平台，并完成资产与任务。 */
@Service
public class AiVideoImageCompletionService
{
    @Autowired
    private AiVideoAssetMapper assetMapper;
    @Autowired
    private AiVideoGenerationTaskMapper taskMapper;
    @Autowired
    private AiVideoLocalAssetStorage localAssetStorage;

    @Transactional(rollbackFor = Exception.class)
    public void complete(AiVideoGenerationTask task, AiVideoAsset asset, String imageUrl, String updateBy) throws Exception
    {
        AiVideoLocalAssetStorage.StoredImage stored = localAssetStorage.store(
                asset.getProjectId(), asset.getAssetId(), asset.getVersionNo(), asset.getAssetCode(), imageUrl);
        asset.setStorageProvider(stored.getPlatform());
        asset.setObjectKey(stored.getResourcePath());
        asset.setPreviewObjectKey(stored.getResourcePath());
        asset.setMimeType("image/png");
        asset.setFileSize(stored.getSize());
        asset.setContentHash(stored.getSha256());
        asset.setWidth(stored.getWidth());
        asset.setHeight(stored.getHeight());
        asset.setUpdateBy(updateBy);
        if (assetMapper.markAiVideoAssetGenerated(asset) != 1)
        {
            throw new IllegalStateException("图片资产状态已变化，拒绝覆盖生成结果");
        }
        if (taskMapper.updateAiVideoGenerationTaskStatus(task.getTaskId(), "SUCCEEDED", 100, null, null) != 1)
        {
            throw new IllegalStateException("图片任务状态更新失败，拒绝提交资产结果");
        }
    }
}
