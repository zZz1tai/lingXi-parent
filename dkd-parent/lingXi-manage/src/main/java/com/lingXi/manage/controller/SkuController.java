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
import com.lingXi.manage.domain.Sku;
import com.lingXi.manage.service.ISkuService;
import com.lingXi.common.utils.poi.ExcelUtil;
import com.lingXi.common.core.page.TableDataInfo;
import org.springframework.web.multipart.MultipartFile;

/**
 * 商品管理Controller
 * 
 * @author itzhou
 * @date 2025-08-29
 */
@Tag(name = "商品管理")
@RestController
@RequestMapping("/manage/sku")
public class SkuController extends BaseController
{
    @Autowired
    private ISkuService skuService;

    /**
     * 查询商品管理列表
     */
    @Operation(summary = "查询商品管理列表")
    @PreAuthorize("@ss.hasPermi('manage:sku:list')")
    @GetMapping("/list")
    public TableDataInfo list(Sku sku)
    {
        startPage();
        List<Sku> list = skuService.selectSkuList(sku);
        return getDataTable(list);
    }

    /**
     * 导出商品管理列表
     */
    @Operation(summary = "导出商品管理列表")
    @PreAuthorize("@ss.hasPermi('manage:sku:export')")
    @Log(title = "商品管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, Sku sku)
    {
        List<Sku> list = skuService.selectSkuList(sku);
        ExcelUtil<Sku> util = new ExcelUtil<Sku>(Sku.class);
        util.exportEasyExcel(response, list, "商品管理数据");
    }

    /**
     * 获取商品管理详细信息
     */
    @Operation(summary = "获取商品管理详细信息")
    @PreAuthorize("@ss.hasPermi('manage:sku:query')")
    @GetMapping(value = "/{skuId}")
    public AjaxResult getInfo(@PathVariable("skuId") Long skuId)
    {
        return success(skuService.selectSkuBySkuId(skuId));
    }

    /**
     * 新增商品管理
     */
    @Operation(summary = "新增商品管理")
    @PreAuthorize("@ss.hasPermi('manage:sku:add')")
    @Log(title = "商品管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody Sku sku)
    {
        return toAjax(skuService.insertSku(sku));
    }

    /**
     * 修改商品管理
     */
    @Operation(summary = "修改商品管理")
    @PreAuthorize("@ss.hasPermi('manage:sku:edit')")
    @Log(title = "商品管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody Sku sku)
    {
        return toAjax(skuService.updateSku(sku));
    }

    /**
     * 删除商品管理
     */
    @Operation(summary = "删除商品管理")
    @PreAuthorize("@ss.hasPermi('manage:sku:remove')")
    @Log(title = "商品管理", businessType = BusinessType.DELETE)
	@DeleteMapping("/{skuIds}")
    public AjaxResult remove(@PathVariable Long[] skuIds)
    {
        return toAjax(skuService.deleteSkuBySkuIds(skuIds));
    }

    /**
     * 导入商品管理列表
     */
    @Operation(summary = "导入商品管理列表")
    @PreAuthorize("@ss.hasPermi('manage:sku:add')")
    @Log(title = "商品管理", businessType = BusinessType.IMPORT)
    @PostMapping("/import")
    public AjaxResult excelImport(MultipartFile file) throws Exception
    {
        ExcelUtil<Sku> util = new ExcelUtil<Sku>(Sku.class);
        List<Sku> skuList = util.importEasyExcel(file.getInputStream());
        return toAjax(skuService.insertSkuBatch(skuList));

    }
}
