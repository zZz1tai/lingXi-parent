package com.lingXi.ai.service;

import com.lingXi.ai.config.AgentConfig;
import com.lingXi.ai.domain.AgentAction;
import com.lingXi.ai.domain.dto.AgentUserContext;
import com.lingXi.ai.domain.dto.tool.AgentToolException;
import com.lingXi.ai.domain.dto.tool.AgentToolGrant;
import com.lingXi.ai.domain.dto.tool.MaintenanceTaskExecuteArguments;
import com.lingXi.ai.domain.dto.tool.MaintenanceTaskProposalArguments;
import com.lingXi.ai.mapper.AgentActionMapper;
import com.lingXi.common.exception.ServiceException;
import com.lingXi.manage.domain.VendingMachine;
import com.lingXi.manage.service.IVendingMachineService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** 维修工单提案、人工决策和幂等执行的安全编排。 */
@Service
public class AgentWriteActionService {
    public static final String ACTION_TYPE = "CREATE_MAINTENANCE_TASK";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_EXPIRED = "EXPIRED";

    private static final Pattern INNER_CODE = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final Pattern IDEMPOTENCY_KEY =
            Pattern.compile("^[A-Za-z0-9:_-]{1,128}$");
    private static final int DEFAULT_TTL_MINUTES = 15;
    private static final int MAX_TTL_MINUTES = 60;

    private final AgentConfig config;
    private final AgentActionMapper actionMapper;
    private final IVendingMachineService vendingMachineService;
    private final AgentWriteActionExecutor executor;

    public AgentWriteActionService(
            AgentConfig config,
            AgentActionMapper actionMapper,
            IVendingMachineService vendingMachineService,
            AgentWriteActionExecutor executor) {
        this.config = config;
        this.actionMapper = actionMapper;
        this.vendingMachineService = vendingMachineService;
        this.executor = executor;
    }

    /** 只创建待确认提案，不执行任何业务写入。 */
    @Transactional
    public Map<String, Object> propose(
            AgentToolGrant grant, MaintenanceTaskProposalArguments arguments) {
        requireEnabled();
        String innerCode = requireInnerCode(arguments == null ? null : arguments.getInnerCode());
        String description = requireDescription(
                arguments == null ? null : arguments.getDescription());
        String idempotencyKey = requireIdempotencyKey(
                arguments == null ? null : arguments.getIdempotencyKey());

        VendingMachine machine = vendingMachineService.selectVendingMachineByInnerCode(innerCode);
        if (machine == null) {
            throw toolError("TOOL_NOT_FOUND", "权限范围内未找到该设备", 404);
        }
        requireRegion(grant, machine.getRegionId());
        if (!com.lingXi.common.constant.DkdContants.VM_STATUS_RUNNING.equals(
                machine.getVmStatus())) {
            throw toolError("ACTION_PRECONDITION_FAILED", "设备当前不能创建维修工单", 409);
        }

        Date now = new Date();
        AgentAction candidate = new AgentAction();
        candidate.setActionId(UUID.randomUUID().toString().replace("-", ""));
        candidate.setIdempotencyKey(idempotencyKey);
        candidate.setActionType(ACTION_TYPE);
        candidate.setUserId(grant.getUserId());
        candidate.setThreadId(grant.getThreadId());
        candidate.setRegionId(machine.getRegionId());
        candidate.setInnerCode(innerCode);
        candidate.setActionDesc(description);
        candidate.setStatus(STATUS_PENDING);
        candidate.setCreatedAt(now);
        candidate.setExpiresAt(Date.from(
                now.toInstant().plus(proposalTtlMinutes(), ChronoUnit.MINUTES)));
        actionMapper.insertIgnore(candidate);

        AgentAction stored = actionMapper.selectByIdempotency(
                grant.getUserId(), grant.getThreadId(), idempotencyKey);
        if (stored == null) {
            throw toolError("ACTION_STORAGE_FAILED", "受控动作提案保存失败", 500);
        }
        if (!ACTION_TYPE.equals(stored.getActionType())
                || !innerCode.equals(stored.getInnerCode())
                || (STATUS_PENDING.equals(stored.getStatus())
                && !description.equals(stored.getActionDesc()))) {
            throw toolError(
                    "ACTION_IDEMPOTENCY_CONFLICT",
                    "同一幂等键已用于不同的受控动作",
                    409);
        }
        return publicAction(stored);
    }

