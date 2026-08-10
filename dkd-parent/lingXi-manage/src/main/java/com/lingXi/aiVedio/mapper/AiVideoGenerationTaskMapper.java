package com.lingXi.aiVedio.mapper;

import org.apache.ibatis.annotations.Param;
import java.util.Date;
import java.util.List;
import com.lingXi.aiVedio.domain.AiVideoGenerationTask;

/**
 * AI视频生成任务数据访问接口
 * <p>
 * 提供AI视频生成任务数据的数据库操作方法，包括任务的创建、状态查询、状态流转、
 * 幂等性控制以及视频供应商任务核对等操作。
 * 支持故事圣经、图片生成和视频生成三类任务的完整生命周期管理。
 * </p>
 *
 * @author lingXi
 * @since 2026-07-23
 */
public interface AiVideoGenerationTaskMapper
{
    /**
     * 新增AI视频生成任务
     *
     * @param task 生成任务信息对象
     * @return 影响的行数
     */
    int insertAiVideoGenerationTask(AiVideoGenerationTask task);

    /**
     * 根据幂等键查询生成任务
     *
     * @param idempotencyKey 幂等键
     * @return 生成任务信息，不存在时返回null
     */
    AiVideoGenerationTask selectAiVideoGenerationTaskByIdempotencyKey(String idempotencyKey);

    /**
     * 根据任务ID查询生成任务
     *
     * @param taskId 任务ID
     * @return 生成任务信息，不存在时返回null
     */
    AiVideoGenerationTask selectAiVideoGenerationTaskByTaskId(Long taskId);

    /**
     * 查询运行中的故事圣经任务（悲观锁）
     *
     * @param taskId 任务ID
     * @return 锁定的任务信息，不存在时返回null
     */
    AiVideoGenerationTask selectRunningStoryBibleTaskForUpdate(Long taskId);

    /**
     * 根据键前缀查询最新的故事圣经任务
     *
     * @param keyPrefix 键前缀
     * @return 最新的故事圣经任务信息，不存在时返回null
     */
    AiVideoGenerationTask selectLatestStoryBibleTaskByKeyPrefix(String keyPrefix);

    /**
     * 根据资产ID查询最新的图片生成任务
     *
     * @param assetId 资产ID
     * @return 最新的图片任务信息，不存在时返回null
     */
    AiVideoGenerationTask selectLatestImageTaskByAssetId(Long assetId);

    /**
     * 根据资产ID查询最新的待审核视频任务
     *
     * @param assetId 资产ID
     * @return 最新的待审核视频任务信息，不存在时返回null
     */
    AiVideoGenerationTask selectLatestNeedsReviewVideoTaskByAssetId(Long assetId);

    /**
     * 统计指定资产的活跃生成任务数量
     *
     * @param assetId 资产ID
     * @return 活跃任务数量
     */
    int countActiveAiVideoGenerationTasksByAssetId(Long assetId);

    /**
     * 更新生成任务状态
     *
     * @param taskId       任务ID
     * @param status       任务状态
     * @param progress     进度百分比
     * @param errorCode    错误代码
     * @param errorMessage 错误信息
     * @return 影响的行数
     */
    int updateAiVideoGenerationTaskStatus(@Param("taskId") Long taskId, @Param("status") String status,
            @Param("progress") Integer progress, @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage);

    /**
     * 认领故事圣经任务
     *
     * @param taskId 任务ID
     * @return 影响的行数
     */
    int claimStoryBibleTask(@Param("taskId") Long taskId);

    /**
     * 暂停故事圣经任务
     *
     * @param taskId  任务ID
     * @param updateBy 操作人
     * @return 影响的行数
     */
    int pauseStoryBibleTask(@Param("taskId") Long taskId, @Param("updateBy") String updateBy);

    /**
     * 恢复故事圣经任务
     *
     * @param taskId  任务ID
     * @param updateBy 操作人
     * @return 影响的行数
     */
    int resumeStoryBibleTask(@Param("taskId") Long taskId, @Param("updateBy") String updateBy);

    /**
     * 更新故事圣经任务进度
     *
     * @param taskId     任务ID
     * @param progress   进度百分比
     * @param stageCode  阶段编码
     * @param stageLabel 阶段标签
     * @return 影响的行数
     */
    int updateStoryBibleTaskProgress(@Param("taskId") Long taskId,
            @Param("progress") Integer progress, @Param("stageCode") String stageCode,
            @Param("stageLabel") String stageLabel);

    /**
     * 仅在任务运行中时更新故事圣经任务状态
     *
     * @param taskId       任务ID
     * @param status       任务状态
     * @param progress     进度百分比
     * @param errorCode    错误代码
     * @param errorMessage 错误信息
     * @return 影响的行数
     */
    int updateStoryBibleTaskStatusIfRunning(@Param("taskId") Long taskId,
            @Param("status") String status, @Param("progress") Integer progress,
            @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage);

