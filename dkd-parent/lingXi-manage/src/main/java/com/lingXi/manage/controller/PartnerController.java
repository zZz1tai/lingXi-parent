package com.lingXi.manage.controller;

import java.util.List;
import javax.servlet.http.HttpServletResponse;

import com.lingXi.common.utils.SecurityUtils;
import com.lingXi.manage.domain.vo.PartnerVo;
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
import com.lingXi.manage.domain.Partner;
import com.lingXi.manage.service.IPartnerService;
import com.lingXi.common.utils.poi.ExcelUtil;
import com.lingXi.common.core.page.TableDataInfo;

/**
 * 合作商管理Controller
 * 
 * @author itzhou
 * @date 2025-08-24
 */
@Api(tags = "合作商管理")
@RestController
@RequestMapping("/manage/partner")
public class PartnerController extends BaseController
{
    @Autowired
    private IPartnerService partnerService;

    /**
     * 查询合作商管理列表
     */
    @ApiOperation("查询合作商管理列表")
    @PreAuthorize("@ss.hasPermi('manage:partner:list')")
    @GetMapping("/list")
    public TableDataInfo list(Partner partner)
    {
        startPage();
        List<PartnerVo> volist = partnerService.selectPartnerListVo( partner);
        return getDataTable(volist);
    }

    /**
     * 导出合作商管理列表
     */
    @ApiOperation("导出合作商管理列表")
    @PreAuthorize("@ss.hasPermi('manage:partner:export')")
    @Log(title = "合作商管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, Partner partner)
    {
        List<Partner> list = partnerService.selectPartnerList(partner);
        ExcelUtil<Partner> util = new ExcelUtil<Partner>(Partner.class);
        util.exportExcel(response, list, "合作商管理数据");
    }

    /**
     * 获取合作商管理详细信息
     */
    @ApiOperation("获取合作商管理详细信息")
    @PreAuthorize("@ss.hasPermi('manage:partner:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(partnerService.selectPartnerById(id));
    }

    /**
     * 新增合作商管理
     */
    @ApiOperation("新增合作商管理")
    @PreAuthorize("@ss.hasPermi('manage:partner:add')")
    @Log(title = "合作商管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody Partner partner)
    {
        return toAjax(partnerService.insertPartner(partner));
    }

    /**
     * 修改合作商管理
     */
    @ApiOperation("修改合作商管理")
    @PreAuthorize("@ss.hasPermi('manage:partner:edit')")
    @Log(title = "合作商管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody Partner partner)
    {
        return toAjax(partnerService.updatePartner(partner));
    }

    /**
     * 删除合作商管理
     */
    @ApiOperation("删除合作商管理")
    @PreAuthorize("@ss.hasPermi('manage:partner:remove')")
    @Log(title = "合作商管理", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(partnerService.deletePartnerByIds(ids));
    }

    /**
     * 重置合作商的密码
     */
    @ApiOperation("重置合作商的密码")
    @PreAuthorize("@ss.hasPermi('manage:partner:resetPwd')")
    @Log(title = "合作商管理", businessType = BusinessType.UPDATE)
    @PutMapping("/resetPwd/{id}")
    public AjaxResult resetPwd(@PathVariable Long id)
    {
        //2.创建对象
        Partner partner = new Partner();
        partner.setId(id);
        partner.setPassword(SecurityUtils.encryptPassword("123456"));//设置加密后的初始密码密码123456
        return toAjax(partnerService.updatePartner( partner));
    }
}
