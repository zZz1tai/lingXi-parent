package com.lingXi.manage.service.impl;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.lingXi.common.constant.DkdContants;
import com.lingXi.common.core.domain.entity.SysUser;
import com.lingXi.common.exception.ServiceException;
import com.lingXi.common.utils.DateUtils;
import com.lingXi.common.utils.SecurityUtils;
import com.lingXi.app.common.constant.TaskTypeConstant;
import com.lingXi.manage.domain.TaskDetails;
import com.lingXi.manage.domain.UserTaskRelation;
import com.lingXi.manage.domain.VendingMachine;
import com.lingXi.manage.domain.dto.TaskDetailsDto;
import com.lingXi.manage.domain.dto.TaskDto;
import com.lingXi.manage.domain.vo.TaskVo;
import com.lingXi.manage.service.IUserTaskRelationService;
import com.lingXi.manage.service.ITaskDetailsService;
import com.lingXi.manage.service.IVendingMachineService;
import com.lingXi.system.service.ISysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import com.lingXi.manage.mapper.TaskMapper;
import com.lingXi.manage.domain.Task;
import com.lingXi.manage.service.ITaskService;
import org.springframework.transaction.annotation.Transactional;

import static com.lingXi.common.constant.DkdContants.*;

/**
 * 工单Service业务层处理
 *
 * @author itzhou
 * @date 2025-09-01
 */
@Service
public class TaskServiceImpl implements ITaskService {
    @Autowired
    private TaskMapper taskMapper;
    @Autowired
    private IVendingMachineService vendingMachineService;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private ITaskDetailsService taskDetailsService;
    @Autowired
    private ISysUserService sysUserService;
    @Autowired
    private IUserTaskRelationService userTaskRelationService;

    /**
     * 查询工单
     *
     * @param taskId 工单主键
     * @return 工单
     */
    @Override
    public Task selectTaskByTaskId(Long taskId) {
        return taskMapper.selectTaskByTaskId(taskId);
    }

    /**
     * 查询工单列表
     *
     * @param task 工单
     * @return 工单
     */
    @Override
    public List<Task> selectTaskList(Task task) {
        return taskMapper.selectTaskList(task);
    }

    /**
     * 新增工单
     *
     * @param task 工单
     * @return 结果
     */
    @Override
    public int insertTask(Task task) {
        return taskMapper.insertTask(task);
    }

    /**
     * 修改工单
     *
     * @param task 工单
     * @return 结果
     */
    @Override
    public int updateTask(Task task) {
        return taskMapper.updateTask(task);
    }

    /**
     * 批量删除工单
     *
     * @param taskIds 需要删除的工单主键
     * @return 结果
     */
    @Override
    public int deleteTaskByTaskIds(Long[] taskIds) {
        return taskMapper.deleteTaskByTaskIds(taskIds);
    }

    /**
     * 删除工单信息
     *
     * @param taskId 工单主键
     * @return 结果
     */
    @Override
    public int deleteTaskByTaskId(Long taskId) {
        return taskMapper.deleteTaskByTaskId(taskId);
    }

    /**
     * 查询工单列表
     *
     * @param task 工单
     * @return 工单
     */
    @Override
    public List<TaskVo> selectTaskVoList(Task task) {
        return taskMapper.selectTaskVoList(task);
    }

    /**
     * 新增运营、运维工单
     *
     * @param taskDto
     * @return 结果
     */
    @Transactional
    @Override
    public int insertTaskDto(TaskDto taskDto) {
        //1. 查询售货机是否存在
        VendingMachine vm = vendingMachineService.selectVendingMachineByInnerCode(taskDto.getInnerCode());
        if (vm == null) {
            throw new ServiceException("设备不存在");
        }
        //2. 校验售货机状态与工单类型是否相符
        checkCreateTask(vm.getVmStatus(), taskDto.getProductTypeId());
        //3. 检查设备是否有未完成的同类型工单
        hasTask(taskDto.getInnerCode(),taskDto.getProductTypeId());
        //5. 将dto转为po并补充属性，保存工单
        Task task = BeanUtil.copyProperties(taskDto, Task.class);// 属性复制
        task.setTaskStatus(TASK_STATUS_CREATE);// 创建工单
        task.setRegionId(vm.getRegionId());// 所属区域id
        task.setAddr(vm.getAddr());// 地址
        task.setTaskCode(generateTaskCode());// 工单编号
        int taskResult = taskMapper.insertTask(task);
        
        //7. 判断是否为补货工单
        if (taskDto.getProductTypeId().equals(TASK_TYPE_SUPPLY)) {
            // 8.保存工单详情
            List<TaskDetailsDto> details = taskDto.getDetails();
            if (CollUtil.isEmpty(details)) {
                throw new ServiceException("补货工单详情不能为空");
            }
            // 将dto转为po补充属性
            List<TaskDetails> taskDetailsList = details.stream().map(dto -> {
                TaskDetails taskDetails = BeanUtil.copyProperties(dto, TaskDetails.class);
                taskDetails.setTaskId(task.getTaskId());
                return taskDetails;
            }).collect(Collectors.toList());
            // 批量新增
            taskDetailsService.insertTaskDetailsBatch(taskDetailsList);
        }

        return taskResult;
    }

    /**
     * 取消工单
     * @param task
     * @return
     */
    @Override
    public int cancelTask(Task task) {
        //1.判断工单状态是否可以取消
        Task taskDb = taskMapper.selectTaskByTaskId(task.getTaskId());
        if (taskDb.getTaskStatus().equals(TASK_STATUS_CANCEL)) {
            throw new ServiceException("该工单已经取消，不能再次取消");
        }
        if (taskDb.getTaskStatus().equals(TASK_STATUS_FINISH)) {
            throw new ServiceException("该工单已经完成，不能取消");
        }
        task.setTaskStatus(TASK_STATUS_CANCEL);//设置工单状态为取消
        return taskMapper.updateTask(task);
    }


