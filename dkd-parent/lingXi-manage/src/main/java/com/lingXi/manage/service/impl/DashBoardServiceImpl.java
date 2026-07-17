package com.lingXi.manage.service.impl;

import com.lingXi.manage.mapper.DashBoardMapper;
import com.lingXi.manage.service.IDashBoardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 首页统计查询ServiceImpl
 *
 * @author itzhou
 * @date 2025-11-27
 */
@Service
public class DashBoardServiceImpl implements IDashBoardService {

    @Autowired
    private DashBoardMapper dashBoardMapper;

    /**
     * 获取工单统计
     */
    @Override
    public List<Map<String, Object>> getTaskStats(String start, String end) {
        List<Map<String, Object>> taskStats = new ArrayList<>();
        
        // 从Mapper获取工单数据
        List<Map<String, Object>> tasks = dashBoardMapper.getTaskStats(start, end);
        
        // 统计维修工单
        int repairTotal = 0;
        int repairCompleted = 0;
        int repairCanceled = 0;
        int repairProgress = 0;
        Set<Integer> repairWorkers = new HashSet<>();
        
        // 统计非维修工单
        int nonRepairTotal = 0;
        int nonRepairCompleted = 0;
        int nonRepairCanceled = 0;
        int nonRepairProgress = 0;
        Set<Integer> nonRepairWorkers = new HashSet<>();
        
        // 遍历工单数据进行统计
        for (Map<String, Object> task : tasks) {
            Integer taskType = (Integer) task.get("task_type");
            Integer taskStatus = (Integer) task.get("task_status");
            Integer userId = (Integer) task.get("user_id");
            
            if (taskType == 1) {
                // 维修工单
                repairTotal++;
                if (taskStatus == 4) repairCompleted++;
                if (taskStatus == 3) repairCanceled++;
                if (taskStatus == 1) repairProgress++;
                if (userId != null) repairWorkers.add(userId);
            } else {
                // 非维修工单
                nonRepairTotal++;
                if (taskStatus == 4) nonRepairCompleted++;
                if (taskStatus == 3) nonRepairCanceled++;
                if (taskStatus == 1) nonRepairProgress++;
                if (userId != null) nonRepairWorkers.add(userId);
            }
        }
        
        // 维修工单统计结果
        Map<String, Object> repairTask = new HashMap<>();
        repairTask.put("total", repairTotal);
        repairTask.put("completedTotal", repairCompleted);
        repairTask.put("cancelTotal", repairCanceled);
        repairTask.put("progressTotal", repairProgress);
        repairTask.put("workerCount", repairWorkers.size());
        repairTask.put("repair", true);
        repairTask.put("date", null);
        taskStats.add(repairTask);
        
        // 非维修工单统计结果
        Map<String, Object> nonRepairTask = new HashMap<>();
        nonRepairTask.put("total", nonRepairTotal);
        nonRepairTask.put("completedTotal", nonRepairCompleted);
        nonRepairTask.put("cancelTotal", nonRepairCanceled);
        nonRepairTask.put("progressTotal", nonRepairProgress);
        nonRepairTask.put("workerCount", nonRepairWorkers.size());
        nonRepairTask.put("repair", false);
        nonRepairTask.put("date", null);
        taskStats.add(nonRepairTask);
        
        return taskStats;
    }

    /**
     * 获取销售统计
     */
    @Override
    public Map<String, Object> getSaleStats(String start, String end) {
        return dashBoardMapper.getSaleStats(start, end);
    }

    /**
     * 获取SKU销售排名
     */
    @Override
    public List<Map<String, Object>> getSkuSaleRank(String start, String end) {
        return dashBoardMapper.getSkuSaleRank(start, end);
    }

    /**
     * 获取SKU销售汇总
     */
    @Override
    public Map<String, Object> getSkuSaleCollect(String start, String end, Integer collectType) {
        Map<String, Object> result = new java.util.HashMap<>();
        
        // 获取折线图数据
        List<String> lineXAxisData = dashBoardMapper.getSkuSaleLineXAxisData(start, end, collectType);
        List<Integer> lineSeriesData = dashBoardMapper.getSkuSaleLineSeriesData(start, end, collectType);
        result.put("lineXAxisData", lineXAxisData);
        result.put("lineSeriesData", lineSeriesData);
        
        // 获取柱状图数据
        List<String> barXAxisData = dashBoardMapper.getSkuSaleBarXAxisData(start, end, collectType);
        List<Integer> barSeriesData = dashBoardMapper.getSkuSaleBarSeriesData(start, end, collectType);
        result.put("barXAxisData", barXAxisData);
        result.put("barSeriesData", barSeriesData);
        
        return result;
    }

    /**
     * 获取合作商节点汇总
     */
    @Override
    public Map<String, Object> getPartnerNodeCollect() {
        Map<String, Object> result = new java.util.HashMap<>();
        
        // 获取饼图数据
        List<Map<String, Object>> seriesData = dashBoardMapper.getPartnerNodeSeriesData();
        result.put("seriesData", seriesData);
        
        // 获取统计数据
        Integer totalNodes = dashBoardMapper.getTotalNodes();
        Integer totalPartners = dashBoardMapper.getTotalPartners();
        result.put("totalNodes", totalNodes);
        result.put("totalPartners", totalPartners);
        
        return result;
    }

    /**
     * 获取异常设备列表
     */
    @Override
    public List<Map<String, Object>> getAbnormalEquipment() {
        return dashBoardMapper.getAbnormalEquipment();
    }
}