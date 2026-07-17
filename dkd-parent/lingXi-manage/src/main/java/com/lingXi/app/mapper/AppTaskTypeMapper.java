package com.lingXi.app.mapper;

import com.lingXi.app.domain.AppTaskType;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AppTaskTypeMapper {
    /**
     * 查询所有任务类型
     * @return 任务类型列表
     */
    List<AppTaskType> selectList();
}
