package com.lingXi.aiVedio.worker;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.lingXi.aiVedio.domain.AiVideoGenerationTask;
import com.lingXi.aiVedio.mapper.AiVideoGenerationTaskMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * 故事圣经任务恢复扫描器。
 * <p>章节分析任务经 @Async 线程池执行，入队后仅存在于 JVM 内存；
 * 进程重启后 QUEUED 任务无人领取。此扫描器定期把滞留超过 60 秒的
 * QUEUED 章节任务重新交给分析 Worker（内部悲观锁领取，天然防重复）。</p>
 * <p>防失控保护：每次恢复投递前原子累加 recover_count，达到上限后
 * 任务直接失败，防止任务在 QUEUED/RUNNING 之间无限循环消耗 LLM token；
 * 执行中但租约缺失的异常任务直接失败而非释放回排队。</p>
 */
@Component
@Slf4j
public class AiVideoQueuedStoryBibleTaskRecovery
{
    /** 每次扫描的最大任务数。 */
    private static final int RECOVERY_BATCH_SIZE = 10;

    /** 单个任务的最大恢复重投递次数，超过后任务直接失败。 */
    private static final int MAX_RECOVERY_ATTEMPTS = 3;

    @Autowired
    private AiVideoGenerationTaskMapper taskMapper;
    @Autowired
    private AiVideoChapterAnalysisWorker chapterAnalysisWorker;

    /**
     * 定时恢复滞留排队中的故事圣经任务。
     */
    @Scheduled(fixedDelayString = "${aivideo.story-bible.queued-recovery-interval-ms}")
    public void recover()
    {
        int released = taskMapper.releaseStaleStoryBibleTasks();
        int failed = taskMapper.failNullLeaseStoryBibleTasks();
        List<AiVideoGenerationTask> tasks =
                taskMapper.selectQueuedStoryBibleTasksForRecovery(RECOVERY_BATCH_SIZE);
        int redelivered = 0;
        int limitExceeded = 0;
        for (AiVideoGenerationTask task : tasks)
        {
            if (taskMapper.incrementStoryBibleRecoveryCount(
                    task.getTaskId(), MAX_RECOVERY_ATTEMPTS) == 1)
            {
                chapterAnalysisWorker.analyze(task.getTaskId(), task.getChapterId());
                redelivered++;
            }
            else if (taskMapper.failStoryBibleTaskRecoveryLimitExceeded(
                    task.getTaskId(), MAX_RECOVERY_ATTEMPTS) == 1)
            {
                limitExceeded++;
            }
        }
        if (released > 0 || failed > 0 || !tasks.isEmpty())
        {
            log.info("AI视频故事圣经恢复扫描完成，释放租约过期任务数量={}，租约缺失直接失败数量={}，" +
                    "重新投递数量={}，投递超限终止数量={}", released, failed, redelivered, limitExceeded);
        }
    }
}
