package com.lingXi.aiVedio.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.lingXi.aiVedio.domain.AiVideoAsset;
import com.lingXi.aiVedio.domain.AiVideoGenerationTask;
import com.lingXi.aiVedio.domain.enums.AiVideoTaskStatus;
import com.lingXi.aiVedio.mapper.AiVideoAssetMapper;
import com.lingXi.aiVedio.mapper.AiVideoGenerationTaskMapper;
import com.lingXi.aiVedio.util.AiVideoJsonMetadata;
import com.lingXi.common.exception.ServiceException;

/**
 * AI 视频生成任务统一取消服务。
 * <p>当前供应商未提供取消接口，采用「本地终态取消 + 忽略晚到产物」策略：</p>
 * <ul>
 *   <li>QUEUED/RETRYING：任务未被执行者领取，直接取消为 CANCELED；</li>
 *   <li>VIDEO 的 WAITING_CALLBACK/RUNNING：取消为 CANCELED 终态，供应商晚到的
 *       回调或轮询结果会因任务状态条件更新失败而被忽略，视频文件不会转存为正式资产；</li>
 *   <li>IMAGE 的 RUNNING：图片模型调用为同步短任务，拒绝取消，提示稍后重试；</li>
 *   <li>终态任务（SUCCEEDED/FAILED/CANCELED）不可重复取消。</li>
 * </ul>
 * <p>待供应商支持取消 API 后，可在此增加 CANCEL_REQUESTED 中间态与外部取消确认闭环。</p>
 */
@Service
public class AiVideoTaskCancellationService
{
    @Autowired
    private AiVideoGenerationTaskMapper taskMapper;
    @Autowired
    private AiVideoAssetMapper assetMapper;
    @Autowired
    private AiVideoTaskAttemptService attemptService;

    /**
     * 取消生成任务。
     *
     * @param taskId   任务ID
     * @param username 操作人
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long taskId, String username)
    {
        AiVideoGenerationTask task = taskMapper.selectAiVideoGenerationTaskByTaskId(taskId);
        if (task == null)
        {
            throw new ServiceException("生成任务不存在");
        }
        if (AiVideoTaskStatus.CANCELED.is(task.getStatus()))
        {
            return;
        }
        if (AiVideoTaskStatus.isFinal(task.getStatus()))
        {
            throw new ServiceException("任务已结束，无法取消");
        }
        int updated;
        if (AiVideoTaskStatus.QUEUED.is(task.getStatus())
                || AiVideoTaskStatus.RETRYING.is(task.getStatus()))
        {
            updated = taskMapper.cancelQueuedTask(taskId, username);
        }
        else if (AiVideoTaskStatus.WAITING_CALLBACK.is(task.getStatus())
                || AiVideoTaskStatus.RUNNING.is(task.getStatus()))
        {
            if (!"VIDEO".equals(task.getTaskType()))
            {
                throw new ServiceException("图片生成正在执行中，请稍后重试");
            }
            updated = taskMapper.cancelActiveVideoTask(taskId, username);
        }
        else
        {
            throw new ServiceException("当前任务状态不支持取消：" + task.getStatus());
        }
        if (updated != 1)
        {
            throw new ServiceException("任务状态已变化，请刷新后重试");
        }
        markCANCELEDAsset(task);
        attemptService.failAttempt(taskId, "TASK_CANCELED", "用户取消生成任务");
    }

    /**
     * 将取消任务的关联资产标记为失败态（用户可重新生成）。
     *
     * @param task 生成任务
     */
    private void markCANCELEDAsset(AiVideoGenerationTask task)
    {
        if (task.getAssetId() == null)
        {
            return;
        }
        AiVideoAsset asset = assetMapper.selectAiVideoAssetByAssetId(task.getAssetId());
        if (asset == null || !"GENERATING".equals(asset.getStatus()))
        {
            return;
        }
        asset.setMetadataJson(AiVideoJsonMetadata.generationFailure(
                asset.getMetadataJson(), "生成任务已取消，可重新生成"));
        asset.setUpdateBy("cancel-task");
        assetMapper.markAiVideoAssetFailed(asset);
    }
}
