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
import com.lingXi.manage.domain.Order;
import com.lingXi.manage.domain.Sku;
import com.lingXi.manage.domain.dto.RecommendQuery;
import com.lingXi.manage.service.IOrderService;
import com.lingXi.common.utils.poi.ExcelUtil;
import com.lingXi.common.core.page.TableDataInfo;

/**
 * 订单管理Controller
 * 
 * @author itheima
 * @date 2024-07-29
 */
@Api(tags = "订单管理")
@RestController
@RequestMapping("/manage/order")
public class OrderController extends BaseController
{
    @Autowired
    private IOrderService orderService;

    /**
     * 查询订单管理列表
     */
    @ApiOperation("查询订单管理列表")
    @PreAuthorize("@ss.hasPermi('manage:order:list')")
    @GetMapping("/list")
    public TableDataInfo list(Order order)
    {
        startPage();
        List<Order> list = orderService.selectOrderList(order);
        return getDataTable(list);
    }

    /**
     * 导出订单管理列表
     */
    @ApiOperation("导出订单管理列表")
    @PreAuthorize("@ss.hasPermi('manage:order:export')")
    @Log(title = "订单管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, Order order)
    {
        List<Order> list = orderService.selectOrderList(order);
        ExcelUtil<Order> util = new ExcelUtil<Order>(Order.class);
        util.exportExcel(response, list, "订单管理数据");
    }

    /**
     * 获取订单管理详细信息
     */
    @ApiOperation("获取订单管理详细信息")
    @PreAuthorize("@ss.hasPermi('manage:order:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(orderService.selectOrderById(id));
    }

    /**
     * 新增订单管理
     */
    @ApiOperation("新增订单管理")
    @PreAuthorize("@ss.hasPermi('manage:order:add')")
    @Log(title = "订单管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody Order order)
    {
        return toAjax(orderService.insertOrder(order));
    }

    /**
     * 修改订单管理
     */
    @ApiOperation("修改订单管理")
    @PreAuthorize("@ss.hasPermi('manage:order:edit')")
    @Log(title = "订单管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody Order order)
    {
        return toAjax(orderService.updateOrder(order));
    }

    /**
     * 删除订单管理
     */
    @ApiOperation("删除订单管理")
    @PreAuthorize("@ss.hasPermi('manage:order:remove')")
    @Log(title = "订单管理", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(orderService.deleteOrderByIds(ids));
    }

    /**
     * 获取当前用户的商品推荐列表
     * 基于历史订单分析购买偏好，返回推荐的skuId列表
     */
    @ApiOperation("获取用户商品推荐列表")
    @GetMapping("/recommend/{userName}")
    public AjaxResult recommend(@PathVariable("userName") String userName)
    {
        List<Long> recommendSkuIds = orderService.getRecommendSkuIds(userName);
        return success(recommendSkuIds);
    }

    /**
     * 混合推荐：综合用户偏好、类别偏好、区域上下文、库存等多维度打分排序
     * <p>
     * 支持参数：
     * - userName: 用户名（必填）
     * - regionId: 区域ID
     * - businessType: 商圈类型
     * - partnerId: 合作商ID
     * - nodeId: 点位ID
     * - innerCode: 设备编号（传入后优先推荐本机有货商品）
     * - limit: 返回数量（默认10，最大50）
     */
    @ApiOperation("混合推荐商品列表")
    @GetMapping("/recommend/hybrid")
    public AjaxResult hybridRecommend(RecommendQuery query)
    {
        List<Sku> recommendations = orderService.getHybridRecommendations(query);
        return success(recommendations);
    }
}