    /** 仅接受当前登录用户对自己会话中提案作出的批准或拒绝。 */
    @Transactional(noRollbackFor = ServiceException.class)
    public Map<String, Object> decide(
            String actionId,
            String threadId,
            String decision,
            String editedDescription,
            AgentUserContext userContext) {
        requireEnabledForBrowser();
        requireTaskPermission(userContext);
        String normalizedActionId = requireActionId(actionId);
        String normalizedThreadId = requireThreadId(threadId);
        String normalizedDecision = normalizeDecision(decision);
        AgentAction action = actionMapper.selectByActionIdForUpdate(normalizedActionId);
        if (action == null
                || !action.getUserId().equals(userContext.getUserId())
                || !action.getThreadId().equals(normalizedThreadId)) {
            throw new ServiceException("受控动作不存在或无权访问");
        }

        String targetStatus = "approve".equals(normalizedDecision)
                ? STATUS_APPROVED : STATUS_REJECTED;
        String finalDescription = "approve".equals(normalizedDecision)
                ? (isBlank(editedDescription)
                        ? action.getActionDesc() : requireDescription(editedDescription))
                : action.getActionDesc();
        if (targetStatus.equals(action.getStatus())) {
            if (STATUS_APPROVED.equals(targetStatus)
                    && !Objects.equals(finalDescription, action.getActionDesc())) {
                if (actionMapper.updateDecision(
                        normalizedActionId, targetStatus, finalDescription, new Date(),
                        parseUserId(userContext.getUserId())) != 1) {
                    throw new ServiceException("受控动作更新失败");
                }
                return publicAction(actionMapper.selectByActionId(normalizedActionId));
            }
            if (!Objects.equals(finalDescription, action.getActionDesc())) {
                throw new ServiceException("受控动作已经决定，不能再次修改");
            }
            return publicAction(action);
        }
        if (!STATUS_PENDING.equals(action.getStatus())) {
            throw new ServiceException("受控动作已经决定，不能重复审批");
        }
        Date now = new Date();
        if (action.getExpiresAt() == null || !now.before(action.getExpiresAt())) {
            actionMapper.updateDecision(
                    normalizedActionId, STATUS_EXPIRED, action.getActionDesc(), now,
                    parseUserId(userContext.getUserId()));
            throw new ServiceException("受控动作已过期，请重新发起提案");
        }
        if (actionMapper.updateDecision(
                normalizedActionId, targetStatus, finalDescription, now,
                parseUserId(userContext.getUserId())) != 1) {
            throw new ServiceException("受控动作审批结果保存失败");
        }
        return publicAction(actionMapper.selectByActionId(normalizedActionId));
    }

    /** 只能由 LangGraph 恢复后的内部工具调用执行，失败后状态被冻结且不自动重试。 */
    public Map<String, Object> execute(
            AgentToolGrant grant, MaintenanceTaskExecuteArguments arguments) {
        requireEnabled();
        String actionId = requireActionId(arguments == null ? null : arguments.getActionId());
        try {
            return publicAction(executor.execute(actionId, grant));
        } catch (AgentToolException exception) {
            if ("ACTION_PRECONDITION_FAILED".equals(exception.getCode())
                    || "ACTION_EXECUTION_FAILED".equals(exception.getCode())) {
                actionMapper.markFailed(actionId, exception.getCode(), new Date());
            }
            throw exception;
        }
    }

