package com.lingXi.manage.mapper;

import java.util.List;
import com.lingXi.manage.domain.Task;
import com.lingXi.manage.domain.vo.TaskVo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工单Mapper接口
 * 
 * @author itzhou
 * @date 2025-09-01
 */
@Mapper

public interface TaskMapper 
{
    /**
     * 查询工单
     * 
     * @param taskId 工单主键
     * @return 工单
     */
    public Task selectTaskByTaskId(Long taskId);

    /** 根据 AI 受控动作ID查询幂等创建的工单。 */
    public Task selectTaskByAgentActionId(String agentActionId);

    /** 查询设备指定类型的待处理或进行中工单数量。 */
    public int countUnfinishedTasks(
            @org.apache.ibatis.annotations.Param("innerCode") String innerCode,
            @org.apache.ibatis.annotations.Param("productTypeId") Long productTypeId);

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
     * 删除工单
     * 
     * @param taskId 工单主键
     * @return 结果
     */
    public int deleteTaskByTaskId(Long taskId);

    /**
     * 批量删除工单
     * 
     * @param taskIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteTaskByTaskIds(Long[] taskIds);


    /**
     * 查询工单列表
     *
     * @param task 工单
     * @return 工单集合
     */
    List<TaskVo> selectTaskVoList(Task task);
}
