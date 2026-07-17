package com.lingXi.manage.controller;

import java.util.List;
import javax.servlet.http.HttpServletResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
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
import com.lingXi.manage.domain.TaskType;
import com.lingXi.manage.service.ITaskTypeService;
import com.lingXi.common.utils.poi.ExcelUtil;
import com.lingXi.common.core.page.TableDataInfo;

/**
 * 工单类型Controller
 * 
 * @author itzhou
 * @date 2025-09-01
 */
@Api(tags = "工单类型管理")
@RestController
@RequestMapping("/manage/taskType")
public class TaskTypeController extends BaseController
{
    @Autowired
    private ITaskTypeService taskTypeService;

    /**
     * 查询工单类型列表
     */
    @ApiOperation("查询工单类型列表")
    @GetMapping("/list")
    public TableDataInfo list(TaskType taskType)
    {
        startPage();
        List<TaskType> list = taskTypeService.selectTaskTypeList(taskType);
        return getDataTable(list);
    }

    /**
     * 导出工单类型列表
     */
    @ApiOperation("导出工单类型列表")
    @Log(title = "工单类型", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, TaskType taskType)
    {
        List<TaskType> list = taskTypeService.selectTaskTypeList(taskType);
        ExcelUtil<TaskType> util = new ExcelUtil<TaskType>(TaskType.class);
        util.exportExcel(response, list, "工单类型数据");
    }

    /**
     * 获取工单类型详细信息
     */
    @ApiOperation("获取工单类型详细信息")
    @GetMapping(value = "/{typeId}")
    public AjaxResult getInfo(@PathVariable("typeId") Long typeId)
    {
        return success(taskTypeService.selectTaskTypeByTypeId(typeId));
    }

    /**
     * 新增工单类型
     */
    @ApiOperation("新增工单类型")
    @Log(title = "工单类型", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody TaskType taskType)
    {
        return toAjax(taskTypeService.insertTaskType(taskType));
    }

    /**
     * 修改工单类型
     */
    @ApiOperation("修改工单类型")
    @Log(title = "工单类型", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody TaskType taskType)
    {
        return toAjax(taskTypeService.updateTaskType(taskType));
    }

    /**
     * 删除工单类型
     */
    @ApiOperation("删除工单类型")
    @Log(title = "工单类型", businessType = BusinessType.DELETE)
	@DeleteMapping("/{typeIds}")
    public AjaxResult remove(@PathVariable Long[] typeIds)
    {
        return toAjax(taskTypeService.deleteTaskTypeByTypeIds(typeIds));
    }
}