    /** 获取只含前端展示字段的动作视图。 */
    public Map<String, Object> publicAction(AgentAction action) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("action_id", action.getActionId());
        data.put("action_type", action.getActionType());
        data.put("status", action.getStatus());
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("inner_code", action.getInnerCode());
        data.put("target", target);
        data.put("description", action.getActionDesc());
        data.put("impact", "只创建一张待处理维修工单，不修改设备状态、库存或配置");
        data.put("expires_at", toIso(action.getExpiresAt()));
        if (action.getTaskId() != null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("task_id", action.getTaskId());
            result.put("task_code", action.getTaskCode());
            data.put("result", result);
        }
        return data;
    }

    private void requireEnabled() {
        if (!config.isWriteActionsEnabled()) {
            throw toolError("ACTION_DISABLED", "受控写操作当前未启用", 403);
        }
    }

    private void requireEnabledForBrowser() {
        if (!config.isWriteActionsEnabled()) {
            throw new ServiceException("受控写操作当前未启用");
        }
    }

    private static void requireTaskPermission(AgentUserContext userContext) {
        if (userContext == null
                || !hasPermission(userContext, "manage:task:add")) {
            throw new ServiceException("当前用户无权创建维修工单");
        }
    }

    private static boolean hasPermission(AgentUserContext context, String required) {
        for (String permission : context.getPermissions()) {
            if ("*:*:*".equals(permission)
                    || org.springframework.util.PatternMatchUtils.simpleMatch(
                            permission, required)) {
                return true;
            }
        }
        return false;
    }

    private static void requireRegion(AgentToolGrant grant, Long targetRegionId) {
        if (targetRegionId == null
                || (!targetRegionId.equals(grant.getRegionId())
                && !grant.hasPermission("*:*:*"))) {
            throw toolError("TOOL_SCOPE_EMPTY", "目标设备不在当前用户可见区域内", 403);
        }
    }

    private int proposalTtlMinutes() {
        Integer configured = config.getWriteActionProposalTtlMinutes();
        if (configured == null || configured.intValue() <= 0) {
            return DEFAULT_TTL_MINUTES;
        }
        return Math.min(configured.intValue(), MAX_TTL_MINUTES);
    }

    private static String requireInnerCode(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!INNER_CODE.matcher(normalized).matches()) {
            throw toolError("TOOL_INVALID_ARGUMENT", "inner_code格式无效", 400);
        }
        return normalized;
    }

    private static String requireDescription(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 500
                || normalized.indexOf('\0') >= 0) {
            throw toolError("TOOL_INVALID_ARGUMENT", "description必须为1到500个字符", 400);
        }
        return normalized;
    }

    private static String requireIdempotencyKey(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!IDEMPOTENCY_KEY.matcher(normalized).matches()) {
            throw toolError("TOOL_INVALID_ARGUMENT", "idempotency_key格式无效", 400);
        }
        return normalized;
    }

    private static String requireActionId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("^[A-Za-z0-9_-]{1,64}$")) {
            throw toolError("TOOL_INVALID_ARGUMENT", "action_id格式无效", 400);
        }
        return normalized;
    }

    private static String requireThreadId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 128
                || normalized.indexOf('\r') >= 0 || normalized.indexOf('\n') >= 0) {
            throw new ServiceException("会话ID无效");
        }
        return normalized;
    }

    private static String normalizeDecision(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        if (!"approve".equals(normalized) && !"reject".equals(normalized)) {
            throw new ServiceException("decision仅支持approve或reject");
        }
        return normalized;
    }

    private static Long parseUserId(String userId) {
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException exception) {
            throw new ServiceException("当前登录用户标识无效");
        }
    }

    private static String toIso(Date value) {
        return value == null ? null : value.toInstant().toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static AgentToolException toolError(String code, String message, int status) {
        return new AgentToolException(code, message, status, false);
    }
}
