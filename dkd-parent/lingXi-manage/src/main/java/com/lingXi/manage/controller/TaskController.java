package com.lingXi.manage.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;

import com.lingXi.manage.domain.dto.TaskDto;
import com.lingXi.manage.domain.vo.TaskVo;
import com.lingXi.common.utils.SecurityUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lingXi.common.annotation.Log;
import com.lingXi.common.core.controller.BaseController;
import com.lingXi.common.core.domain.AjaxResult;
import com.lingXi.common.enums.BusinessType;
import com.lingXi.manage.domain.Task;
import com.lingXi.manage.service.ITaskService;
import com.lingXi.common.utils.poi.ExcelUtil;
import com.lingXi.common.core.page.TableDataInfo;

/**
 * 工单Controller
 * 
 * @author itzhou
 * @date 2025-09-01
 */
@RestController
@RequestMapping("/manage/task")
public class TaskController extends BaseController
{
    @Autowired
    private ITaskService taskService;

    /**
     * 查询工单列表
     * 非管理员只能看到自己指派的工单
     */
    @GetMapping("/list")
    public TableDataInfo list(Task task)
    {
        startPage();
        // 非管理员只查看自己的工单
        Long userId = SecurityUtils.getUserId();
        if (userId != null && userId != 1L) {
            task.setAssignorId(userId);
        }
        List<TaskVo> voList = taskService.selectTaskVoList(task);
        return getDataTable(voList);
    }

    /**
     * 导出工单列表
     */
    @Log(title = "工单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, Task task)
    {
        List<Task> list = taskService.selectTaskList(task);
        ExcelUtil<Task> util = new ExcelUtil<Task>(Task.class);
        util.exportExcel(response, list, "工单数据");
    }

    /**
     * 获取工单详细信息
     */
    @GetMapping(value = "/{taskId}")
    public AjaxResult getInfo(@PathVariable("taskId") Long taskId)
    {
        // 校验当前用户是否有权查看该工单
        taskService.checkTaskPermission(taskId);
        return success(taskService.selectTaskByTaskId(taskId));
    }

    /**
     * 新增工单
     */
    @Log(title = "工单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody TaskDto taskDto)
    {
        // 设置指派人（登录用户）id
        taskDto.setAssignorId(getUserId());
        return toAjax(taskService.insertTaskDto(taskDto));
    }

    /**
     * 修改工单
     */
    @Log(title = "工单", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody Task task)
    {
        // 校验当前用户是否有权操作该工单
        taskService.checkTaskPermission(task.getTaskId());
        return toAjax(taskService.updateTask(task));
    }

    /**
     * 删除工单
     */
    @Log(title = "工单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{taskIds}")
    public AjaxResult remove(@PathVariable Long[] taskIds)
    {
        return toAjax(taskService.deleteTaskByTaskIds(taskIds));
    }

    /**
     * 取消工单
     */
    @Log(title = "工单", businessType = BusinessType.UPDATE)
    @PutMapping("/cancel")
    public AjaxResult cancel(@RequestBody Task task)
    {
        // 校验当前用户是否有权操作该工单
        taskService.checkTaskPermission(task.getTaskId());
        return toAjax(taskService.cancelTask(task));
    }

    /**
     * 查询设备维修次数
     */
    @GetMapping("/maintenance-count/{innerCode}")
    public AjaxResult getMaintenanceCount(@PathVariable("innerCode") String innerCode)
    {
        int count = taskService.selectMaintenanceCountByInnerCode(innerCode);
        return success(count);
    }
}
