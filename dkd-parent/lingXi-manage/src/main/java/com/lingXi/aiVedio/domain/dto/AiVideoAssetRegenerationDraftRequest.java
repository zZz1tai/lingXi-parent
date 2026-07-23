package com.lingXi.aiVedio.domain.dto;

import java.util.List;
import lombok.Data;

/**
 * 资产再生草稿请求对象。
 * <p>用于创建资产新版本草稿时，可选地覆盖关键帧的场景和人物参考图，
 * 或切换到同一分镜的另一个关键帧版本。</p>
 */
@Data
public class AiVideoAssetRegenerationDraftRequest
{
    /** 新关键帧使用的场景参考图资产ID；传入覆盖参数时必填 */
    private Long sceneReferenceAssetId;

    /** 新关键帧使用的人物参考图资产ID列表；顺序会保存到资产关系中 */
    private List<Long> characterReferenceAssetIds;

    /** 视频新版本可显式切换到同一分镜的另一个关键帧资产ID */
    private Long keyframeAssetId;
}
