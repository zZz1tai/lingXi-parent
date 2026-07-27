package com.lingXi.ai.domain;

import lombok.Data;

import java.util.Date;

/** AI 人工确认受控动作的持久化记录。 */
@Data
public class AgentAction {
    private String actionId;
    private String idempotencyKey;
    private String actionType;
    private String userId;
    private String threadId;
    private Long regionId;
    private String innerCode;
    private String actionDesc;
    private String status;
    private Date createdAt;
    private Date expiresAt;
    private Date decidedAt;
    private Long decidedBy;
    private Date executedAt;
    private Long taskId;
    private String taskCode;
    private String lastErrorCode;
}

