package com.lingXi.app.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.lingXi.app.common.constant.TaskTypeConstant;
import com.lingXi.app.common.constant.VmStatusConstant;
import com.lingXi.app.common.constant.VmSystemConstant;
import com.lingXi.app.common.exception.LogicException;
import com.lingXi.app.domain.*;
import com.lingXi.app.domain.dto.CancelTaskDto;
import com.lingXi.app.domain.vo.Pager;
import com.lingXi.app.domain.vo.TaskSearchVo;
import com.lingXi.app.mapper.AppTaskMapper;
import com.lingXi.app.service.*;
import com.lingXi.manage.service.IUserTaskRelationService;
import com.lingXi.common.utils.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service("appTaskServiceImpl")
@Slf4j
public class TaskServiceImpl implements TaskService {

    @Autowired
    private AppTaskMapper taskMapper;
    @Autowired
    private TaskStatusService taskStatusService;
    @Autowired
    private TaskTypeService taskTypeService;

    @Autowired
    private VendingMachineService vendingMachineService;

    @Autowired
    private TaskDetailsService taskDetailsService;
    
    @Autowired
    private IUserTaskRelationService userTaskRelationService;

    @Autowired
    private EmpService empService;
    
    @Autowired
    private com.lingXi.system.service.ISysUserService sysUserService;

    /**
     * 搜索工单
     *
     * @param taskSearchVo 工单搜索参数
     * @return
     */
    @Override
    public Pager<AppTask> search(TaskSearchVo taskSearchVo) {
        // 构建查询参数
        Map<String, Object> params = new HashMap<>();
        params.put("innerCode", taskSearchVo.getInnerCode());
        params.put("userId", taskSearchVo.getUserId());
        params.put("taskCode", taskSearchVo.getTaskCode());
        params.put("status", taskSearchVo.getStatus());
        params.put("productTypeId", taskSearchVo.getProductTypeId());
        
        // 处理日期范围
        if (ObjectUtil.isNotEmpty(taskSearchVo.getStart()) && ObjectUtil.isNotEmpty(taskSearchVo.getEnd())) {
            // 转换为日期时间对象
            LocalDateTime minTime = taskSearchVo.getStart().atTime(LocalTime.MIN);
            LocalDateTime maxTime = taskSearchVo.getEnd().atTime(LocalTime.MAX);
            params.put("start", minTime);
            params.put("end", maxTime);
        }
        
        // 分页参数
        long pageIndex = taskSearchVo.getPageIndex() != null ? taskSearchVo.getPageIndex() : 1;
        long pageSize = taskSearchVo.getPageSize() != null ? taskSearchVo.getPageSize() : 10;
        long offset = (pageIndex - 1) * pageSize;
        params.put("offset", offset);
        params.put("pageSize", pageSize);
        
        // 执行分页查询
        List<AppTask> taskList = taskMapper.selectPage(params);
        // 查询总数
        Long total = taskMapper.selectCount(params);
        
        // 读取工单类型和工单状态
        readAll(taskList);
        
        // 构建并返回分页响应
        Pager<AppTask> pager = new Pager<>();
        pager.setList(taskList);
        pager.setTotal(total);
        pager.setPageIndex(taskSearchVo.getPageIndex());
        pager.setPageSize(taskSearchVo.getPageSize());
        return pager;
    }

    // 读取工单类型和工单状态
    private void readAll(List<AppTask> taskList) {
        List<AppTaskType> typeList = taskTypeService.list();
        List<AppTaskStatus> statusList = new ArrayList<>();
        statusList.add(new AppTaskStatus(1,"待处理"));
        statusList.add(new AppTaskStatus(2,"处理中"));
        statusList.add(new AppTaskStatus(3,"已取消"));
        statusList.add(new AppTaskStatus(4,"已完成"));
        taskList.forEach(task -> {
            //工单类型
            Optional<AppTaskType> type = typeList.stream().filter(taskTypeEntity -> taskTypeEntity.getTypeId().equals(task.getProductTypeId())).findFirst();
            if (type.isPresent()) {
                task.setTaskType(type.get());
            }
            //工单状态
            Optional<AppTaskStatus> status = statusList.stream().filter(taskStatusTypeEntity -> taskStatusTypeEntity.getStatusId().equals(task.getTaskStatus())).findFirst();
            if (status.isPresent()) {
                task.setTaskStatusTypeEntity(status.get());
            }
        });

    }

