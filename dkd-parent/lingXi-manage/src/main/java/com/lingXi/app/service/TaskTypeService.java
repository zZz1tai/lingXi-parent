package com.lingXi.app.service;

import com.lingXi.app.domain.AppTaskType;

import java.util.List;

public interface TaskTypeService {
    /**
     * 查询所有任务类型
     * @return 任务类型列表
     */
    List<AppTaskType> list();
}