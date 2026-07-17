package com.lingXi.app.service;

import com.lingXi.app.domain.AppEmp;


public interface EmpService {
    /**
     * 根据ID查询员工
     * @param id 员工ID
     * @return 员工信息
     */
    AppEmp getById(Integer id);
    
    /**
     * 根据用户ID查询员工
     * @param userId 用户ID
     * @return 员工信息
     */
    AppEmp getByUserId(Long userId);
    
    /**
     * 查询已绑定员工的账号总数
     * @return 已绑定员工的账号总数
     */
    int countBoundEmployees();
}