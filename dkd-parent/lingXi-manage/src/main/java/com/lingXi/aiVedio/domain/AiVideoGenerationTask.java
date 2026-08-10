package com.lingXi.aiVedio.domain;

import java.util.Date;
import com.lingXi.common.core.domain.BaseEntity;
import lombok.Data;

/**
 * AI 视频异步生成任务实体类，对应数据库表 ai_video_generation_task。
 * <p>管理视频、图片等内容的异步生成任务，包含任务状态、
 * 进度追踪、重试控制及错误信息等完整的任务生命周期数据。</p>
 */
@Data
public class AiVideoGenerationTask extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 任务主键ID */
    private Long taskId;

    /** 所属项目ID */
    private Long projectId;

    /** 所属章节ID，可为空（项目级任务） */
    private Long chapterId;

    /** 关联资产ID，任务完成后将结果关联到该资产 */
    private Long assetId;

    /** 任务类型，如关键帧生成、视频生成等 */
    private String taskType;

    /** 任务名称，用于展示和日志记录 */
    private String taskName;

    /** 任务状态，如待处理、处理中、已完成、失败等 */
    private String status;

    /** 优先级，数值越大优先级越高 */
    private Integer priority;

    /** 幂等键，用于防止重复提交 */
    private String idempotencyKey;

    /** 供应商编码，标识调用的AI服务供应商 */
    private String providerCode;

    /** 模型编码，标识使用的具体AI模型 */
    private String modelCode;

    /** 供应商端任务ID，用于查询外部任务状态 */
    private String providerTaskId;

    /** 任务进度百分比，0-100 */
    private Integer progress;

    /** 最大重试次数 */
    private Integer maxRetry;

    /** 已重试次数 */
    private Integer retryCount;

    /** 下次重试时间 */
    private Date nextRetryTime;

    /** 请求参数，JSON格式存储 */
    private String requestJson;

    /** 错误码，任务失败时填写 */
    private String errorCode;

    /** 错误信息，任务失败时填写 */
    private String errorMessage;

    /** 最后回调事件ID，用于供应商回调去重 */
    private String callbackEventId;

    /** 当前执行者标识（租约持有者） */
    private String workerId;

    /** 租约过期时间，过期后可被其他执行者回收 */
    private Date leaseExpire;
}
