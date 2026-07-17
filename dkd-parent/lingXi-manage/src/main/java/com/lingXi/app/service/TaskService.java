package com.lingXi.app.service;

import com.lingXi.app.domain.AppTask;
import com.lingXi.app.domain.dto.CancelTaskDto;
import com.lingXi.app.domain.vo.Pager;
import com.lingXi.app.domain.vo.TaskSearchVo;

import java.util.Map;

/**
 * 工单业务逻辑
 */
public interface TaskService {

    // 通过条件搜索工单列表
    Pager<AppTask> search(TaskSearchVo taskSearchVo);

    // 接受工单
    Boolean accept(Long taskId, Long userId);

    // 拒绝/取消工单
    Boolean cancel(Long taskId, CancelTaskDto cancelTaskDto, Long userId);

    // 完成工单
    Boolean complete(Long taskId, Long userId);

    // 获取用户排名
    Map<String, Object> getRank(Long userId);

    // 检查用户是否绑定员工
    void checkEmpBinding();

    // 检查用户是否具有工单处理权限
    void checkTaskPermission(Long taskId);
}