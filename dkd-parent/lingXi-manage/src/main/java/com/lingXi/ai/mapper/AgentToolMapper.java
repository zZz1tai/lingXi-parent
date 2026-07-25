package com.lingXi.ai.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** 只供 Agent Tool Gateway 使用的区域化、聚合化查询。 */
@Mapper
public interface AgentToolMapper {

    Map<String, Object> selectSalesSummary(
            @Param("start") String start,
            @Param("endExclusive") String endExclusive,
            @Param("regionId") Long regionId);

    List<Map<String, Object>> selectSalesTrend(
            @Param("start") String start,
            @Param("endExclusive") String endExclusive,
            @Param("regionId") Long regionId,
            @Param("granularity") String granularity);

    Map<String, Object> selectTaskStatistics(
            @Param("start") String start,
            @Param("endExclusive") String endExclusive,
            @Param("regionId") Long regionId,
            @Param("taskType") Integer taskType);

    int countAbnormalDevices(@Param("regionId") Long regionId);

    List<Map<String, Object>> selectAbnormalDevices(
            @Param("regionId") Long regionId,
            @Param("limit") int limit);

    Map<String, Object> selectDeviceByInnerCodeAndRegion(
            @Param("innerCode") String innerCode,
            @Param("regionId") Long regionId);
}
