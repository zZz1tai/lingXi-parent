package com.lingXi.aiVedio.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** AI 生产链路运行时配置；API Key 只允许写入，绝不通过 JSON 或日志返回明文。 */
@Data
public class AiVideoModelConfig
{
    private String workspaceBaseUrl;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private String apiKey;
    private String apiKeyMasked;
    private Boolean apiKeyConfigured;
    private String textModel;
    private String imageModel;
    private String videoProvider;
    private String videoModel;
    private String videoResolution;
    private String videoRatio;
    private Boolean videoWatermark;
}
