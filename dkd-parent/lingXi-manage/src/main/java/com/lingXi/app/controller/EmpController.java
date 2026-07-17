package com.lingXi.app.controller;

import com.lingXi.app.domain.AppEmp;
import com.lingXi.app.domain.vo.EmpVo;
import com.lingXi.app.service.EmpService;
import com.lingXi.app.service.RoleService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

// 员工
@Api(tags = "APP员工管理")
@RestController("appEmpController")
@RequestMapping("/user-service/user")
@Slf4j
public class EmpController {

    @Autowired
    private EmpService empService;
    @Autowired
    private RoleService roleService;

    @ApiOperation("查询员工基本信息")
    @GetMapping("/{id}")
    public EmpVo findById(@PathVariable String id) {
        try {
            Integer empId = Integer.parseInt(id);
            AppEmp emp = empService.getById(empId);
            if (emp == null) {
                return null;
            }
            return convertToVM(emp);
        } catch (NumberFormatException e) {
            // 处理无效的ID值，返回null或错误信息
            return null;
        }
    }

    @ApiOperation("根据用户ID查询员工信息")
    @GetMapping("/byUserId/{userId}")
    public EmpVo findByUserId(@PathVariable Long userId) {
        AppEmp emp = empService.getByUserId(userId);
        if (emp == null) {
            return null;
        }
        return convertToVM(emp);
    }

    // 封装返回结果
    private EmpVo convertToVM(AppEmp emp) {
        EmpVo empVo = new EmpVo();
        empVo.setMobile(emp.getMobile());
        empVo.setRoleId(emp.getRoleId());
        empVo.setRoleCode(emp.getRoleCode());
        empVo.setLoginName(emp.getUserName());
        empVo.setUserId(emp.getId());
        empVo.setRoleName(roleService.getById(emp.getRoleId()).getRoleName());
        empVo.setUserName(emp.getUserName());
        empVo.setStatus(emp.getStatus());
        empVo.setRegionId(emp.getRegionId());
        empVo.setRegionName(emp.getRegionName());
        empVo.setImage(emp.getImage());
        return empVo;
    }

}