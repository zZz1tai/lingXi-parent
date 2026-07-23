package com.lingXi.aiVedio.domain.dto;

import lombok.Data;

/**
 * 视频关键帧绑定请求对象。
 * <p>用于修改视频草稿生成时所使用的具体关键帧资产版本，
 * 以便切换不同的视觉参考来源。</p>
 */
@Data
public class AiVideoVideoSourceBindingRequest
{
    /** 关键帧资产ID，视频将基于该关键帧版本生成 */
    private Long keyframeAssetId;
}
