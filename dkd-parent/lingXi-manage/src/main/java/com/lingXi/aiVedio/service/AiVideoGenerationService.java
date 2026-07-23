package com.lingXi.aiVedio.service;

import java.util.List;
import com.lingXi.aiVedio.domain.AiVideoAsset;

/**
 * 视频生成供应商适配接口，业务层只依赖此接口，不绑定具体模型品牌。
 */
public interface AiVideoGenerationService
{
    /**
     * 获取供应商编码。
     *
     * @return 供应商编码
     */
    String providerCode();

    /**
     * 获取模型编码。
     *
     * @return 模型编码
     */
    String modelCode();

    /**
     * 提交视频生成任务。
     *
     * @param video 视频资产
     * @param keyframe 关键帧资产
     * @param boundReferenceAssets 绑定的参考图资产列表
     * @param username 操作用户
     * @return 生成任务ID
     */
    Long submit(AiVideoAsset video, AiVideoAsset keyframe,
            List<AiVideoAsset> boundReferenceAssets, String username);
}
