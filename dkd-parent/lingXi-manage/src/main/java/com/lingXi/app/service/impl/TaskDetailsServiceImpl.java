package com.lingXi.app.service.impl;

import com.lingXi.app.domain.AppTaskDetails;
import com.lingXi.app.mapper.AppTaskDetailsMapper;
import com.lingXi.app.service.TaskDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service("appTaskDetailsServiceImpl")
public class TaskDetailsServiceImpl implements TaskDetailsService {

    @Autowired
    private AppTaskDetailsMapper taskDetailsMapper;

    @Override
    public List<AppTaskDetails> getByTaskId(Long taskId) {
        return taskDetailsMapper.selectByTaskId(taskId);
    }
}