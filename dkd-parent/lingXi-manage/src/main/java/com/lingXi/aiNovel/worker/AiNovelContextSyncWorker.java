package com.lingXi.aiNovel.worker;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import com.lingXi.aiNovel.domain.AiNovelContextTask;
import com.lingXi.aiNovel.mapper.AiNovelContextTaskMapper;
import com.lingXi.aiNovel.service.IAiNovelContextSyncService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 扫描并执行小说资料同步任务；租约保证多实例下同一时刻只有一个 Worker 执行。 */
@Component
public class AiNovelContextSyncWorker
{
    private static final Logger log = LoggerFactory.getLogger(AiNovelContextSyncWorker.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final int LEASE_SECONDS = 1_800;
    private static final int BATCH_SIZE = 4;

    private final AiNovelContextTaskMapper taskMapper;
    private final IAiNovelContextSyncService contextSyncService;
    private final ThreadPoolTaskExecutor executor;
    private final ObjectMapper objectMapper;

    public AiNovelContextSyncWorker(
            AiNovelContextTaskMapper taskMapper,
            IAiNovelContextSyncService contextSyncService,
            @Qualifier("threadPoolTaskExecutor") ThreadPoolTaskExecutor executor,
            ObjectMapper objectMapper)
    {
        this.taskMapper = taskMapper;
        this.contextSyncService = contextSyncService;
        this.executor = executor;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${novel.context-sync.worker.poll-delay-ms:1500}")
    public void dispatch()
    {
        taskMapper.markExhaustedExpiredTasks(MAX_ATTEMPTS, "任务执行超时且已达到最大重试次数");
        List<Long> taskIds = taskMapper.selectRunnableTaskIds(BATCH_SIZE, MAX_ATTEMPTS);
        for (Long taskId : taskIds)
        {
            String workerId = UUID.randomUUID().toString();
            if (taskMapper.claimTask(taskId, workerId, LEASE_SECONDS, MAX_ATTEMPTS) == 1)
            {
                try
                {
                    executor.execute(() -> executeClaimedTask(taskId, workerId));
                }
                catch (RuntimeException exception)
                {
                    // 线程池短时饱和时立即释放租约，避免任务无谓等待半小时。
                    taskMapper.markRetry(taskId, workerId, 5, "后台执行队列繁忙，稍后重试");
                    log.warn("小说资料同步任务提交线程池失败，taskId={}", taskId, exception);
                }
            }
        }
    }

    void executeClaimedTask(Long taskId, String workerId)
    {
        AiNovelContextTask task = taskMapper.selectByTaskId(taskId);
        if (task == null || !workerId.equals(task.getWorkerId()))
        {
            return;
        }
        try
        {
            JsonNode result = contextSyncService.executeAnalysisTask(task);
            if (result == null)
            {
                taskMapper.markObsolete(taskId, workerId, "章节正文已变化或章节已删除");
                return;
            }
            taskMapper.markSucceeded(taskId, workerId, objectMapper.writeValueAsString(result));
        }
        catch (Exception exception)
        {
            String message = safeMessage(exception);
            if (task.getAttemptCount() != null && task.getAttemptCount() < MAX_ATTEMPTS)
            {
                int retryDelay = task.getAttemptCount() <= 1 ? 5 : 15;
                taskMapper.markRetry(taskId, workerId, retryDelay, message);
            }
            else
            {
                taskMapper.markFailed(taskId, workerId, message);
            }
            log.warn("小说资料同步任务执行失败，taskId={}，attempt={}",
                    taskId, task.getAttemptCount(), exception);
        }
    }

    private static String safeMessage(Exception exception)
    {
        String message = exception.getMessage();
        if (message == null || message.isBlank())
        {
            message = "资料同步任务执行失败";
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
