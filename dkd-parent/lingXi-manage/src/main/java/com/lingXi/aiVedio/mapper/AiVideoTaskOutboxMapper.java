package com.lingXi.aiVedio.mapper;

import org.apache.ibatis.annotations.Param;
import java.util.Date;
import java.util.List;
import com.lingXi.aiVedio.domain.AiVideoTaskOutbox;

/**
 * AI视频任务可靠投递箱数据访问接口。
 * <p>提供投递事件的写入、扫描、占用和结果标记能力；
 * 扫描使用悲观锁配合事务，保证多实例部署下每个事件只被一个派发器消费。</p>
 */
public interface AiVideoTaskOutboxMapper
{
    /**
     * 新增投递事件（与业务任务创建处于同一事务）。
     *
     * @param outbox 投递事件
     * @return 影响的行数
     */
    int insertAiVideoTaskOutbox(AiVideoTaskOutbox outbox);

    /**
     * 事务内扫描待投递事件（含悲观锁）。
     *
     * @param limit 最大扫描条数
     * @return 待投递事件列表
     */
    List<AiVideoTaskOutbox> selectPendingForDispatch(@Param("limit") int limit);

    /**
     * 标记投递事件为已发送。
     *
     * @param outboxId 事件ID
     * @return 影响的行数
     */
    int markAiVideoTaskOutboxSent(@Param("outboxId") Long outboxId);

    /**
     * 标记投递失败并安排下次重试时间。
     *
     * @param outboxId       事件ID
     * @param retryCount     已重试次数
     * @param nextRetryTime  下次重试时间
     * @param errorMessage   失败原因
     * @return 影响的行数
     */
    int markAiVideoTaskOutboxFailed(@Param("outboxId") Long outboxId,
            @Param("retryCount") int retryCount, @Param("nextRetryTime") Date nextRetryTime,
            @Param("errorMessage") String errorMessage);

    /**
     * 统计未投递事件数量（含等待重试）。
     *
     * @return 未投递事件数量
     */
    long countPendingOutboxEvents();

    /**
     * 查询最老未投递事件的等待时长（秒）。
     *
     * @return 等待秒数；无待投递事件时返回 null
     */
    Long selectOldestPendingEventAgeSeconds();
}
