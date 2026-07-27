package com.lingXi.ai.domain.dto.tool;

import org.springframework.util.PatternMatchUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** 由短期工具令牌解析出的不可变授权快照。 */
public final class AgentToolGrant {

    private final String userId;
    private final String threadId;
    private final String agentRequestId;
    private final String roleCode;
    private final Long regionId;
    private final String regionName;
    private final Set<String> permissions;
    private final Set<String> allowedTools;
    private final Instant expiresAt;
    private final AtomicInteger remainingCalls;

    public AgentToolGrant(
            String userId,
            String threadId,
            String agentRequestId,
            String roleCode,
            Long regionId,
            String regionName,
            Set<String> permissions,
            Set<String> allowedTools,
            Instant expiresAt,
            int maxCalls) {
        this.userId = userId;
        this.threadId = threadId;
        this.agentRequestId = agentRequestId;
        this.roleCode = roleCode;
        this.regionId = regionId;
        this.regionName = regionName;
        this.permissions = Collections.unmodifiableSet(new LinkedHashSet<>(permissions));
        this.allowedTools = Collections.unmodifiableSet(new LinkedHashSet<>(allowedTools));
        this.expiresAt = expiresAt;
        this.remainingCalls = new AtomicInteger(maxCalls);
    }

    public boolean tryConsumeCall() {
        int current;
        do {
            current = remainingCalls.get();
            if (current <= 0) {
                return false;
            }
        } while (!remainingCalls.compareAndSet(current, current - 1));
        return true;
    }

    public boolean hasPermission(String requiredPermission) {
        for (String permission : permissions) {
            if ("*:*:*".equals(permission)
                    || PatternMatchUtils.simpleMatch(permission, requiredPermission)) {
                return true;
            }
        }
        return false;
    }

    public String getUserId() {
        return userId;
    }

    public String getThreadId() {
        return threadId;
    }

    public String getAgentRequestId() {
        return agentRequestId;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public Long getRegionId() {
        return regionId;
    }

    public String getRegionName() {
        return regionName;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public Set<String> getAllowedTools() {
        return allowedTools;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public int getRemainingCalls() {
        return remainingCalls.get();
    }
}
