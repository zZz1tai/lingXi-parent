package com.lingXi.manage.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;

import com.lingXi.common.core.domain.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import com.lingXi.manage.domain.TaskDetails;
import com.lingXi.manage.service.ITaskDetailsService;
import com.lingXi.common.utils.poi.ExcelUtil;
import com.lingXi.common.core.page.TableDataInfo;

/**
 * 工单详情Controller
 *
 * @author itzhou
 * @date 2025-09-01
 */
@Tag(name = "工单详情管理")
@RestController
@RequestMapping("/manage/taskDetails")
public class TaskDetailsController extends BaseController
{
    @Autowired
    private ITaskDetailsService taskDetailsService;

    /**
     * 查询工单详情列表
     */
    @Operation(summary = "查询工单详情列表", description = "根据条件分页查询工单详情列表，支持多条件组合查询")
    @Parameter(name = "taskDetails", description = "工单详情查询条件对象", required = false)
    @PreAuthorize("@ss.hasPermi('manage:taskDetails:list')")
    @GetMapping("/list")
    public TableDataInfo list(TaskDetails taskDetails)
    {
        startPage();
        List<TaskDetails> list = taskDetailsService.selectTaskDetailsList(taskDetails);
        return getDataTable(list);
    }

    /**
     * 导出工单详情列表
     */
    @Operation(summary = "导出工单详情列表", description = "根据查询条件导出工单详情数据为Excel文件")
    @Parameter(name = "taskDetails", description = "工单详情查询条件对象", required = false)
    @PreAuthorize("@ss.hasPermi('manage:taskDetails:export')")
    @Log(title = "工单详情", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, TaskDetails taskDetails)
    {
        List<TaskDetails> list = taskDetailsService.selectTaskDetailsList(taskDetails);
        ExcelUtil<TaskDetails> util = new ExcelUtil<TaskDetails>(TaskDetails.class);
        util.exportExcel(response, list, "工单详情数据");
    }

    /**
     * 获取工单详情详细信息
     */
    @Operation(summary = "获取工单详情详细信息", description = "根据工单详情ID查询单个工单详情的详细信息")
    @Parameter(name = "detailsId", description = "工单详情ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermi('manage:taskDetails:query')")
    @GetMapping(value = "/{detailsId}")
    public AjaxResult getInfo(@PathVariable("detailsId") Long detailsId)
    {
        return success(taskDetailsService.selectTaskDetailsByDetailsId(detailsId));
    }

    /**
     * 新增工单详情
     */
    @Operation(summary = "新增工单详情", description = "创建新的工单详情记录，传入完整的工单详情信息")
    @Parameter(name = "taskDetails", description = "工单详情对象，包含所有必填字段", required = true)
    @PreAuthorize("@ss.hasPermi('manage:taskDetails:add')")
    @Log(title = "工单详情", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody TaskDetails taskDetails)
    {
        return toAjax(taskDetailsService.insertTaskDetails(taskDetails));
    }

    /**
     * 修改工单详情
     */
    @Operation(summary = "修改工单详情", description = "更新已存在的工单详情记录，需传入完整的工单详情信息")
    @Parameter(name = "taskDetails", description = "工单详情对象，包含ID和需要更新的字段", required = true)
    @PreAuthorize("@ss.hasPermi('manage:taskDetails:edit')")
    @Log(title = "工单详情", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody TaskDetails taskDetails)
    {
        return toAjax(taskDetailsService.updateTaskDetails(taskDetails));
    }

    /**
     * 删除工单详情
     */
    @Operation(summary = "删除工单详情", description = "批量删除工单详情记录，传入多个工单详情ID")
    @Parameter(name = "detailsIds", description = "工单详情ID数组，多个ID用逗号分隔", required = true, example = "1,2,3")
    @PreAuthorize("@ss.hasPermi('manage:taskDetails:remove')")
    @Log(title = "工单详情", businessType = BusinessType.DELETE)
    @DeleteMapping("/{detailsIds}")
    public AjaxResult remove(@PathVariable Long[] detailsIds)
    {
        return toAjax(taskDetailsService.deleteTaskDetailsByDetailsIds(detailsIds));
    }

    /**
     * 根据工单ID查询工单详情
     */
    @Operation(summary = "根据工单ID查询工单详情", description = "通过工单ID查询该工单下的所有详情记录")
    @Parameter(name = "taskId", description = "工单ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermi('manage:taskDetails:list')")
    @GetMapping("/byTaskId/{taskId}")
    public R<List<TaskDetails>> listByTaskId(@PathVariable("taskId") Long taskId)
    {
        TaskDetails taskDetails = new TaskDetails();
        taskDetails.setTaskId(taskId);
        return R.ok(taskDetailsService.selectTaskDetailsList(taskDetails));
    }
}
