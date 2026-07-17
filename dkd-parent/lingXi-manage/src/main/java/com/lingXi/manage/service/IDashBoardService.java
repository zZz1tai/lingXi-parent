package com.lingXi.manage.service;

import java.util.List;
import java.util.Map;

/**
 * 首页统计查询Service
 *
 * @author itzhou
 * @date 2025-11-27
 */
public interface IDashBoardService {

    /**
     * 获取工单统计
     */
    List<Map<String, Object>> getTaskStats(String start, String end);

    /**
     * 获取销售统计
     */
    Map<String, Object> getSaleStats(String start, String end);

    /**
     * 获取SKU销售排名
     */
    List<Map<String, Object>> getSkuSaleRank(String start, String end);

    /**
     * 获取SKU销售汇总
     */
    Map<String, Object> getSkuSaleCollect(String start, String end, Integer collectType);

    /**
     * 获取合作商节点汇总
     */
    Map<String, Object> getPartnerNodeCollect();

    /**
     * 获取异常设备列表
     */
    List<Map<String, Object>> getAbnormalEquipment();
}