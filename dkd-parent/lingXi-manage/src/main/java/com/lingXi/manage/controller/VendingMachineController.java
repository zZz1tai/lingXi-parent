package com.lingXi.manage.controller;

import java.util.List;
import javax.servlet.http.HttpServletResponse;

import com.lingXi.manage.domain.VendingMachine;
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
import org.springframework.web.bind.annotation.RequestParam;
import com.lingXi.common.annotation.Log;
import com.lingXi.common.core.controller.BaseController;
import com.lingXi.common.core.domain.AjaxResult;
import com.lingXi.common.enums.BusinessType;
import com.lingXi.manage.service.IVendingMachineService;
import com.lingXi.manage.service.IChannelService;
import com.lingXi.common.utils.poi.ExcelUtil;
import com.lingXi.common.core.page.TableDataInfo;

/**
 * 设备管理Controller
 * 
 * @author itzhou
 * @date 2025-08-26
 */
@Api(tags = "设备管理")
@RestController
@RequestMapping("/manage/vm")
public class VendingMachineController extends BaseController
{
    @Autowired
    private IVendingMachineService vendingMachineService;
    
    @Autowired
    private IChannelService channelService;

    /**
     * 查询设备管理列表
     */
    @ApiOperation("查询设备管理列表")
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(required = false) String id,
                              @RequestParam(required = false) String innerCode,
                              @RequestParam(required = false) String nodeId,
                              @RequestParam(required = false) String partnerId,
                              @RequestParam(required = false) String vmTypeId,
                              @RequestParam(required = false) String vmStatus)
    {
        VendingMachine vendingMachine = new VendingMachine();
        // 处理 'all' 参数，忽略它
        if (id != null && !"all".equals(id)) {
            vendingMachine.setId(Long.valueOf(id));
        }
        vendingMachine.setInnerCode(innerCode);
        if (nodeId != null) {
            vendingMachine.setNodeId(Long.valueOf(nodeId));
        }
        if (partnerId != null) {
            vendingMachine.setPartnerId(Long.valueOf(partnerId));
        }
        if (vmTypeId != null) {
            vendingMachine.setVmTypeId(Long.valueOf(vmTypeId));
        }
        if (vmStatus != null) {
            vendingMachine.setVmStatus(Long.valueOf(vmStatus));
        }
        List<VendingMachine> list = vendingMachineService.selectVendingMachineList(vendingMachine);
        return getDataTable(list);
    }

    /**
     * 导出设备管理列表
     */
    @ApiOperation("导出设备管理列表")
    @PreAuthorize("@ss.hasPermi('manage:vm:export')")
    @Log(title = "设备管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, VendingMachine vendingMachine)
    {
        List<VendingMachine> list = vendingMachineService.selectVendingMachineList(vendingMachine);
        ExcelUtil<VendingMachine> util = new ExcelUtil<VendingMachine>(VendingMachine.class);
        util.exportExcel(response, list, "设备管理数据");
    }

    /**
     * 获取所有售货机列表（无需权限）
     */
    @ApiOperation("获取所有售货机列表")
    @GetMapping("/all")
    public AjaxResult getAllVendingMachines()
    {
        List<VendingMachine> list = vendingMachineService.selectVendingMachineList(new VendingMachine());
        return success(list);
    }
    
    /**
     * 新增设备管理
     */
    @ApiOperation("新增设备管理")
    @PreAuthorize("@ss.hasPermi('manage:vm:add')")
    @Log(title = "设备管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody VendingMachine vendingMachine)
    {
        return toAjax(vendingMachineService.insertVendingMachine(vendingMachine));
    }

    /**
     * 修改设备管理
     */
    @ApiOperation("修改设备管理")
    @PreAuthorize("@ss.hasPermi('manage:vm:edit')")
    @Log(title = "设备管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody VendingMachine vendingMachine)
    {
        return toAjax(vendingMachineService.updateVendingMachine(vendingMachine));
    }

    /**
     * 删除设备管理
     */
    @ApiOperation("删除设备管理")
    @PreAuthorize("@ss.hasPermi('manage:vm:remove')")
    @Log(title = "设备管理", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(vendingMachineService.deleteVendingMachineByIds(ids));
    }
    
    /**
     * 获取设备管理详细信息
     */
    @ApiOperation("获取设备管理详细信息")
    @PreAuthorize("@ss.hasPermi('manage:vm:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(vendingMachineService.selectVendingMachineById(id));
    }
    
    /**
     * 根据设备ID查询货道信息
     */
    @ApiOperation("根据设备ID查询货道信息")
    @GetMapping("/channels/{vmId}")
    public AjaxResult getChannelsByVmId(@PathVariable("vmId") Long vmId)
    {
        VendingMachine vm = vendingMachineService.selectVendingMachineById(vmId);
        if (vm == null) {
            return error("设备不存在");
        }
        return success(channelService.selectChannelVoListByInnerCode(vm.getInnerCode()));
    }
}
