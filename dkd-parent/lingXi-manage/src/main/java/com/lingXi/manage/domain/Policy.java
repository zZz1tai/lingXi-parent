package com.lingXi.manage.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.lingXi.common.annotation.Excel;
import com.lingXi.common.core.domain.BaseEntity;

/**
 * 策略管理对象 tb_policy
 * 
 * @author itzhou
 * @date 2025-08-28
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Policy extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 策略id */
    private Long policyId;

    /** 策略名称 */
    @Excel(name = "策略名称")
    private String policyName;

    /** 策略方案，如：80代表8折 */
    @Excel(name = "策略方案，如：80代表8折")
    private Long discount;

}
