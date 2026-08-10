package com.lingXi.aiVedio.worker;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.lingXi.aiVedio.domain.AiVideoGenerationTask;
import com.lingXi.aiVedio.domain.AiVideoTaskOutbox;
import com.lingXi.aiVedio.mapper.AiVideoGenerationTaskMapper;
import com.lingXi.aiVedio.mapper.AiVideoTaskOutboxMapper;
import com.lingXi.aiVedio.service.AiVideoHappyHorseVideoService;
import lombok.extern.slf4j.Slf4j;

/**
 * AI视频任务投递事件派发器。
 * <p>定时扫描 PENDING 投递事件（事务内悲观锁占用），按事件类型路由到任务处理器：
 * VIDEO 提交事件交给视频供应商适配器执行外部提交，STORY_BIBLE 事件交给章节分析
 * Worker 异步执行。投递失败按指数退避安排下次重试，超过上限后标记 FAILED 并告警。</p>
 */
@Component
@Slf4j
public class AiVideoTaskOutboxDispatcher
{
    /** 投递重试上限，超过后不再自动重试。 */
    private static final int MAX_DISPATCH_RETRY = 3;
    /** 每次扫描的最大事件数。 */
    private static final int DISPATCH_BATCH_SIZE = 20;

    @Autowired
    private AiVideoTaskOutboxMapper outboxMapper;
    @Autowired
    private AiVideoGenerationTaskMapper taskMapper;
    @Autowired
    private AiVideoHappyHorseVideoService happyHorseVideoService;
    @Autowired
    private AiVideoChapterAnalysisWorker chapterAnalysisWorker;

    /**
     * 定时派发待处理投递事件。
     */
    @Scheduled(fixedDelayString = "${aivideo.outbox.dispatch-interval-ms}")
    public void dispatch()
    {
        int processed = 0;
        List<AiVideoTaskOutbox> pending = outboxMapper.selectPendingForDispatch(DISPATCH_BATCH_SIZE);
        for (AiVideoTaskOutbox outbox : pending)
        {
            try
            {
                dispatchOne(outbox);
                processed++;
            }
            catch (Exception ex)
            {
                log.error("AI视频投递事件处理失败，outboxId={}, taskId={}, eventType={}, errorType={}",
                        outbox.getOutboxId(), outbox.getTaskId(), outbox.getEventType(),
                        ex.getClass().getSimpleName());
                scheduleDispatchRetry(outbox, ex.getMessage());
            }
        }
        if (processed > 0)
        {
            log.info("AI视频投递事件派发完成，数量={}", processed);
        }
    }

    /**
     * 事务内占用并处理单个投递事件；处理成功标记 SENT，失败抛出后由外层安排重试。
     *
     * @param outbox 投递事件（已被悲观锁占用）
     */
    @Transactional
    protected void dispatchOne(AiVideoTaskOutbox outbox)
    {
        AiVideoGenerationTask task = taskMapper.selectAiVideoGenerationTaskByTaskId(outbox.getTaskId());
        if (task == null)
        {
            log.warn("AI视频投递事件关联任务不存在，直接标记已发送，outboxId={}, taskId={}",
                    outbox.getOutboxId(), outbox.getTaskId());
            outboxMapper.markAiVideoTaskOutboxSent(outbox.getOutboxId());
            return;
        }
        if ("VIDEO".equals(task.getTaskType()))
        {
            submitVideoToProvider(task);
        }
        else if ("STORY_BIBLE".equals(task.getTaskType()))
        {
            chapterAnalysisWorker.analyze(task.getTaskId(), task.getChapterId());
        }
        else
        {
            log.warn("AI视频投递事件暂不支持的任务类型，标记已发送，outboxId={}, taskType={}",
                    outbox.getOutboxId(), task.getTaskType());
        }
        outboxMapper.markAiVideoTaskOutboxSent(outbox.getOutboxId());
    }

    /**
     * 将排队中的视频任务提交给供应商。
     *
     * @param task 视频生成任务
     */
    private void submitVideoToProvider(AiVideoGenerationTask task)
    {
        happyHorseVideoService.submitQueuedVideoTask(task.getTaskId());
    }

    /**
     * 按指数退避安排投递重试，超过上限后标记失败。
     *
     * @param outbox      投递事件
     * @param errorMessage 失败原因
     */
    private void scheduleDispatchRetry(AiVideoTaskOutbox outbox, String errorMessage)
    {
        int retryCount = outbox.getRetryCount() == null ? 0 : outbox.getRetryCount();
        if (retryCount >= MAX_DISPATCH_RETRY)
        {
            outboxMapper.markAiVideoTaskOutboxFailed(outbox.getOutboxId(),
                    retryCount + 1, null, truncate(errorMessage));
            log.error("AI视频投递事件重试次数超限，放弃投递，outboxId={}, taskId={}",
                    outbox.getOutboxId(), outbox.getTaskId());
            return;
        }
        long delayMinutes = 1L << retryCount;
        Date nextRetryTime = new Date(System.currentTimeMillis() + delayMinutes * 60_000L);
        outboxMapper.markAiVideoTaskOutboxFailed(outbox.getOutboxId(),
                retryCount + 1, nextRetryTime, truncate(errorMessage));
        log.warn("AI视频投递事件将重试，outboxId={}, taskId={}, retryCount={}, delayMinutes={}",
                outbox.getOutboxId(), outbox.getTaskId(), retryCount + 1, delayMinutes);
    }

    /**
     * 截断过长的错误信息，避免超出字段长度。
     *
     * @param message 错误信息
     * @return 截断后的错误信息
     */
    private String truncate(String message)
    {
        if (message == null)
        {
            return "dispatch failed";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
