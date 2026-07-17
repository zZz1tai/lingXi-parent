package com.lingXi.app.service.impl;

import com.lingXi.app.domain.AppTaskType;
import com.lingXi.app.mapper.AppTaskTypeMapper;
import com.lingXi.app.service.TaskTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service("appTaskTypeServiceImpl")
public class TaskTypeServiceImpl implements TaskTypeService {

    @Autowired
    private AppTaskTypeMapper taskTypeMapper;

    @Override
    public List<AppTaskType> list() {
        return taskTypeMapper.selectList();
    }
}