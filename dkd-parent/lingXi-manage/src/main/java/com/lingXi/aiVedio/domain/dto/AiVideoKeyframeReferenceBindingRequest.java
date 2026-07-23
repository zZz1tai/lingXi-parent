package com.lingXi.aiVedio.domain.dto;

import java.util.List;
import lombok.Data;

/**
 * 关键帧参考图绑定请求对象。
 * <p>用于修改分镜关键帧生成时所使用的具体人物参考图与场景参考图版本，
 * 支持手动选择和自动绑定两种模式。</p>
 */
@Data
public class AiVideoKeyframeReferenceBindingRequest
{
    /** 绑定模式：MANUAL-人工选择，AUTO-由独立的自动重绑接口处理 */
    private String mode;

    /** 场景参考图资产ID */
    private Long sceneReferenceAssetId;

    /** 人物参考图资产ID列表，顺序与人物出场顺序一致 */
    private List<Long> characterReferenceAssetIds;
}
