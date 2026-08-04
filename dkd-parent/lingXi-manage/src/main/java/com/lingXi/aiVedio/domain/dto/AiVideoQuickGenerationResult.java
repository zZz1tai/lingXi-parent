package com.lingXi.aiVedio.domain.dto;

import lombok.Data;

/** 快速视频任务提交结果。 */
@Data
public class AiVideoQuickGenerationResult
{
    private Long projectId;
    private Long videoAssetId;
    private Long taskId;
    private String status;
    private Integer progress;
    private String errorCode;
    private String errorMessage;
}
