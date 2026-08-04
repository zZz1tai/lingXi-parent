package com.lingXi.ai.domain.dto.tool;

import lombok.Data;

/**
 * Java 传递给 Python Agent 的单轮工具访问凭据。
 * <p>令牌不实现 {@code toString}，避免被日志或调试输出意外展开。</p>
 */
@Data
public final class AgentToolAccess {

    private final String token;
    private final String agentRequestId;
    private final String threadId;

    public AgentToolAccess(String token, String agentRequestId, String threadId) {
        this.token = token;
        this.agentRequestId = agentRequestId;
        this.threadId = threadId;
    }

}
