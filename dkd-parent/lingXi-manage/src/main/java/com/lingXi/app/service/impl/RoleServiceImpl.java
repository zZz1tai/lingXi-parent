package com.lingXi.app.service.impl;

import com.lingXi.app.domain.AppRole;
import com.lingXi.app.mapper.AppRoleMapper;
import com.lingXi.app.service.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("appRoleServiceImpl")
public class RoleServiceImpl implements RoleService {

    @Autowired
    private AppRoleMapper roleMapper;

    @Override
    public AppRole getById(Integer id) {
        return roleMapper.selectById(id);
    }
}