package com.lingXi.app.service;

import com.lingXi.app.domain.AppTaskDetails;

import java.util.List;

public interface TaskDetailsService {

    // 根据工单id查询工单详情
    List<AppTaskDetails> getByTaskId(Long taskId);
}