package com.lingXi.manage.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 首页统计查询Mapper
 *
 * @author itzhou
 * @date 2025-11-27
 */
@Mapper
public interface DashBoardMapper {

    /**
     * 获取工单统计
     */
    List<Map<String, Object>> getTaskStats(@Param("start") String start, @Param("end") String end);

    /**
     * 获取销售统计
     */
    Map<String, Object> getSaleStats(@Param("start") String start, @Param("end") String end);

    /**
     * 获取SKU销售排名
     */
    List<Map<String, Object>> getSkuSaleRank(@Param("start") String start, @Param("end") String end);

    /**
     * 获取SKU销售折线图X轴数据
     */
    List<String> getSkuSaleLineXAxisData(@Param("start") String start, @Param("end") String end, @Param("collectType") Integer collectType);

    /**
     * 获取SKU销售折线图系列数据
     */
    List<Integer> getSkuSaleLineSeriesData(@Param("start") String start, @Param("end") String end, @Param("collectType") Integer collectType);

    /**
     * 获取SKU销售柱状图X轴数据
     */
    List<String> getSkuSaleBarXAxisData(@Param("start") String start, @Param("end") String end, @Param("collectType") Integer collectType);

    /**
     * 获取SKU销售柱状图系列数据
     */
    List<Integer> getSkuSaleBarSeriesData(@Param("start") String start, @Param("end") String end, @Param("collectType") Integer collectType);

    /**
     * 获取合作商节点饼图数据
     */
    List<Map<String, Object>> getPartnerNodeSeriesData();

    /**
     * 获取总节点数
     */
    Integer getTotalNodes();

    /**
     * 获取总合作商数
     */
    Integer getTotalPartners();

    /**
     * 获取异常设备列表
     */
    List<Map<String, Object>> getAbnormalEquipment();
}