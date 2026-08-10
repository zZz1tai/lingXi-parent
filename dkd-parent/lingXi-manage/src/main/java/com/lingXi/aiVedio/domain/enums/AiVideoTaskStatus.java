package com.lingXi.aiVedio.domain.enums;

/**
 * AI 视频生成任务统一状态枚举。
 * <p>对应数据库 ai_video_generation_task.status 的取值，用于收敛散落在
 * Controller、Service、Worker 与回调中的状态字符串字面量。</p>
 */
public enum AiVideoTaskStatus
{
    /** 待处理（初始态） */
    PENDING,
    /** 排队中，等待 Worker 领取 */
    QUEUED,
    /** 运行中 */
    RUNNING,
    /** 等待供应商回调（同时保留轮询兜底） */
    WAITING_CALLBACK,
    /** 质检中 */
    QUALITY_CHECK,
    /** 重试中 */
    RETRYING,
    /** 待人工审核（供应商结果不确定） */
    NEEDS_REVIEW,
    /** 已提交（历史状态兼容） */
    SUBMITTED,
    /** 处理中（历史状态兼容） */
    PROCESSING,
    /** 校验中（历史状态兼容） */
    VALIDATING,
    /** 已暂停（章节解析协作式暂停） */
    PAUSED,
    /** 成功（终态） */
    SUCCEEDED,
    /** 失败（终态） */
    FAILED,
    /** 已取消（终态） */
    CANCELED;

    /** 终态集合 */
    public static final AiVideoTaskStatus[] FINAL_STATUSES = { SUCCEEDED, FAILED, CANCELED };

    /** 活动状态集合（非终态且未被暂停） */
    public static final AiVideoTaskStatus[] ACTIVE_STATUSES = {
        PENDING, QUEUED, RUNNING, WAITING_CALLBACK, QUALITY_CHECK,
        RETRYING, NEEDS_REVIEW, SUBMITTED, PROCESSING, VALIDATING
    };

    /**
     * 判断当前枚举是否为指定的数据库状态值。
     *
     * @param status 数据库状态字符串，可为 null
     */
    public boolean is(String status)
    {
        return name().equals(status);
    }

    /**
     * 判断指定状态字符串是否为终态。
     */
    public static boolean isFinal(String status)
    {
        for (AiVideoTaskStatus value : FINAL_STATUSES)
        {
            if (value.is(status))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断指定状态字符串是否为活动状态（运行中、可推进）。
     */
    public static boolean isActive(String status)
    {
        for (AiVideoTaskStatus value : ACTIVE_STATUSES)
        {
            if (value.is(status))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * 根据数据库状态字符串解析枚举；未知或空值返回 null。
     */
    public static AiVideoTaskStatus from(String status)
    {
        if (status == null || status.isEmpty())
        {
            return null;
        }
        try
        {
            return valueOf(status);
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }
    }
}
