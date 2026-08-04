package com.lingXi.aiVedio.domain.dto;

import lombok.Data;

/** AI 对话页轮询快速视频任务时返回的最小状态。 */
@Data
public class AiVideoQuickGenerationStatus
{
    private Long projectId;
    private Long videoAssetId;
    private Long taskId;
    private String status;
    private Integer progress;
    private String errorCode;
    private String errorMessage;
    private Integer durationMs;
    private String videoUrl;
}
