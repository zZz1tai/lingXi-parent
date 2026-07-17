package com.lingXi.app.mapper;

import com.lingXi.app.domain.AppTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AppTaskMapper {
    /**
     * 分页查询工单
     * @param params 查询参数
     * @return 工单列表
     */
    List<AppTask> selectPage(@Param("params") Map<String, Object> params);

    /**
     * 查询工单总数
     * @param params 查询参数
     * @return 工单总数
     */
    Long selectCount(@Param("params") Map<String, Object> params);

    /**
     * 根据ID查询工单
     * @param id 工单ID
     * @return 工单信息
     */
    AppTask selectById(@Param("id") Long id);

    /**
     * 更新工单
     * @param appTask 工单信息
     * @return 更新结果
     */
    int updateById(@Param("task") AppTask appTask);
}