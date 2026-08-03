package com.lingXi.manage.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
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
import com.lingXi.manage.domain.VmType;
import com.lingXi.manage.service.IVmTypeService;
import com.lingXi.common.utils.poi.ExcelUtil;
import com.lingXi.common.core.page.TableDataInfo;

/**
 * 设备类型管理Controller
 * 
 * @author itzhou
 * @date 2025-08-26
 */
@Tag(name = "设备类型管理")
@RestController
@RequestMapping("/manage/vmType")
public class VmTypeController extends BaseController
{
    @Autowired
    private IVmTypeService vmTypeService;

    /**
     * 查询设备类型管理列表
     */
    @Operation(summary = "查询设备类型管理列表")
    @PreAuthorize("@ss.hasPermi('manage:vmType:list')")
    @GetMapping("/list")
    public TableDataInfo list(VmType vmType)
    {
        startPage();
        List<VmType> list = vmTypeService.selectVmTypeList(vmType);
        return getDataTable(list);
    }

    /**
     * 导出设备类型管理列表
     */
    @Operation(summary = "导出设备类型管理列表")
    @PreAuthorize("@ss.hasPermi('manage:vmType:export')")
    @Log(title = "设备类型管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, VmType vmType)
    {
        List<VmType> list = vmTypeService.selectVmTypeList(vmType);
        ExcelUtil<VmType> util = new ExcelUtil<VmType>(VmType.class);
        util.exportExcel(response, list, "设备类型管理数据");
    }

    /**
     * 获取设备类型管理详细信息
     */
    @Operation(summary = "获取设备类型管理详细信息")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(vmTypeService.selectVmTypeById(id));
    }

    /**
     * 新增设备类型管理
     */
    @Operation(summary = "新增设备类型管理")
    @PreAuthorize("@ss.hasPermi('manage:vmType:add')")
    @Log(title = "设备类型管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody VmType vmType)
    {
        return toAjax(vmTypeService.insertVmType(vmType));
    }

    /**
     * 修改设备类型管理
     */
    @Operation(summary = "修改设备类型管理")
    @PreAuthorize("@ss.hasPermi('manage:vmType:edit')")
    @Log(title = "设备类型管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody VmType vmType)
    {
        return toAjax(vmTypeService.updateVmType(vmType));
    }

    /**
     * 删除设备类型管理
     */
    @Operation(summary = "删除设备类型管理")
    @PreAuthorize("@ss.hasPermi('manage:vmType:remove')")
    @Log(title = "设备类型管理", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(vmTypeService.deleteVmTypeByIds(ids));
    }
}
