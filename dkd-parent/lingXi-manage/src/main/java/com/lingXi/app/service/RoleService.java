package com.lingXi.app.service;

import com.lingXi.app.domain.AppRole;

public interface RoleService {
    /**
     * 根据ID查询角色
     * @param id 角色ID
     * @return 角色信息
     */
    AppRole getById(Integer id);
}