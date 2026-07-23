package com.lingXi.aiVedio.domain.dto;

import lombok.Data;

/**
 * 视频提交结果核对请求对象。
 * <p>当视频供应商返回不确定的提交结果时，由人工介入进行核对和决策，
 * 支持恢复查询或确认未提交两种操作。</p>
 */
@Data
public class AiVideoSubmissionResolutionRequest
{
    /** 操作类型：RESUME_WITH_PROVIDER_TASK_ID-恢复查询，CONFIRM_NOT_SUBMITTED-确认未提交 */
    private String action;

    /** 供应商任务ID，当操作类型为 RESUME_WITH_PROVIDER_TASK_ID 时必填 */
    private String providerTaskId;
}
