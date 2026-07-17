package com.lingXi.aiVedio.domain;

import com.lingXi.common.core.domain.BaseEntity;
import lombok.Data;

/** AI 视频异步任务对象 ai_video_generation_task */
@Data
public class AiVideoGenerationTask extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long taskId;
    private Long projectId;
    private Long chapterId;
    private Long assetId;
    private String taskType;
    private String taskName;
    private String status;
    private Integer priority;
    private String idempotencyKey;
    private String providerCode;
    private String modelCode;
    private String providerTaskId;
    private Integer progress;
    private Integer maxRetry;
    private String requestJson;
    private String errorCode;
    private String errorMessage;
}
