package com.lingXi.manage.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.lingXi.common.annotation.Excel;
import com.lingXi.common.core.domain.BaseEntity;

/**
 * 人员列表对象 tb_emp
 * 
 * @author itzhou
 * @date 2025-08-26
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Emp extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 员工名称 */
    @Excel(name = "员工名称")
    private String userName;

    /** 所属区域Id */
    private Long regionId;

    /** 区域名称 */
    @Excel(name = "区域名称")
    private String regionName;

    /** 角色id */
    @Excel(name = "角色id")
    private Long roleId;

    /** 角色编号 */
    private String roleCode;

    /** 角色名称 */
    @Excel(name = "角色名称")
    private String roleName;

    /** 联系电话 */
    @Excel(name = "联系电话")
    private String mobile;

    /** 登录密码 */
    private String password;

    /** 员工头像 */
    private String image;

    /** 是否启用 */
    private Long status;

    /** 系统用户ID */
    private Long userId;

}
