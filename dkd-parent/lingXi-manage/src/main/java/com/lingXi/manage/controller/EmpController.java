package com.lingXi.manage.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;

import com.lingXi.manage.domain.VendingMachine;
import com.lingXi.manage.service.IVendingMachineService;
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
import com.lingXi.manage.domain.Emp;
import com.lingXi.manage.service.IEmpService;
import com.lingXi.common.utils.poi.ExcelUtil;
import com.lingXi.common.core.page.TableDataInfo;

import static com.lingXi.common.constant.DkdContants.*;

/**
 * 人员列表Controller
 * 
 * @author itzhou
 * @date 2025-08-26
 */
@Tag(name = "人员列表管理")
@RestController
@RequestMapping("/manage/emp")
public class EmpController extends BaseController
{
    @Autowired
    private IEmpService empService;
    @Autowired
    private IVendingMachineService vendingMachineService;

    /**
     * 查询人员列表列表
     */
    @Operation(summary = "查询人员列表列表")
    @PreAuthorize("@ss.hasPermi('manage:emp:list')")
    @GetMapping("/list")
    public TableDataInfo list(Emp emp)
    {
        startPage();
        List<Emp> list = empService.selectEmpList(emp);
        return getDataTable(list);
    }

    /**
     * 导出人员列表列表
     */
    @Operation(summary = "导出人员列表列表")
    @PreAuthorize("@ss.hasPermi('manage:emp:export')")
    @Log(title = "人员列表", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, Emp emp)
    {
        List<Emp> list = empService.selectEmpList(emp);
        ExcelUtil<Emp> util = new ExcelUtil<Emp>(Emp.class);
        util.exportExcel(response, list, "人员列表数据");
    }

    /**
     * 获取人员列表详细信息
     */
    @Operation(summary = "获取人员列表详细信息")
    @PreAuthorize("@ss.hasPermi('manage:emp:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(empService.selectEmpById(id));
    }

    /**
     * 新增人员列表
     */
    @Operation(summary = "新增人员列表")
    @PreAuthorize("@ss.hasPermi('manage:emp:add')")
    @Log(title = "人员列表", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody Emp emp)
    {
        return toAjax(empService.insertEmp(emp));
    }

    /**
     * 修改人员列表
     */
    @Operation(summary = "修改人员列表")
    @PreAuthorize("@ss.hasPermi('manage:emp:edit')")
    @Log(title = "人员列表", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody Emp emp)
    {
        return toAjax(empService.updateEmp(emp));
    }

    /**
     * 删除人员列表
     */
    @Operation(summary = "删除人员列表")
    @PreAuthorize("@ss.hasPermi('manage:emp:remove')")
    @Log(title = "人员列表", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(empService.deleteEmpByIds(ids));
    }


    /**
     * 根据售货机获取维修人员列表
     */
    @Operation(summary = "根据售货机获取维修人员列表")
    @PreAuthorize("@ss.hasPermi('manage:emp:list')")
    @GetMapping("/operationList/{innerCode}")
    public AjaxResult getOperationListByInnerCode(@PathVariable("innerCode") String innerCode)
    {
        VendingMachine vendingMachine = vendingMachineService.selectVendingMachineByInnerCode(innerCode);
        if (vendingMachine == null){
            return error("售货机不存在");
        }
        //根据区域ID编号查询人员列表
        Emp emp = new Emp();
        //区域ID
        emp.setRegionId(vendingMachine.getRegionId());
        //启用状态人员
        emp.setStatus(EMP_STATUS_NORMAL);
        //维修人员
        emp.setRoleCode(ROLE_CODE_OPERATOR);
        List<Emp> list = empService.selectEmpList(emp);
        return success(list);
    }

    /**
     * 根据售货机获取运营人员列表
     */
    @Operation(summary = "根据售货机获取运营人员列表")
    @PreAuthorize("@ss.hasPermi('manage:emp:list')")
    @GetMapping("/businessList/{innerCode}")
    public AjaxResult getBusinessListByInnerCode(@PathVariable("innerCode") String innerCode)
    {
        VendingMachine vendingMachine = vendingMachineService.selectVendingMachineByInnerCode(innerCode);
        if (vendingMachine == null){
            return error("售货机不存在");
        }
        //根据区域ID编号查询人员列表
        Emp emp = new Emp();
        //区域ID
        emp.setRegionId(vendingMachine.getRegionId());
        //启用状态人员
        emp.setStatus(EMP_STATUS_NORMAL);
        //维修人员
        emp.setRoleCode(ROLE_CODE_BUSINESS);
        List<Emp> list = empService.selectEmpList(emp);
        return success(list);
    }
}
