package com.lingXi.aiVedio.mapper;

import org.apache.ibatis.annotations.Param;
import java.util.List;
import com.lingXi.aiVedio.domain.AiVideoGenerationTask;

public interface AiVideoGenerationTaskMapper
{
    int insertAiVideoGenerationTask(AiVideoGenerationTask task);

    AiVideoGenerationTask selectAiVideoGenerationTaskByIdempotencyKey(String idempotencyKey);

    AiVideoGenerationTask selectAiVideoGenerationTaskByTaskId(Long taskId);

    AiVideoGenerationTask selectRunningStoryBibleTaskForUpdate(Long taskId);

    AiVideoGenerationTask selectLatestStoryBibleTaskByKeyPrefix(String keyPrefix);

    AiVideoGenerationTask selectLatestImageTaskByAssetId(Long assetId);

    AiVideoGenerationTask selectLatestNeedsReviewVideoTaskByAssetId(Long assetId);

    int countActiveAiVideoGenerationTasksByAssetId(Long assetId);

    int updateAiVideoGenerationTaskStatus(@Param("taskId") Long taskId, @Param("status") String status,
            @Param("progress") Integer progress, @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage);

    int claimStoryBibleTask(@Param("taskId") Long taskId);

    int pauseStoryBibleTask(@Param("taskId") Long taskId, @Param("updateBy") String updateBy);

    int resumeStoryBibleTask(@Param("taskId") Long taskId, @Param("updateBy") String updateBy);

    int updateStoryBibleTaskProgress(@Param("taskId") Long taskId,
            @Param("progress") Integer progress, @Param("stageCode") String stageCode,
            @Param("stageLabel") String stageLabel);

    int updateStoryBibleTaskStatusIfRunning(@Param("taskId") Long taskId,
            @Param("status") String status, @Param("progress") Integer progress,
            @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage);

    int updateAiVideoGenerationProviderTaskId(@Param("taskId") Long taskId, @Param("providerTaskId") String providerTaskId);

    int markVideoProviderTaskWaiting(@Param("taskId") Long taskId,
            @Param("providerCode") String providerCode,
            @Param("providerTaskId") String providerTaskId,
            @Param("normalizedDurationMs") Integer normalizedDurationMs);

    int markVideoProviderTaskNeedsReviewWithProviderId(@Param("taskId") Long taskId,
            @Param("providerCode") String providerCode,
            @Param("providerTaskId") String providerTaskId,
            @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage);

    int claimVideoProviderTask(@Param("taskId") Long taskId,
            @Param("providerCode") String providerCode);

    int updateClaimedVideoProviderTaskStatus(@Param("taskId") Long taskId,
            @Param("providerCode") String providerCode,
            @Param("status") String status, @Param("progress") Integer progress,
            @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage);

    int markStaleVideoProviderSubmissionsNeedsReview(@Param("providerCode") String providerCode);

    int recoverStaleVideoProviderSubmissionsWithProviderId(@Param("providerCode") String providerCode);

    int releaseStaleClaimedVideoProviderTasks(@Param("providerCode") String providerCode);

    int failQueuedVideoProviderTask(@Param("taskId") Long taskId,
            @Param("providerCode") String providerCode,
            @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage);

    int resolveNeedsReviewVideoTaskWithProviderId(@Param("taskId") Long taskId,
            @Param("providerTaskId") String providerTaskId, @Param("resolvedBy") String resolvedBy);

    int resolveNeedsReviewVideoTaskAsNotSubmitted(@Param("taskId") Long taskId,
            @Param("resolvedBy") String resolvedBy);

    int updateClaimedImageTaskRequest(@Param("taskId") Long taskId, @Param("requestJson") String requestJson,
            @Param("modelCode") String modelCode);

    int resetFailedImageTaskForRetry(@Param("taskId") Long taskId, @Param("requestJson") String requestJson,
            @Param("modelCode") String modelCode);

    int claimImageTask(@Param("taskId") Long taskId, @Param("expectedStatus") String expectedStatus);

    int failImageTaskIfExpectedStatus(@Param("taskId") Long taskId,
            @Param("expectedStatus") String expectedStatus, @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage);

    List<AiVideoGenerationTask> selectQueuedImageTasksForRecovery();

    List<AiVideoGenerationTask> selectWaitingVideoProviderTasks(
            @Param("providerCode") String providerCode);

    List<AiVideoGenerationTask> selectAiVideoGenerationTaskList(Long projectId);
}