    /**
     * 生成并获取当天任务代码的唯一标识。
     * 该方法首先尝试从Redis中获取当天的任务代码计数，如果不存在，则初始化为1并返回"日期0001"格式的字符串。
     * 如果存在，则对计数加1并返回更新后的任务代码。
     *
     * @return 返回当天任务代码的唯一标识，格式为"日期XXXX"，其中XXXX是四位数字的计数。
     */
    public String generateTaskCode() {
        // 获取当前日期并格式化为"yyyyMMdd"
        String dateStr = DateUtils.getDate().replaceAll("-", "");
        // 根据日期生成redis的键
        String key = "dkd.task.code." + dateStr;
        // 判断key是否存在
        if (!redisTemplate.hasKey(key)) {
            // 如果key不存在，设置初始值为1，并指定过期时间为1天
            redisTemplate.opsForValue().set(key, 1, Duration.ofDays(1));
            // 返回工单编号（日期+0001）
            return dateStr + "0001";
        }
        // 如果key存在，计数器+1（0002），确保字符串长度为4位
        return dateStr+ StrUtil.padPre(redisTemplate.opsForValue().increment(key).toString(),4,'0');
    }

    /**
     * 检查设备是否已有未完成的同类型工单。
     * 本方法用于在创建新工单前，验证指定设备是否已经有处于进行中的同类型工单。
     * 如果存在未完成的同类型工单，则抛出服务异常，阻止新工单的创建。
     *
     * @param innerCode     设备的内部编码，用于唯一标识设备。
     * @param productTypeId 任务的类型，决定任务的性质（投放、维修、补货、撤机）。
     */
    private void hasTask(String innerCode, Long productTypeId) {
        // 创建Task对象，并设置设备编号和工单类型ID，以及任务状态为进行中
        Task taskParam = new Task();
        taskParam.setInnerCode(innerCode);
        taskParam.setProductTypeId(productTypeId);
        taskParam.setTaskStatus(TASK_STATUS_PROGRESS);

        // 查询数据库中符合指定条件的工单列表
        List<Task> taskList = taskMapper.selectTaskList(taskParam);

        // 如果存在未完成的同类型工单，则抛出服务异常
        if (CollUtil.isNotEmpty(taskList)) {
            throw new ServiceException("该设备有未完成的同类型工单，不能重复创建");
        }
    }

    /**
     * 根据设备的状态和任务类型，验证是否可以创建相应的任务。
     * 如果条件不满足，抛出服务异常。
     *
     * @param vmStatus      设备的状态，表示设备是否在运行。
     * @param productTypeId 任务的类型，决定任务的性质（投放、维修、补货、撤机）。
     */
    private void checkCreateTask(Long vmStatus, Long productTypeId) {
        // 如果是投放工单，且设备状态为运行中，则抛出异常，因为设备已在运营中无法进行投放
        if (productTypeId == DkdContants.TASK_TYPE_DEPLOY && vmStatus == DkdContants.VM_STATUS_RUNNING) {
            throw new ServiceException("该设备状态为运行中，无法进行投放");
        }

        // 如果是维修工单，且设备状态不是运行中，则抛出异常，因为设备不在运营中无法进行维修
        if (productTypeId == DkdContants.TASK_TYPE_REPAIR && vmStatus != DkdContants.VM_STATUS_RUNNING) {
            throw new ServiceException("该设备状态不是运行中，无法进行维修");
        }

        // 如果是补货工单，且设备状态不是运行中，则抛出异常，因为设备不在运营状态无法进行补货
        if (productTypeId == TASK_TYPE_SUPPLY && vmStatus != DkdContants.VM_STATUS_RUNNING) {
            throw new ServiceException("该设备状态不是运行中，无法进行补货");
        }

        // 如果是撤机工单，且设备状态不是运行中，则抛出异常，因为设备不在运营状态无法进行撤机
        if (productTypeId == DkdContants.TASK_TYPE_REVOKE && vmStatus != DkdContants.VM_STATUS_RUNNING) {
            throw new ServiceException("该设备状态不是运行中，无法进行撤机");
        }
    }

    /**
     * 查询设备维修次数
     *
     * @param innerCode 设备编号
     * @return 维修次数
     */
    @Override
    public int selectMaintenanceCountByInnerCode(String innerCode) {
        // 创建查询条件
        Task task = new Task();
        // 设置设备编号
        task.setInnerCode(innerCode);
        // 设置工单类型为维修工单（3）
        task.setProductTypeId(DkdContants.TASK_TYPE_REPAIR);
        // 查询维修工单数量
        List<Task> list = taskMapper.selectTaskList(task);
        return list.size();
    }

    /**
     * 校验当前用户是否有权操作该工单
     * 规则：工单管理员可操作所有工单；普通用户只能操作自己创建或指派给自己的工单
     *
     * @param taskId 工单ID
     */
    @Override
    public void checkTaskPermission(Long taskId) {
        Long userId = SecurityUtils.getUserId();
        // 查询工单
        Task task = taskMapper.selectTaskByTaskId(taskId);
        if (task == null) {
            throw new ServiceException("工单不存在");
        }
        // 判断是否为管理员（系统管理员userId=1 或 角色编码为1001的工单管理员）
        if (userId != null && userId == 1L) {
            return; // 系统管理员放行
        }
        // 判断当前用户是否为工单的创建者或指派人
        if (task.getAssignorId() != null && task.getAssignorId().equals(userId)) {
            return; // 指派人可操作
        }
        throw new ServiceException("无权操作：只能处理自己的工单");
    }
}
