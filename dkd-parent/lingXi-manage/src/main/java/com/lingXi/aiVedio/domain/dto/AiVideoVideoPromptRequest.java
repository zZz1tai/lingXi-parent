package com.lingXi.aiVedio.domain.dto;

import lombok.Data;

/** 视频草稿提示词修改请求。 */
@Data
public class AiVideoVideoPromptRequest
{
    private String promptText;
    private String negativePromptText;
    private Integer durationMs;
}
