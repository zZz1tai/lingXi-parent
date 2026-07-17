package com.lingXi.manage.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.lingXi.common.annotation.Excel;
import com.lingXi.common.core.domain.BaseEntity;

/**
 * 区域管理对象 tb_region
 * 
 * @author itzhou
 * @date 2025-08-24
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Region extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 区域ID */
    private Long id;

    /** 区域名称 */
    @Excel(name = "区域名称")
    private String regionName;

}
