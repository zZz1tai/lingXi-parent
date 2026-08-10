package com.lingXi.aiVedio.worker;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.lingXi.aiVedio.domain.AiVideoGenerationTask;
import com.lingXi.aiVedio.mapper.AiVideoGenerationTaskMapper;
import com.lingXi.aiVedio.outbox.AiVideoTaskOutboxPublisher;
import lombok.extern.slf4j.Slf4j;

/**
 * 视频任务重试恢复扫描器。
 * <p>外部提交失败的任务由供应商适配器按指数退避重新入队并写入下次重试时间；
 * 此扫描器把到达重试时间的 QUEUED 视频任务重新发布 TASK_RETRY 投递事件，
 * 由派发器再次执行外部提交，避免提交线程与调度线程互相阻塞。</p>
 */
@Component
@Slf4j
public class AiVideoQueuedVideoTaskRecovery
{
    /** 每次扫描的最大任务数。 */
    private static final int RECOVERY_BATCH_SIZE = 10;

    @Autowired
    private AiVideoGenerationTaskMapper taskMapper;
    @Autowired
    private AiVideoTaskOutboxPublisher outboxPublisher;

    /**
     * 定时把到达重试时间的视频任务重新投递。
     */
    @Scheduled(fixedDelayString = "${aivideo.video.retry-recovery-interval-ms}")
    public void recover()
    {
        List<AiVideoGenerationTask> tasks =
                taskMapper.selectQueuedVideoTasksForRetry(RECOVERY_BATCH_SIZE);
        for (AiVideoGenerationTask task : tasks)
        {
            outboxPublisher.publish(task.getTaskId(),
                    AiVideoTaskOutboxPublisher.EVENT_TASK_RETRY);
        }
        if (!tasks.isEmpty())
        {
            log.info("AI视频任务重试扫描完成，重新投递数量={}", tasks.size());
        }
    }
}
