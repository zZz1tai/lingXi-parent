package com.lingXi.aiVedio.domain.dto;

import lombok.Data;

/**
 * 视频草稿提示词修改请求对象。
 * <p>用于修改视频草稿的正向提示词、反向提示词及视频时长，
 * 以便重新生成符合新要求的视频。</p>
 */
@Data
public class AiVideoVideoPromptRequest
{
    /** 正向提示词，描述希望生成的视频内容 */
    private String promptText;

    /** 反向提示词，描述不希望出现的视频内容 */
    private String negativePromptText;

    /** 视频时长（毫秒） */
    private Integer durationMs;
}
