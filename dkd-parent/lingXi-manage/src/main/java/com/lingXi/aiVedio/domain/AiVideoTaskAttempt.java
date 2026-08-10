package com.lingXi.aiVedio.domain;

import com.lingXi.common.core.domain.BaseEntity;
import lombok.Data;

import java.util.Date;

/**
 * AI 视频生成任务尝试记录实体类，对应数据库表 ai_video_task_attempt。
 * <p>每次任务领取执行保存一条尝试记录，包含供应商请求标识、供应商任务ID、
 * 错误分类与起止时间，用于审计、恢复和重试追踪。</p>
 */
@Data
public class AiVideoTaskAttempt extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 尝试记录ID */
    private Long attemptId;

    /** 任务ID */
    private Long taskId;

    /** 尝试序号，从1开始递增 */
    private Integer attemptNo;

    /** 尝试状态：SUBMITTED/SUCCEEDED/FAILED */
    private String status;

    /** 供应商编码 */
    private String providerCode;

    /** 模型编码 */
    private String modelCode;

    /** 供应商请求标识（提交幂等键） */
    private String providerRequestId;

    /** 供应商任务ID（异步查询/回调关联） */
    private String providerTaskId;

    /** 错误分类码 */
    private String errorCode;

    /** 错误信息 */
    private String errorMessage;

    /** 尝试开始时间 */
    private Date startedTime;

    /** 尝试结束时间 */
    private Date completedTime;
}
