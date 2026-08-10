package com.lingXi.aiVedio.mapper;

import org.apache.ibatis.annotations.Param;
import java.util.Date;
import java.util.List;
import com.lingXi.aiVedio.domain.AiVideoTaskAttempt;

/**
 * AI视频生成任务尝试记录数据访问接口。
 */
public interface AiVideoTaskAttemptMapper
{
    /**
     * 新增尝试记录。
     *
     * @param attempt 尝试记录
     * @return 影响的行数
     */
    int insertAiVideoTaskAttempt(AiVideoTaskAttempt attempt);

    /**
     * 查询任务下已存在的最大尝试序号。
     *
     * @param taskId 任务ID
     * @return 最大尝试序号，无记录时返回 null
     */
    Integer selectMaxAttemptNoByTaskId(@Param("taskId") Long taskId);

    /**
     * 按任务ID查询尝试记录。
     *
     * @param taskId 任务ID
     * @return 尝试记录列表（按尝试序号升序）
     */
    List<AiVideoTaskAttempt> selectAiVideoTaskAttemptsByTaskId(@Param("taskId") Long taskId);

    /**
     * 标记最后一条活跃尝试成功。
     * <p>只有 SUBMITTED/RUNNING 状态的尝试可以被推进，终态不可重复覆盖。</p>
     *
     * @param taskId        任务ID
     * @param providerTaskId 供应商任务ID，可为 null
     * @return 影响的行数
     */
    int succeedLastActiveAttempt(@Param("taskId") Long taskId,
            @Param("providerTaskId") String providerTaskId);

    /**
     * 标记最后一条活跃尝试失败。
     *
     * @param taskId        任务ID
     * @param errorCode     错误分类码
     * @param errorMessage  错误信息
     * @return 影响的行数
     */
    int failLastActiveAttempt(@Param("taskId") Long taskId,
            @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage);

    /**
     * 为最后一条活跃尝试登记供应商任务ID。
     *
     * @param taskId         任务ID
     * @param providerTaskId 供应商任务ID
     * @return 影响的行数
     */
    int updateLastActiveAttemptProviderTaskId(@Param("taskId") Long taskId,
            @Param("providerTaskId") String providerTaskId);

    /**
     * 清理任务的历史尝试记录（归档或删除任务时调用）。
     *
     * @param taskId 任务ID
     * @return 删除的行数
     */
    int deleteAiVideoTaskAttemptsByTaskId(@Param("taskId") Long taskId);
}
