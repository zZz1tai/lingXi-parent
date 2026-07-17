package com.lingXi.app.service.impl;

import com.lingXi.app.domain.AppEmp;
import com.lingXi.app.mapper.AppEmpMapper;
import com.lingXi.app.service.EmpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 人员列表Service业务层处理
 *
 * @author itheima
 * @date 2024-05-16
 */
@Service("appEmpServiceImpl")
public class EmpServiceImpl implements EmpService {

    @Autowired
    private AppEmpMapper empMapper;

    @Override
    public AppEmp getById(Integer id) {
        return empMapper.selectById(id);
    }

    @Override
    public AppEmp getByUserId(Long userId) {
        return empMapper.selectByUserId(userId);
    }

    @Override
    public int countBoundEmployees() {
        return empMapper.countBoundEmployees();
    }
}