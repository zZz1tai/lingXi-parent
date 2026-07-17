package com.lingXi.manage.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.lingXi.common.annotation.Excel;
import com.lingXi.common.core.domain.BaseEntity;

/**
 * 点位管理对象 tb_node
 * 
 * @author itzhou
 * @date 2025-08-24
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Node extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 点位ID */
    private Long id;

    /** 点位名称 */
    @Excel(name = "点位名称")
    private String nodeName;

    /** 详细地址 */
    @Excel(name = "详细地址")
    private String address;

    /** 商圈类型 */
    @Excel(name = "商圈类型")
    private Long businessType;

    /** 区域ID */
    @Excel(name = "区域ID")
    private Long regionId;

    /** 合作商ID */
    @Excel(name = "合作商ID")
    private Long partnerId;

}
