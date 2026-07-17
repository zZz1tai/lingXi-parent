package com.lingXi.manage.service;

import java.util.List;
import com.lingXi.manage.domain.Task;
import com.lingXi.manage.domain.dto.TaskDto;
import com.lingXi.manage.domain.vo.TaskVo;

/**
 * 工单Service接口
 * 
 * @author itzhou
 * @date 2025-09-01
 */
public interface ITaskService 
{
    /**
     * 查询工单
     * 
     * @param taskId 工单主键
     * @return 工单
     */
    public Task selectTaskByTaskId(Long taskId);

    /**
     * 查询工单列表
     * 
     * @param task 工单
     * @return 工单集合
     */
    public List<Task> selectTaskList(Task task);

    /**
     * 新增工单
     * 
     * @param task 工单
     * @return 结果
     */
    public int insertTask(Task task);

    /**
     * 修改工单
     * 
     * @param task 工单
     * @return 结果
     */
    public int updateTask(Task task);

    /**
     * 批量删除工单
     * 
     * @param taskIds 需要删除的工单主键集合
     * @return 结果
     */
    public int deleteTaskByTaskIds(Long[] taskIds);

    /**
     * 删除工单信息
     * 
     * @param taskId 工单主键
     * @return 结果
     */
    public int deleteTaskByTaskId(Long taskId);

    /**
     * 查询工单列表
     *
     * @param task 工单
     * @return 工单集合
     */
    public List<TaskVo> selectTaskVoList(Task task);

    /**
     * 新增运营和运维工单
     *
     * @param taskDto 工单
     * @return 结果
     */
    int insertTaskDto(TaskDto taskDto);

    /**
     * 取消工单
     *
     * @param task 工单
     * @return 结果
     */
    int cancelTask(Task task);

    /**
     * 查询设备维修次数
     *
     * @param innerCode 设备编号
     * @return 维修次数
     */
    int selectMaintenanceCountByInnerCode(String innerCode);

    /**
     * 校验当前用户是否有权操作该工单
     * 规则：工单管理员可操作所有工单；普通用户只能操作自己创建或指派给自己的工单
     *
     * @param taskId 工单ID
     */
    void checkTaskPermission(Long taskId);
}
