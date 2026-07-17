package com.lingXi.app.service.impl;

import com.lingXi.app.domain.AppTaskStatus;
import com.lingXi.app.mapper.AppTaskStatusMapper;
import com.lingXi.app.service.TaskStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service("appTaskStatusServiceImpl")
public class TaskStatusServiceImpl implements TaskStatusService {

    @Autowired
    private AppTaskStatusMapper taskStatusMapper;

    @Override
    public List<AppTaskStatus> list() {
        return taskStatusMapper.selectList();
    }
}