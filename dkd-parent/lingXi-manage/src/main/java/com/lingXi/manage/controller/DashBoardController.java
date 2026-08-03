package com.lingXi.manage.controller;

import com.lingXi.manage.service.IDashBoardService;
import com.lingXi.common.core.domain.AjaxResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * 首页统计查询Controller
 *
 * @author itzhou
 * @date 2025-11-27
 */
@Tag(name = "首页统计查询")
@RestController
@RequestMapping("/manage/dashboard")
public class DashBoardController {

    @Autowired
    private IDashBoardService dashBoardService;

    /**
     * 获取工单统计
     */
    @Operation(summary = "获取工单统计")
    @GetMapping("/taskStats")
    public AjaxResult getTaskStats(@RequestParam(required = false) String start, @RequestParam(required = false) String end) {
        List<Map<String, Object>> taskStats = dashBoardService.getTaskStats(start, end);
        return AjaxResult.success(taskStats);
    }

    /**
     * 获取销售统计
     */
    @Operation(summary = "获取销售统计")
    @GetMapping("/saleStats")
    public AjaxResult getSaleStats(@RequestParam(required = false) String start, @RequestParam(required = false) String end) {
        Map<String, Object> saleStats = dashBoardService.getSaleStats(start, end);
        return AjaxResult.success(saleStats);
    }

    /**
     * 获取SKU销售排名
     */
    @Operation(summary = "获取SKU销售排名")
    @GetMapping("/skuSaleRank")
    public AjaxResult getSkuSaleRank(@RequestParam(required = false) String start, @RequestParam(required = false) String end) {
        List<Map<String, Object>> skuSaleRank = dashBoardService.getSkuSaleRank(start, end);
        return AjaxResult.success(skuSaleRank);
    }

    /**
     * 获取SKU销售汇总
     */
    @Operation(summary = "获取SKU销售汇总")
    @GetMapping("/skuSaleCollect")
    public AjaxResult getSkuSaleCollect(@RequestParam(required = false) String start,
                                                 @RequestParam(required = false) String end,
                                                 @RequestParam(required = false, defaultValue = "1") Integer collectType) {
        Map<String, Object> saleCollect = dashBoardService.getSkuSaleCollect(start, end, collectType);
        return AjaxResult.success(saleCollect);
    }

    /**
     * 获取合作商节点汇总
     */
    @Operation(summary = "获取合作商节点汇总")
    @GetMapping("/partnerNodeCollect")
    public AjaxResult getPartnerNodeCollect() {
        Map<String, Object> partnerNodeCollect = dashBoardService.getPartnerNodeCollect();
        return AjaxResult.success(partnerNodeCollect);
    }

    /**
     * 获取异常设备列表
     */
    @Operation(summary = "获取异常设备列表")
    @GetMapping("/abnormalEquipment")
    public AjaxResult getAbnormalEquipment() {
        List<Map<String, Object>> abnormalEquipment = dashBoardService.getAbnormalEquipment();
        return AjaxResult.success(abnormalEquipment);
    }
}