    // 接受工单
    @Override
    public Boolean accept(Long taskId, Long userId) {
        // 1.判断工单是否存在
        AppTask task = taskMapper.selectById(taskId);
        if (ObjectUtil.isEmpty(task)) {
            throw new LogicException("工单不存在");
        }
        // 2.判断工单状态是否为刚创建（待处理）
        if (task.getTaskStatus() != VmSystemConstant.TASK_STATUS_CREATE) {
            throw new LogicException("此工单状态不是待处理，无法接受");
        }
        // 4.修改工单状态
        task.setTaskStatus(VmSystemConstant.TASK_STATUS_PROGRESS);
        task.setUpdateTime(LocalDateTime.now());
        // 5.执行更新
        int result = taskMapper.updateById(task);
        return result > 0;
    }

    // 拒绝/取消工单
    @Override
    public Boolean cancel(Long taskId, CancelTaskDto cancelTaskDto, Long userId) {
        // 1.判断工单是否存在
        AppTask task = taskMapper.selectById(taskId);
        if (ObjectUtil.isEmpty(task)) {
            throw new LogicException("工单不存在");
        }
        // 2.判断工单状态是否为刚创建（待处理）
        if (task.getTaskStatus() == VmSystemConstant.TASK_STATUS_FINISH ||
                task.getTaskStatus() == VmSystemConstant.TASK_STATUS_CANCEL) {
            throw new LogicException("此工单状态不是待处理或进行中，无法取消");
        }
        // 4.修改工单状态
        task.setTaskStatus(VmSystemConstant.TASK_STATUS_CANCEL);
        task.setDesc(cancelTaskDto.getDesc());
        task.setUpdateTime(LocalDateTime.now());
        // 5.执行更新
        int result = taskMapper.updateById(task);
        return result > 0;
    }

    // 完成工单
    @Transactional
    @Override
    public Boolean complete(Long taskId, Long userId) {
        // 1.判断工单是否存在
        AppTask task = taskMapper.selectById(taskId);
        if (ObjectUtil.isEmpty(task)) {
            throw new LogicException("工单不存在");
        }
        // 2.判断工单状态
        if (task.getTaskStatus() == VmSystemConstant.TASK_STATUS_FINISH ||
                task.getTaskStatus() == VmSystemConstant.TASK_STATUS_CANCEL ||
                task.getTaskStatus() == VmSystemConstant.TASK_STATUS_CREATE) {
            throw new LogicException("此工单状态不是进行中，无法完成");
        }
        // 3.获取用户信息
        AppEmp emp = empService.getByUserId(userId);
        // 获取系统用户信息，使用nickName作为员工真实姓名
        com.lingXi.common.core.domain.entity.SysUser sysUser = sysUserService.selectUserById(userId);
        String realName = emp.getUserName();
        if (sysUser != null && sysUser.getNickName() != null && !sysUser.getNickName().isEmpty()) {
            realName = sysUser.getNickName();
        }
        // 4.修改工单状态和完成人员信息
        task.setTaskStatus(VmSystemConstant.TASK_STATUS_FINISH);
        task.setUserId(userId);
        task.setUserName(realName);
        task.setUpdateTime(LocalDateTime.now());
        // 5.执行更新
        int result = taskMapper.updateById(task);
        if (result <= 0) {
            return false;
        }
        // 6. 更新设备状态
        //判断工单类型  如果是投放工单，则修改为运行中状态
        boolean b = true;
        if (task.getProductTypeId().equals(TaskTypeConstant.TASK_TYPE_DEPLOY)) {
            b = vendingMachineService.updateStatus(task.getInnerCode(), VmStatusConstant.VM_STATUS_RUNNING);
        }
        //判断工单类型  如果是撤机工单，则修改为撤机状态
        if (task.getProductTypeId().equals(TaskTypeConstant.TASK_TYPE_REVOKE)) {
            b = vendingMachineService.updateStatus(task.getInnerCode(), VmStatusConstant.VM_STATUS_REVOKE);
        }
        //判断如果是补货工单
        if (task.getProductTypeId().equals(TaskTypeConstant.TASK_TYPE_SUPPLY)) {
            // 查询工单明细
            List<AppTaskDetails> details = taskDetailsService.getByTaskId(task.getTaskId());
            // 更新货道库存
            b = vendingMachineService.supply(task.getInnerCode(), details);
        }
        // 7.返回结果
        return b;
    }