    /**
     * 更新生成任务的供应商任务ID
     *
     * @param taskId         任务ID
     * @param providerTaskId 供应商任务ID
     * @return 影响的行数
     */
    int updateAiVideoGenerationProviderTaskId(@Param("taskId") Long taskId, @Param("providerTaskId") String providerTaskId);

    /**
     * 标记视频供应商任务为等待状态
     *
     * @param taskId              任务ID
     * @param providerCode        供应商编码
     * @param providerTaskId      供应商任务ID
     * @param normalizedDurationMs 标准化后的视频时长（毫秒）
     * @return 影响的行数
     */
    int markVideoProviderTaskWaiting(@Param("taskId") Long taskId,
            @Param("providerCode") String providerCode,
            @Param("providerTaskId") String providerTaskId,
            @Param("normalizedDurationMs") Integer normalizedDurationMs);

    /**
     * 标记视频供应商任务为待审核状态（含供应商ID）
     *
     * @param taskId          任务ID
     * @param providerCode    供应商编码
     * @param providerTaskId  供应商任务ID
     * @param errorCode       错误代码
     * @param errorMessage    错误信息
     * @return 影响的行数
     */
    int markVideoProviderTaskNeedsReviewWithProviderId(@Param("taskId") Long taskId,
            @Param("providerCode") String providerCode,
            @Param("providerTaskId") String providerTaskId,
            @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage);

    /**
     * 认领视频供应商任务
     *
     * @param taskId       任务ID
     * @param providerCode 供应商编码
     * @return 影响的行数
     */
    int claimVideoProviderTask(@Param("taskId") Long taskId,
            @Param("providerCode") String providerCode);

    /**
     * 更新已认领的视频供应商任务状态
     *
     * @param taskId       任务ID
     * @param providerCode 供应商编码
     * @param status       任务状态
     * @param progress     进度百分比
     * @param errorCode    错误代码
     * @param errorMessage 错误信息
     * @return 影响的行数
     */
    int updateClaimedVideoProviderTaskStatus(@Param("taskId") Long taskId,
            @Param("providerCode") String providerCode,
            @Param("status") String status, @Param("progress") Integer progress,
            @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage);

    /**
     * 将过期的视频供应商提交标记为待审核
     *
     * @param providerCode 供应商编码
     * @return 影响的行数
     */
    int markStaleVideoProviderSubmissionsNeedsReview(@Param("providerCode") String providerCode);

    /**
     * 恢复过期的视频供应商提交（含供应商ID）
     *
     * @param providerCode 供应商编码
     * @return 影响的行数
     */
    int recoverStaleVideoProviderSubmissionsWithProviderId(@Param("providerCode") String providerCode);

    /**
     * 释放过期的已认领视频供应商任务
     *
     * @param providerCode 供应商编码
     * @return 影响的行数
     */
    int releaseStaleClaimedVideoProviderTasks(@Param("providerCode") String providerCode);

    /**
     * 将排队中的视频供应商任务标记为失败
     *
     * @param taskId       任务ID
     * @param providerCode 供应商编码
     * @param errorCode    错误代码
     * @param errorMessage 错误信息
     * @return 影响的行数
     */
    int failQueuedVideoProviderTask(@Param("taskId") Long taskId,
            @Param("providerCode") String providerCode,
            @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage);

    /**
     * 使用供应商ID解决待审核视频任务
     *
     * @param taskId         任务ID
     * @param providerTaskId 供应商任务ID
     * @param resolvedBy     解决人
     * @return 影响的行数
     */
    int resolveNeedsReviewVideoTaskWithProviderId(@Param("taskId") Long taskId,
            @Param("providerTaskId") String providerTaskId, @Param("resolvedBy") String resolvedBy);

    /**
     * 将待审核视频任务标记为未提交状态
     *
     * @param taskId     任务ID
     * @param resolvedBy 解决人
     * @return 影响的行数
     */
    int resolveNeedsReviewVideoTaskAsNotSubmitted(@Param("taskId") Long taskId,
            @Param("resolvedBy") String resolvedBy);

    /**
     * 更新已认领的图片任务请求参数
     *
     * @param taskId      任务ID
     * @param requestJson 请求参数JSON
     * @param modelCode   模型编码
     * @return 影响的行数
     */
    int updateClaimedImageTaskRequest(@Param("taskId") Long taskId, @Param("requestJson") String requestJson,
            @Param("modelCode") String modelCode);

    /**
     * 重置失败的图片任务用于重试
     *
     * @param taskId      任务ID
     * @param requestJson 请求参数JSON
     * @param modelCode   模型编码
     * @return 影响的行数
     */
    int resetFailedImageTaskForRetry(@Param("taskId") Long taskId, @Param("requestJson") String requestJson,
            @Param("modelCode") String modelCode);

