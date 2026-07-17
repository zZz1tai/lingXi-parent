package com.lingXi.manage.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.lingXi.common.annotation.Excel;
import com.lingXi.common.core.domain.BaseEntity;

/**
 * 合作商管理对象 tb_partner
 * 
 * @author itzhou
 * @date 2025-08-24
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Partner extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 合作商ID */
    private Long id;

    /** 合作商名称 */
    @Excel(name = "合作商名称")
    private String partnerName;

    /** 联系人 */
    @Excel(name = "联系人")
    private String contactPerson;

    /** 联系电话 */
    @Excel(name = "联系电话")
    private String contactPhone;

    /** 分成比例 */
    @Excel(name = "分成比例")
    private Long commissionRate;

    /** 账号 */
    @Excel(name = "账号")
    private String account;

    /** 密码 */
    private String password;

}
