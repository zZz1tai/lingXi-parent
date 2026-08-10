package com.lingXi.aiVedio.domain;

import com.lingXi.common.core.domain.BaseEntity;
import lombok.Data;

import java.util.Date;

/**
 * AI 视频任务可靠投递箱实体类，对应数据库表 ai_video_task_outbox。
 * <p>业务事务内写入投递事件，派发器事务外消费并路由到任务处理器，
 * 保证任务创建与投递解耦，进程重启后仍可按状态和重试时间恢复。</p>
 */
@Data
public class AiVideoTaskOutbox extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** Outbox 主键ID */
    private Long outboxId;

    /** 关联任务ID */
    private Long taskId;

    /** 事件类型：TASK_CREATED/TASK_RETRY/ASSET_APPROVED */
    private String eventType;

    /** 投递负载 JSON */
    private String payloadJson;

    /** 投递状态：PENDING/SENT/FAILED */
    private String status;

    /** 已重试投递次数 */
    private Integer retryCount;

    /** 下次允许投递时间 */
    private Date nextRetryTime;

    /** 实际投递完成时间 */
    private Date sentTime;
}
