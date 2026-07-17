package com.lingXi.aiVedio.domain.dto;

import java.util.List;
import lombok.Data;

/** 创建资产新版本草稿时可选的关键帧参考图覆盖。 */
@Data
public class AiVideoAssetRegenerationDraftRequest
{
    /** 新关键帧使用的唯一场景参考图；传入覆盖参数时必填。 */
    private Long sceneReferenceAssetId;

    /** 新关键帧使用的0至2张人物参考图；顺序会保留为 Qwen 多图输入顺序。 */
    private List<Long> characterReferenceAssetIds;
}
