package com.lingXi.aiVedio.domain.dto;

import lombok.Data;

/**
 * 图片资产提示词修改请求对象。
 * <p>用于修改已有图片资产的正向提示词和反向提示词，
 * 以便重新生成符合新要求的图片。</p>
 */
@Data
public class AiVideoAssetPromptRequest
{
    /** 正向提示词，描述希望生成的内容 */
    private String promptText;

    /** 反向提示词，描述不希望出现的内容 */
    private String negativePromptText;
}
