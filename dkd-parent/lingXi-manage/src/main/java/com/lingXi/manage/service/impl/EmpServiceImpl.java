package com.lingXi.manage.service.impl;

import java.util.List;
import com.lingXi.common.core.domain.entity.SysUser;
import com.lingXi.common.utils.DateUtils;
import com.lingXi.common.utils.SecurityUtils;
import com.lingXi.common.utils.StringUtils;
import com.lingXi.manage.domain.Region;
import com.lingXi.manage.domain.Role;
import com.lingXi.manage.mapper.RegionMapper;
import com.lingXi.manage.mapper.RoleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.lingXi.manage.mapper.EmpMapper;
import com.lingXi.manage.domain.Emp;
import com.lingXi.manage.service.IEmpService;
import com.lingXi.system.service.ISysUserService;

/**
 * 人员列表Service业务层处理
 * 
 * @author itzhou
 * @date 2025-08-26
 */
@Service
public class EmpServiceImpl implements IEmpService 
{
    @Autowired
    private EmpMapper empMapper;

    @Autowired
    private RegionMapper regionMapper;
    @Autowired
    private RoleMapper roleMapper;
    
    @Autowired
    private ISysUserService userService;

    /**
     * 查询人员列表
     * 
     * @param id 人员列表主键
     * @return 人员列表
     */
    @Override
    public Emp selectEmpById(Long id)
    {
        return empMapper.selectEmpById(id);
    }

    /**
     * 查询人员列表列表
     * 
     * @param emp 人员列表
     * @return 人员列表
     */
    @Override
    public List<Emp> selectEmpList(Emp emp)
    {
        return empMapper.selectEmpList(emp);
    }

    /**
     * 新增人员列表
     * 
     * @param emp 人员列表
     * @return 结果
     */
    @Override
    @Transactional
    public int insertEmp(Emp emp)
    {
        // 补充区域名称
        Region region = regionMapper.selectRegionById(emp.getRegionId());
        if (region != null) {
            emp.setRegionName(region.getRegionName());
        }

        // 补充角色信息
        Role role = roleMapper.selectRoleByRoleId(emp.getRoleId());
        if (role != null) {
            emp.setRoleName(role.getRoleName());
            emp.setRoleCode(role.getRoleCode());
        }

        // 自动生成系统登录账号（如果未绑定userId）
        if (emp.getUserId() == null)
        {
            SysUser sysUser = createSysUser(emp);
            userService.insertUser(sysUser);
            emp.setUserId(sysUser.getUserId());
        }
        else
        {
            // 如果已绑定userId，检查唯一性
            checkUserIdUnique(emp.getUserId(), null);
        }

        emp.setCreateTime(DateUtils.getNowDate());
        return empMapper.insertEmp(emp);
    }

    /**
     * 修改人员列表
     * 
     * @param emp 人员列表
     * @return 结果
     */
    @Override
    public int updateEmp(Emp emp)
    {
        // 检查用户编号是否已被其他员工绑定
        if (emp.getUserId() != null) {
            checkUserIdUnique(emp.getUserId(), emp.getId());
        }
        
        //补充区域名称
        Region region = regionMapper.selectRegionById(emp.getRegionId());
        if (region != null) {
            emp.setRegionName(region.getRegionName());
        }

        //补充角色信息
        Role role = roleMapper.selectRoleByRoleId(emp.getRoleId());
        if (role != null) {
            emp.setRoleName(role.getRoleName());
            emp.setRoleCode(role.getRoleCode());
        }
        
        emp.setUpdateTime(DateUtils.getNowDate());
        return empMapper.updateEmp(emp);
    }

    /**
     * 检查用户编号是否唯一
     * 
     * @param userId 用户编号
     * @param empId 员工ID，用于排除当前员工
     */
    private void checkUserIdUnique(Long userId, Long empId)
    {
        Emp emp = new Emp();
        emp.setUserId(userId);
        List<Emp> list = empMapper.selectEmpList(emp);
        if (!list.isEmpty()) {
            for (Emp existingEmp : list) {
                if (empId == null || !existingEmp.getId().equals(empId)) {
                    throw new com.lingXi.common.exception.ServiceException("用户编号已被其他员工绑定");
                }
            }
        }
    }

    /**
     * 根据员工信息创建系统用户账号
     * 账号生成规则：优先用手机号，其次用 emp_ + 时间戳后6位
     *
     * @param emp 员工信息
     * @return 系统用户对象
     */
    private SysUser createSysUser(Emp emp)
    {
        SysUser user = new SysUser();
        // 生成用户名：优先手机号，其次 emp_ + 时间戳后6位
        String userName;
        if (StringUtils.isNotEmpty(emp.getMobile()))
        {
            userName = emp.getMobile();
        }
        else
        {
            userName = "emp" + String.valueOf(System.currentTimeMillis()).substring(7);
        }
        user.setUserName(userName);
        user.setNickName(emp.getUserName());
        user.setPassword(SecurityUtils.encryptPassword(emp.getPassword()));
        user.setPhonenumber(emp.getMobile());
        user.setStatus("0");
        return user;
    }

    /**
     * 批量删除人员列表
     * 同时删除绑定的系统用户账号
     *
     * @param ids 需要删除的人员列表主键
     * @return 结果
     */
    @Override
    @Transactional
    public int deleteEmpByIds(Long[] ids)
    {
        // 查询要删除的员工，获取绑定的userId
        for (Long id : ids)
        {
            Emp emp = empMapper.selectEmpById(id);
            if (emp != null && emp.getUserId() != null)
            {
                userService.deleteUserById(emp.getUserId());
            }
        }
        return empMapper.deleteEmpByIds(ids);
    }

    /**
     * 删除人员列表信息
     * 同时删除绑定的系统用户账号
     * 
     * @param id 人员列表主键
     * @return 结果
     */
    @Override
    @Transactional
    public int deleteEmpById(Long id)
    {
        // 查询要删除的员工，获取绑定的userId
        Emp emp = empMapper.selectEmpById(id);
        if (emp != null && emp.getUserId() != null)
        {
            userService.deleteUserById(emp.getUserId());
        }
        return empMapper.deleteEmpById(id);
    }
}
