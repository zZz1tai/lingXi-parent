package com.lingXi.ai.service;

import com.lingXi.ai.config.AgentConfig;
import com.lingXi.ai.domain.dto.AgentUserContext;
import com.lingXi.ai.domain.dto.tool.AgentToolAccess;
import com.lingXi.ai.domain.dto.tool.AgentToolException;
import com.lingXi.ai.domain.dto.tool.AgentToolGrant;
import com.dkd.framework.web.filter.RequestIdFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 签发、验证并撤销单轮短期工具令牌。
 * <p>首期使用进程内存，适合单实例部署；多实例生产部署必须迁移至共享 Store。</p>
 */
@Service
public class AgentToolTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final int DEFAULT_TTL_SECONDS = 300;
    private static final int MAX_TTL_SECONDS = 900;
    private static final int DEFAULT_MAX_CALLS = 5;
    private static final int MAX_CALLS = 10;

    private final AgentConfig config;
    private final Clock clock;
    private final SecureRandom secureRandom;
    private final ConcurrentMap<String, AgentToolGrant> grants = new ConcurrentHashMap<>();

    @Autowired
    public AgentToolTokenService(AgentConfig config) {
        this(config, Clock.systemUTC(), new SecureRandom());
    }

    AgentToolTokenService(AgentConfig config, Clock clock, SecureRandom secureRandom) {
        this.config = config;
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    public AgentToolAccess issue(AgentUserContext userContext, String threadId) {
        if (userContext == null || isBlank(threadId)) {
            throw new IllegalArgumentException("trusted user context and threadId are required");
        }
        cleanupExpired();
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        String requestId = resolveRequestId();
        Set<String> permissions = new LinkedHashSet<>(userContext.getPermissions());
        Set<String> allowedTools = AgentToolCatalog.allowedTools(permissions);
        if (!config.isWriteActionsEnabled()) {
            allowedTools.remove(AgentToolCatalog.PROPOSE_MAINTENANCE_TASK);
            allowedTools.remove(AgentToolCatalog.EXECUTE_MAINTENANCE_TASK);
        }
        Instant expiresAt = clock.instant().plusSeconds(toolTtlSeconds());
        AgentToolGrant grant = new AgentToolGrant(
                userContext.getUserId(),
                threadId.trim(),
                requestId,
                userContext.getRoleCode(),
                userContext.getRegionId(),
                userContext.getRegionName(),
                permissions,
                allowedTools,
                expiresAt,
                maxCallsPerRun());
        grants.put(tokenDigest(token), grant);
        return new AgentToolAccess(token, requestId, threadId.trim());
    }

    public AgentToolGrant validateAndConsume(
            String token,
            String agentRequestId,
            String threadId,
            String tool) {
        if (isBlank(token) || isBlank(agentRequestId) || isBlank(threadId) || isBlank(tool)) {
            throw unauthorized();
        }
        String digest = tokenDigest(token.trim());
        AgentToolGrant grant = grants.get(digest);
        if (grant == null) {
            throw unauthorized();
        }
        if (!clock.instant().isBefore(grant.getExpiresAt())) {
            grants.remove(digest, grant);
            throw unauthorized();
        }
        if (!MessageDigest.isEqual(
                grant.getAgentRequestId().getBytes(StandardCharsets.UTF_8),
                agentRequestId.trim().getBytes(StandardCharsets.UTF_8))
                || !MessageDigest.isEqual(
                grant.getThreadId().getBytes(StandardCharsets.UTF_8),
                threadId.trim().getBytes(StandardCharsets.UTF_8))) {
            throw unauthorized();
        }
        if (!grant.getAllowedTools().contains(tool.trim())) {
            throw new AgentToolException(
                    "TOOL_UNAUTHORIZED", "当前用户无权使用该工具", 403, false);
        }
        if (!grant.tryConsumeCall()) {
            throw new AgentToolException(
                    "TOOL_RATE_LIMITED", "本轮工具调用次数已达上限", 429, false);
        }
        return grant;
    }

    public void revoke(AgentToolAccess access) {
        if (access != null) {
            revoke(access.getToken());
        }
    }

    public void revoke(String token) {
        if (!isBlank(token)) {
            grants.remove(tokenDigest(token.trim()));
        }
    }

    int activeGrantCount() {
        return grants.size();
    }

    /**
     * 复用当前 HTTP 请求的 request_id（当其为 {@code req-} 格式时），
     * 使 Agent 工具调用与 Java 日志、响应头使用同一条链路标识；
     * 否则生成新的 {@code req-} 前缀标识。
     */
    static String resolveRequestId() {
        String fromContext = RequestIdFilter.current();
        if (fromContext != null && RequestIdFilter.GENERATED_PATTERN.matcher(fromContext).matches()) {
            return fromContext;
        }
        return "req-" + UUID.randomUUID().toString().replace("-", "");
    }

    private void cleanupExpired() {
        Instant now = clock.instant();
        grants.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().getExpiresAt()));
    }

    private long toolTtlSeconds() {
        Integer configured = config.getToolTokenTtlSeconds();
        if (configured == null || configured.intValue() <= 0) {
            return DEFAULT_TTL_SECONDS;
        }
        return Math.min(configured.intValue(), MAX_TTL_SECONDS);
    }

    private int maxCallsPerRun() {
        Integer configured = config.getToolMaxCallsPerRun();
        if (configured == null || configured.intValue() <= 0) {
            return DEFAULT_MAX_CALLS;
        }
        return Math.min(configured.intValue(), MAX_CALLS);
    }

    private static AgentToolException unauthorized() {
        return new AgentToolException(
                "TOOL_UNAUTHORIZED", "工具访问凭据无效或已过期", 401, false);
    }

    private static String tokenDigest(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
