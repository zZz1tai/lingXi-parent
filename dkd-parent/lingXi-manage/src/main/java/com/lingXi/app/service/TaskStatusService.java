package com.lingXi.app.service;

import com.lingXi.app.domain.AppTaskStatus;

import java.util.List;

public interface TaskStatusService {
    /**
     * 查询所有工单状态
     * @return 工单状态列表
     */
    List<AppTaskStatus> list();
}