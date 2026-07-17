package com.lingXi.aiVedio.domain.dto;

import lombok.Data;

/** 图片资产提示词修改请求。 */
@Data
public class AiVideoAssetPromptRequest
{
    private String promptText;
    private String negativePromptText;
}
