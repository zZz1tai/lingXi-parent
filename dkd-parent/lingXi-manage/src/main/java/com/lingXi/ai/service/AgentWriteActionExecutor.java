package com.lingXi.ai.service;

import com.lingXi.ai.domain.AgentAction;
import com.lingXi.ai.domain.dto.tool.AgentToolException;
import com.lingXi.ai.domain.dto.tool.AgentToolGrant;
import com.lingXi.ai.mapper.AgentActionMapper;
import com.lingXi.common.constant.DkdContants;
import com.lingXi.common.exception.ServiceException;
import com.lingXi.manage.domain.Task;
import com.lingXi.manage.domain.VendingMachine;
import com.lingXi.manage.domain.dto.TaskDto;
import com.lingXi.manage.mapper.TaskMapper;
import com.lingXi.manage.service.ITaskService;
import com.lingXi.manage.service.IVendingMachineService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/** 在单个数据库事务内完成动作行锁、状态复核、工单创建和结果写回。 */
@Service
public class AgentWriteActionExecutor {
    private final AgentActionMapper actionMapper;
    private final TaskMapper taskMapper;
    private final ITaskService taskService;
    private final IVendingMachineService vendingMachineService;

    public AgentWriteActionExecutor(
            AgentActionMapper actionMapper,
            TaskMapper taskMapper,
            ITaskService taskService,
            IVendingMachineService vendingMachineService) {
        this.actionMapper = actionMapper;
        this.taskMapper = taskMapper;
        this.taskService = taskService;
        this.vendingMachineService = vendingMachineService;
    }

    /** 执行已批准动作；同一个 actionId 在数据库层最多创建一张工单。 */
    @Transactional
    public AgentAction execute(String actionId, AgentToolGrant grant) {
        AgentAction action = actionMapper.selectByActionIdForUpdate(actionId);
        if (action == null) {
            throw toolError("ACTION_NOT_FOUND", "受控动作不存在", 404);
        }
        requireSameIdentity(action, grant);

        Task existingTask = taskMapper.selectTaskByAgentActionId(actionId);
        if (existingTask != null) {
            actionMapper.markSucceeded(
                    actionId, existingTask.getTaskId(), existingTask.getTaskCode(), new Date());
            return actionMapper.selectByActionId(actionId);
        }
        if (AgentWriteActionService.STATUS_SUCCEEDED.equals(action.getStatus())) {
            return action;
        }
        if (!AgentWriteActionService.STATUS_APPROVED.equals(action.getStatus())) {
            throw toolError(
                    "ACTION_NOT_APPROVED",
                    "受控动作尚未由当前登录用户批准",
                    409);
        }
        if (action.getExpiresAt() == null || !new Date().before(action.getExpiresAt())) {
            throw toolError("ACTION_PRECONDITION_FAILED", "受控动作已过期", 409);
        }

        VendingMachine machine = vendingMachineService.selectVendingMachineByInnerCodeForUpdate(
                action.getInnerCode());
        if (machine == null) {
            throw toolError("ACTION_PRECONDITION_FAILED", "目标设备已不存在", 409);
        }
        if (machine.getRegionId() == null
                || !machine.getRegionId().equals(action.getRegionId())
                || !isRegionAllowed(grant, machine.getRegionId())) {
            throw toolError("ACTION_FORBIDDEN", "目标设备不在当前权限区域内", 403);
        }
        if (!DkdContants.VM_STATUS_RUNNING.equals(machine.getVmStatus())) {
            throw toolError("ACTION_PRECONDITION_FAILED", "设备状态已变化，不能创建维修工单", 409);
        }
        if (taskMapper.countUnfinishedTasks(
                action.getInnerCode(), DkdContants.TASK_TYPE_REPAIR) > 0) {
            throw toolError(
                    "ACTION_PRECONDITION_FAILED",
                    "设备已有待处理或进行中的维修工单",
                    409);
        }

        TaskDto task = new TaskDto();
        task.setCreateType(1L);
        task.setInnerCode(action.getInnerCode());
        task.setAssignorId(parseUserId(grant.getUserId()));
        task.setProductTypeId(DkdContants.TASK_TYPE_REPAIR);
        task.setDesc(action.getActionDesc());
        task.setAgentActionId(actionId);
        try {
            if (taskService.insertTaskDto(task) != 1) {
                throw new ServiceException("维修工单创建失败");
            }
        } catch (ServiceException exception) {
            throw toolError("ACTION_PRECONDITION_FAILED", exception.getMessage(), 409);
        } catch (RuntimeException exception) {
            throw toolError("ACTION_EXECUTION_FAILED", "维修工单创建失败", 500);
        }

        Task created = taskMapper.selectTaskByAgentActionId(actionId);
        if (created == null || created.getTaskId() == null) {
            throw toolError("ACTION_EXECUTION_FAILED", "维修工单结果校验失败", 500);
        }
        if (actionMapper.markSucceeded(
                actionId, created.getTaskId(), created.getTaskCode(), new Date()) != 1) {
            throw toolError("ACTION_EXECUTION_FAILED", "受控动作结果保存失败", 500);
        }
        return actionMapper.selectByActionId(actionId);
    }

    private static void requireSameIdentity(AgentAction action, AgentToolGrant grant) {
        if (!action.getUserId().equals(grant.getUserId())
                || !action.getThreadId().equals(grant.getThreadId())) {
            throw toolError("ACTION_FORBIDDEN", "受控动作不属于当前用户或会话", 403);
        }
    }

    private static boolean isRegionAllowed(AgentToolGrant grant, Long regionId) {
        return regionId != null
                && (regionId.equals(grant.getRegionId()) || grant.hasPermission("*:*:*"));
    }

    private static Long parseUserId(String userId) {
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException exception) {
            throw toolError("ACTION_FORBIDDEN", "当前登录用户标识无效", 403);
        }
    }

    private static AgentToolException toolError(String code, String message, int status) {
        return new AgentToolException(code, message, status, false);
    }
}
