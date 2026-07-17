package com.lingXi.app.mapper;

import com.lingXi.app.domain.AppTaskStatus;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AppTaskStatusMapper {
    /**
     * 查询所有工单状态
     * @return 工单状态列表
     */
    List<AppTaskStatus> selectList();
}