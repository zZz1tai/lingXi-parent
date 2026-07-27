package com.lingXi.ai.mapper;

import com.lingXi.ai.domain.AgentAction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;

/** AI 受控动作持久化 Mapper。 */
@Mapper
public interface AgentActionMapper {
    AgentAction selectByActionId(String actionId);

    AgentAction selectByActionIdForUpdate(String actionId);

    AgentAction selectByIdempotency(
            @Param("userId") String userId,
            @Param("threadId") String threadId,
            @Param("idempotencyKey") String idempotencyKey);

    int insertIgnore(AgentAction action);

    int updateDecision(
            @Param("actionId") String actionId,
            @Param("status") String status,
            @Param("actionDesc") String actionDesc,
            @Param("decidedAt") Date decidedAt,
            @Param("decidedBy") Long decidedBy);

    int markSucceeded(
            @Param("actionId") String actionId,
            @Param("taskId") Long taskId,
            @Param("taskCode") String taskCode,
            @Param("executedAt") Date executedAt);

    int markFailed(
            @Param("actionId") String actionId,
            @Param("errorCode") String errorCode,
            @Param("executedAt") Date executedAt);
}

