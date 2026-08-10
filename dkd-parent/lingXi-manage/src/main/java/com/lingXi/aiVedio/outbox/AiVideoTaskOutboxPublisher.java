package com.lingXi.aiVedio.outbox;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.lingXi.aiVedio.domain.AiVideoTaskOutbox;
import com.lingXi.aiVedio.mapper.AiVideoTaskOutboxMapper;

/**
 * AI视频任务投递事件发布器。
 * <p>必须在业务事务内调用：任务创建成功后插入 PENDING 事件，事务提交后
 * 由 {@link com.lingXi.aiVedio.worker.AiVideoTaskOutboxDispatcher} 扫描并路由；
 * 事务回滚时事件随任务一起消失，保证任务与投递的一致性。</p>
 */
@Component
public class AiVideoTaskOutboxPublisher
{
    /** 投递事件负载中的任务ID字段。 */
    public static final String EVENT_TASK_CREATED = "TASK_CREATED";
    /** 投递事件负载中的任务ID字段。 */
    public static final String EVENT_TASK_RETRY = "TASK_RETRY";

    @Autowired
    private AiVideoTaskOutboxMapper outboxMapper;

    /**
     * 发布任务事件（与任务创建处于同一事务）。
     *
     * @param taskId    关联任务ID
     * @param eventType 事件类型：TASK_CREATED/TASK_RETRY
     */
    public void publish(Long taskId, String eventType)
    {
        AiVideoTaskOutbox outbox = new AiVideoTaskOutbox();
        outbox.setTaskId(taskId);
        outbox.setEventType(eventType);
        outbox.setPayloadJson("{\"taskId\":" + taskId + "}");
        outbox.setStatus("PENDING");
        outbox.setRetryCount(0);
        if (outboxMapper.insertAiVideoTaskOutbox(outbox) != 1)
        {
            throw new IllegalStateException("AI视频任务投递事件创建失败，taskId=" + taskId);
        }
    }
}
