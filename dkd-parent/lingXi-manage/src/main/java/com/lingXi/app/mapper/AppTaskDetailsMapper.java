package com.lingXi.app.mapper;

import com.lingXi.app.domain.AppTaskDetails;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AppTaskDetailsMapper {
    /**
     * 根据工单ID查询工单详情
     * @param taskId 工单ID
     * @return 工单详情列表
     */
    List<AppTaskDetails> selectByTaskId(@Param("taskId") Long taskId);
}