    // 获取用户排名
    @Override
    public Map<String, Object> getRank(Long userId) {
        Map<String, Object> result = new HashMap<>();
        
        // 1. 查询已绑定员工的账号总数
        int boundEmployeeCount = empService.countBoundEmployees();
        
        // 2. 构建查询参数，获取所有工单
        Map<String, Object> params = new HashMap<>();
        // 设置分页参数，获取所有数据
        params.put("offset", 0);
        params.put("pageSize", 10000);
        
        // 查询所有工单
        List<AppTask> taskList = taskMapper.selectPage(params);
        
        // 统计每个用户的完成工单数量
        Map<Long, Integer> userTaskCountMap = new HashMap<>();
        // 收集所有与工单相关的用户
        Set<Long> allUsers = new HashSet<>();
        
        for (AppTask task : taskList) {
            // 收集所有用户
            if (task.getUserId() != null) {
                allUsers.add(task.getUserId());
            }
            
            // 统计已完成工单的用户
            if (task.getTaskStatus() != null && task.getTaskStatus().equals(VmSystemConstant.TASK_STATUS_FINISH) && task.getUserId() != null) {
                userTaskCountMap.put(task.getUserId(), userTaskCountMap.getOrDefault(task.getUserId(), 0) + 1);
            }
        }
        
        // 计算参与工单的用户数
        int total = allUsers.size();
        
        // 对用户按照完成工单数量降序排序
        List<Map.Entry<Long, Integer>> sortedUsers = new ArrayList<>(userTaskCountMap.entrySet());
        sortedUsers.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        
        // 计算指定用户的排名
        int rank = 0;
        for (int i = 0; i < sortedUsers.size(); i++) {
            if (sortedUsers.get(i).getKey().equals(userId)) {
                rank = i + 1;
                break;
            }
        }
        
        // 如果用户没有完成任何工单，排名为参与工单的用户数
        if (rank == 0) {
            rank = total;
        }
        
        // 设置结果
        result.put("boundEmployeeCount", boundEmployeeCount); // 已绑定员工的账号总数
        result.put("total", total); // 参与工单的用户数
        result.put("rank", rank); // 登录账号的排名
        
        return result;
    }

    @Override
    public void checkEmpBinding() {
        // 获取登录用户ID
        Long userId = SecurityUtils.getUserId();
        // 根据用户ID查询员工信息
        AppEmp emp = empService.getByUserId(userId);
        // 检查员工是否存在
        if (emp == null) {
            throw new RuntimeException("用户未绑定员工信息");
        }
        // 检查员工是否启用
        if (!emp.getStatus()) {
            throw new RuntimeException("员工账号已禁用");
        }
    }

    @Override
    public void checkTaskPermission(Long taskId) {
        checkEmpBinding();
        // 获取登录用户ID
        Long userId = SecurityUtils.getUserId();
        // 根据用户ID查询员工信息
        AppEmp emp = empService.getByUserId(userId);
        String roleCode = emp.getRoleCode();
        
        // 检查员工角色是否具有工单处理权限
        // 工单管理员(1001)：可以处理所有工单
        // 运营员(1002)：可以处理补货工单(2)、投放工单(1)和撤机工单(4)
        // 维修员(1003)：可以处理维修工单(3)
        if (roleCode == null) {
            throw new RuntimeException("无工单处理权限：员工角色未设置");
        }
        
        // 如果没有基本工单处理权限，直接拒绝
        if (!"1001".equals(roleCode) && !"1002".equals(roleCode) && !"1003".equals(roleCode)) {
            throw new RuntimeException("无工单处理权限");
        }
        
        // 如果传入了工单ID，检查工单类型和归属
        if (taskId != null) {
            AppTask task = taskMapper.selectById(taskId);
            if (task == null) {
                throw new RuntimeException("工单不存在");
            }
            Integer productTypeId = task.getProductTypeId();
            
            // 根据角色判断是否有权处理该类型工单
            if ("1001".equals(roleCode)) {
                // 工单管理员可以处理所有工单
            } else if ("1002".equals(roleCode)) {
                // 运营员可以处理补货工单(2)、投放工单(1)和撤机工单(4)
                if (productTypeId != 1 && productTypeId != 2 && productTypeId != 4) {
                    throw new RuntimeException("无工单处理权限：运营员只能处理投放工单、补货工单和撤机工单");
                }
            } else if ("1003".equals(roleCode)) {
                // 维修员可以处理维修工单(3)
                if (productTypeId != 3) {
                    throw new RuntimeException("无工单处理权限：维修员只能处理维修工单");
                }
            }
            
            // 检查工单归属：只能处理自己的工单（工单未分配或已分配给当前用户）
            if (task.getUserId() != null && !task.getUserId().equals(userId)) {
                throw new RuntimeException("无权处理：该工单已分配给其他员工");
            }
        }
    }
}