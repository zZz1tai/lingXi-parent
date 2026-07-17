package com.lingXi.app.mapper;

import com.lingXi.app.domain.AppRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppRoleMapper {
    /**
     * 根据ID查询角色
     * @param id 角色ID
     * @return 角色信息
     */
    AppRole selectById(@Param("id") Integer id);
}