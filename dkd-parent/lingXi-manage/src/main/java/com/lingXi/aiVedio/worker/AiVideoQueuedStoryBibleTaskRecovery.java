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
 */
@Component
@Slf4j
public class AiVideoQueuedStoryBibleTaskRecovery
{
    /** 每次扫描的最大任务数。 */
    private static final int RECOVERY_BATCH_SIZE = 10;

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
        List<AiVideoGenerationTask> tasks =
                taskMapper.selectQueuedStoryBibleTasksForRecovery(RECOVERY_BATCH_SIZE);
        for (AiVideoGenerationTask task : tasks)
        {
            chapterAnalysisWorker.analyze(task.getTaskId(), task.getChapterId());
        }
        if (released > 0 || !tasks.isEmpty())
        {
            log.info("AI视频故事圣经恢复扫描完成，释放租约过期任务数量={}，重新投递数量={}", released, tasks.size());
        }
    }
}
