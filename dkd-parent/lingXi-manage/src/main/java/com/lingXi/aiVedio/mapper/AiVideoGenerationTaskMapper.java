package com.lingXi.aiVedio.mapper;

import org.apache.ibatis.annotations.Param;
import java.util.List;
import com.lingXi.aiVedio.domain.AiVideoGenerationTask;

public interface AiVideoGenerationTaskMapper
{
    int insertAiVideoGenerationTask(AiVideoGenerationTask task);

    AiVideoGenerationTask selectAiVideoGenerationTaskByIdempotencyKey(String idempotencyKey);

    AiVideoGenerationTask selectLatestImageTaskByAssetId(Long assetId);

    AiVideoGenerationTask selectLatestNeedsReviewWanxTaskByAssetId(Long assetId);

    int countActiveAiVideoGenerationTasksByAssetId(Long assetId);

    int updateAiVideoGenerationTaskStatus(@Param("taskId") Long taskId, @Param("status") String status,
            @Param("progress") Integer progress, @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage);

    int claimStoryBibleTask(@Param("taskId") Long taskId);

    int updateAiVideoGenerationProviderTaskId(@Param("taskId") Long taskId, @Param("providerTaskId") String providerTaskId);

    int markWanxVideoTaskWaiting(@Param("taskId") Long taskId,
            @Param("providerTaskId") String providerTaskId,
            @Param("normalizedDurationMs") Integer normalizedDurationMs);

    int markWanxVideoTaskNeedsReviewWithProviderId(@Param("taskId") Long taskId,
            @Param("providerTaskId") String providerTaskId,
            @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage);

    int claimWanxVideoTask(@Param("taskId") Long taskId);

    int updateClaimedWanxVideoTaskStatus(@Param("taskId") Long taskId,
            @Param("status") String status, @Param("progress") Integer progress,
            @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage);

    int markStaleWanxSubmissionsNeedsReview();

    int recoverStaleWanxSubmissionsWithProviderId();

    int releaseStaleClaimedWanxVideoTasks();

    int failQueuedWanxVideoTask(@Param("taskId") Long taskId,
            @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage);

    int resolveNeedsReviewWanxTaskWithProviderId(@Param("taskId") Long taskId,
            @Param("providerTaskId") String providerTaskId, @Param("resolvedBy") String resolvedBy);

    int resolveNeedsReviewWanxTaskAsNotSubmitted(@Param("taskId") Long taskId,
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

    List<AiVideoGenerationTask> selectWaitingWanxVideoTasks();

    List<AiVideoGenerationTask> selectAiVideoGenerationTaskList(Long projectId);
}
