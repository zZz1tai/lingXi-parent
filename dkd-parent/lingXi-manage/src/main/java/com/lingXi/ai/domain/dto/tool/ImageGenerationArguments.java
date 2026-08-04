package com.lingXi.ai.domain.dto.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** 聊天图片生成的严格参数；模型、地址和密钥只能来自服务端当前配置。 */
@Data
public class ImageGenerationArguments {
    private String prompt;
    @JsonProperty("negative_prompt")
    private String negativePrompt;
    @JsonProperty("aspect_ratio")
    private String aspectRatio;
}
