package com.lingXi.aiVedio.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Date;
import com.lingXi.aiVedio.domain.AiVideoTaskAttempt;
import com.lingXi.aiVedio.domain.AiVideoGenerationTask;
import com.lingXi.aiVedio.mapper.AiVideoTaskAttemptMapper;

/**
 * AI 视频任务尝试记录服务。
 * <p>任务每次领取执行时创建一条尝试记录（序号自动递增），
 * 成功、失败和供应商任务ID登记均幂等推进最后一条活跃尝试；
 * 尝试终态不可被重复覆盖。</p>
 */
@Service
public class AiVideoTaskAttemptService
{
    @Autowired
    private AiVideoTaskAttemptMapper attemptMapper;

    /**
     * 为任务开始一次新的尝试记录。
     * <p>尝试序号取任务已有尝试的最大序号加一，不依赖任务表的重试计数，
     * 避免手动重试重置计数导致的序号冲突。</p>
     *
     * @param task               生成任务
     * @param providerCode       供应商编码
     * @param modelCode          模型编码
     * @param providerRequestId  供应商请求标识（如提交幂等键）
     * @return 新尝试的序号
     */
    @Transactional(rollbackFor = Exception.class)
    public int startAttempt(AiVideoGenerationTask task, String providerCode,
            String modelCode, String providerRequestId)
    {
        Integer maxAttemptNo = attemptMapper.selectMaxAttemptNoByTaskId(task.getTaskId());
        int attemptNo = maxAttemptNo == null ? 1 : maxAttemptNo + 1;
        AiVideoTaskAttempt attempt = new AiVideoTaskAttempt();
        attempt.setTaskId(task.getTaskId());
        attempt.setAttemptNo(attemptNo);
        attempt.setStatus("SUBMITTED");
        attempt.setProviderCode(providerCode);
        attempt.setModelCode(modelCode);
        attempt.setProviderRequestId(providerRequestId);
        attempt.setStartedTime(new Date());
        attemptMapper.insertAiVideoTaskAttempt(attempt);
        return attemptNo;
    }

    /**
     * 标记任务最后一条活跃尝试成功。
     *
     * @param taskId         任务ID
     * @param providerTaskId 供应商任务ID，可为 null
     */
    public void succeedAttempt(Long taskId, String providerTaskId)
    {
        attemptMapper.succeedLastActiveAttempt(taskId, providerTaskId);
    }

    /**
     * 标记任务最后一条活跃尝试失败。
     *
     * @param taskId       任务ID
     * @param errorCode    错误分类码
     * @param errorMessage 错误信息
     */
    public void failAttempt(Long taskId, String errorCode, String errorMessage)
    {
        attemptMapper.failLastActiveAttempt(taskId, errorCode, errorMessage);
    }

    /**
     * 为任务最后一条活跃尝试登记供应商任务ID。
     *
     * @param taskId         任务ID
     * @param providerTaskId 供应商任务ID
     */
    public void updateProviderTaskId(Long taskId, String providerTaskId)
    {
        attemptMapper.updateLastActiveAttemptProviderTaskId(taskId, providerTaskId);
    }
}