    /**
     * 认领图片任务
     *
     * @param taskId         任务ID
     * @param expectedStatus 期望的任务状态
     * @return 影响的行数
     */
    int claimImageTask(@Param("taskId") Long taskId, @Param("expectedStatus") String expectedStatus);

    /**
     * 在期望状态匹配时将图片任务标记为失败
     *
     * @param taskId         任务ID
     * @param expectedStatus 期望的任务状态
     * @param errorCode      错误代码
     * @param errorMessage   错误信息
     * @return 影响的行数
     */
    int failImageTaskIfExpectedStatus(@Param("taskId") Long taskId,
            @Param("expectedStatus") String expectedStatus, @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage);

    /**
     * 查询需要恢复的排队中图片任务列表
     *
     * @return 待恢复的图片任务列表
     */
    List<AiVideoGenerationTask> selectQueuedImageTasksForRecovery();

    /**
     * 查询指定供应商的等待中视频任务列表
     *
     * @param providerCode 供应商编码
     * @return 等待中的视频任务列表
     */
    List<AiVideoGenerationTask> selectWaitingVideoProviderTasks(
            @Param("providerCode") String providerCode);

    /**
     * 根据项目ID查询生成任务列表
     *
     * @param projectId 项目ID
     * @return 生成任务列表
     */
    List<AiVideoGenerationTask> selectAiVideoGenerationTaskList(Long projectId);

    /**
     * 领取排队中的视频任务用于外部提交（条件更新，防多实例重复提交）。
     *
     * @param taskId       任务ID
     * @param providerCode 供应商编码
     * @return 影响的行数
     */
    int claimQueuedVideoTaskForSubmission(@Param("taskId") Long taskId,
            @Param("providerCode") String providerCode);

    /**
     * 将已领取的视频任务标记为等待回调（供应商已受理）。
     *
     * @param taskId              任务ID
     * @param providerCode        供应商编码
     * @param providerTaskId      供应商任务ID
     * @param normalizedDurationMs 归一化视频时长
     * @return 影响的行数
     */
    int markClaimedVideoTaskWaiting(@Param("taskId") Long taskId,
            @Param("providerCode") String providerCode,
            @Param("providerTaskId") String providerTaskId,
            @Param("normalizedDurationMs") Integer normalizedDurationMs);

    /**
     * 将已领取的视频任务标记为待人工审核。
     *
     * @param taskId       任务ID
     * @param providerCode 供应商编码
     * @param errorCode    错误码
     * @param errorMessage 错误信息
     * @return 影响的行数
     */
    int markClaimedVideoTaskNeedsReview(@Param("taskId") Long taskId,
            @Param("providerCode") String providerCode,
            @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage);

    /**
     * 将已领取的视频任务标记为最终失败。
     *
     * @param taskId       任务ID
     * @param providerCode 供应商编码
     * @param errorCode    错误码
     * @param errorMessage 错误信息
     * @return 影响的行数
     */
    int failClaimedVideoTask(@Param("taskId") Long taskId,
            @Param("providerCode") String providerCode,
            @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage);

    /**
     * 将已领取的视频任务安排自动重试（回到排队状态并计算退避时间）。
     *
     * @param taskId          任务ID
     * @param providerCode    供应商编码
     * @param retryCount      已重试次数
     * @param nextRetryTime   下次重试时间
     * @param errorCode       错误码
     * @param errorMessage    错误信息
     * @return 影响的行数
     */
    int retryClaimedVideoTask(@Param("taskId") Long taskId,
            @Param("providerCode") String providerCode, @Param("retryCount") int retryCount,
            @Param("nextRetryTime") Date nextRetryTime,
            @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage);

    /**
     * 查询到达重试时间的排队视频任务（供恢复器重新投递）。
     *
     * @param limit 最大条数
     * @return 待重试的视频任务列表
     */
    List<AiVideoGenerationTask> selectQueuedVideoTasksForRetry(@Param("limit") int limit);

    /**
     * 查询排队中的故事圣经任务（供进程重启后的恢复扫描）。
     *
     * @param limit 最大条数
     * @return 排队中的故事圣经任务列表
     */
    List<AiVideoGenerationTask> selectQueuedStoryBibleTasksForRecovery(@Param("limit") int limit);

    /**
     * 将已领取的图片任务安排自动重试（回到排队状态并计算退避时间）。
     *
     * @param taskId          任务ID
     * @param retryCount      已重试次数
     * @param nextRetryTime   下次重试时间
     * @param errorCode       错误码
     * @param errorMessage    错误信息
     * @return 影响的行数
     */
    int retryClaimedImageTask(@Param("taskId") Long taskId,
            @Param("retryCount") int retryCount, @Param("nextRetryTime") Date nextRetryTime,
            @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage);

    /**
     * 查询到达重试时间的排队图片任务（供恢复器重新投递）。
     *
     * @param limit 最大条数
     * @return 待重试的图片任务列表
     */
    List<AiVideoGenerationTask> selectQueuedImageTasksForRetry(@Param("limit") int limit);
}
