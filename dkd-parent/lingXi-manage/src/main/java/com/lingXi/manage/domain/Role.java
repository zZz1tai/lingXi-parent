package com.lingXi.manage.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.lingXi.common.annotation.Excel;
import com.lingXi.common.core.domain.BaseEntity;

/**
 * 工单角色对象 tb_role
 *
 * @author itzhou
 * @date 2025-08-26
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Role extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /** 角色编码
     */
    @Excel(name = "角色编码")
    private String roleCode;

    /** 角色名称
     */
    @Excel(name = "角色名称")
    private String roleName;

}
