package com.lingXi.aiVedio.service;

import java.util.List;
import com.lingXi.aiVedio.domain.AiVideoAsset;

/** 视频生成供应商适配接口；业务层只依赖此接口，不绑定具体模型品牌。 */
public interface AiVideoGenerationService
{
    String providerCode();

    String modelCode();

    Long submit(AiVideoAsset video, AiVideoAsset keyframe,
            List<AiVideoAsset> boundReferenceAssets, String username);
}
