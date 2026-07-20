package com.lingXi.aiVedio.domain.dto;

import lombok.Data;

/** 视频供应商提交结果不确定时的人工核对请求。 */
@Data
public class AiVideoSubmissionResolutionRequest
{
    /** RESUME_WITH_PROVIDER_TASK_ID / CONFIRM_NOT_SUBMITTED */
    private String action;

    /** RESUME_WITH_PROVIDER_TASK_ID 时必填。 */
    private String providerTaskId;
}
