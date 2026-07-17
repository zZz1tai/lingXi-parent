package com.lingXi.app.mapper;

import com.lingXi.app.domain.AppEmp;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 人员列表Mapper接口
 *
 * @author itheima
 * @date 2024-05-16
 */
@Mapper
public interface AppEmpMapper {
    /**
     * 根据ID查询员工
     * @param id 员工ID
     * @return 员工信息
     */
    AppEmp selectById(@Param("id") Integer id);
    
    /**
     * 根据用户ID查询员工
     * @param userId 用户ID
     * @return 员工信息
     */
    AppEmp selectByUserId(@Param("userId") Long userId);
    
    /**
     * 查询已绑定员工的账号总数
     * @return 已绑定员工的账号总数
     */
    int countBoundEmployees();
}