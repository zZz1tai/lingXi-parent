package com.lingXi.aiNovel.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.lingXi.aiNovel.domain.AiNovelContextTask;

/** 小说资料同步异步任务数据访问。 */
public interface AiNovelContextTaskMapper
{
    AiNovelContextTask selectByTaskId(Long taskId);

    AiNovelContextTask selectByChapterAndHash(
            @Param("chapterId") Long chapterId, @Param("contentHash") String contentHash);

    AiNovelContextTask selectLatestByChapterId(Long chapterId);

    int insertIgnore(AiNovelContextTask task);

    int resetTask(@Param("taskId") Long taskId);

    List<Long> selectRunnableTaskIds(
            @Param("limit") int limit, @Param("maxAttempts") int maxAttempts);

    int claimTask(@Param("taskId") Long taskId, @Param("workerId") String workerId,
            @Param("leaseSeconds") int leaseSeconds, @Param("maxAttempts") int maxAttempts);

    int markSucceeded(@Param("taskId") Long taskId, @Param("workerId") String workerId,
            @Param("resultJson") String resultJson);

    int markObsolete(@Param("taskId") Long taskId, @Param("workerId") String workerId,
            @Param("errorMessage") String errorMessage);

    int markRetry(@Param("taskId") Long taskId, @Param("workerId") String workerId,
            @Param("retryDelaySeconds") int retryDelaySeconds,
            @Param("errorMessage") String errorMessage);

    int markFailed(@Param("taskId") Long taskId, @Param("workerId") String workerId,
            @Param("errorMessage") String errorMessage);

    int markExhaustedExpiredTasks(@Param("maxAttempts") int maxAttempts,
            @Param("errorMessage") String errorMessage);

    int obsoleteActiveTasksByChapterId(@Param("chapterId") Long chapterId,
            @Param("errorMessage") String errorMessage);
}
