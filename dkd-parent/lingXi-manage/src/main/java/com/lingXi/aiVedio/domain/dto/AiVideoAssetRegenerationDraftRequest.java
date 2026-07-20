package com.lingXi.aiVedio.domain.dto;

import java.util.List;
import lombok.Data;

/** 创建资产新版本草稿时可选的关键帧参考图覆盖。 */
@Data
public class AiVideoAssetRegenerationDraftRequest
{
    /** 新关键帧使用的唯一场景参考图；传入覆盖参数时必填。 */
    private Long sceneReferenceAssetId;

    /** 新关键帧使用的人物参考图；顺序会保存到资产关系中。 */
    private List<Long> characterReferenceAssetIds;

    /** 视频新版本可显式切换到同一分镜的另一个关键帧版本。 */
    private Long keyframeAssetId;
}
