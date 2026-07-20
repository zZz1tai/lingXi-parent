package com.lingXi.aiVedio.domain.dto;

import lombok.Data;

/** AI 视频生产链路使用的运行时模型配置，不包含 API Key。 */
@Data
public class AiVideoModelConfig
{
    private String workspaceBaseUrl;
    private String textModel;
    private String imageModel;
    private String videoProvider;
    private String videoModel;
    private String videoResolution;
    private String videoRatio;
    private Boolean videoWatermark;
}
