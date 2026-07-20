package com.lingXi.aiVedio.domain.dto;

import java.util.List;
import lombok.Data;

/** 修改分镜关键帧使用的具体人物与场景参考图版本。 */
@Data
public class AiVideoKeyframeReferenceBindingRequest
{
    /** MANUAL 表示人工选择；AUTO 由独立的自动重绑接口处理。 */
    private String mode;
    private Long sceneReferenceAssetId;
    private List<Long> characterReferenceAssetIds;
}
