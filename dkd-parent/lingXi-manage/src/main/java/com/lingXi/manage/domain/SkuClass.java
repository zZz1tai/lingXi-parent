package com.lingXi.manage.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.lingXi.common.annotation.Excel;
import com.lingXi.common.core.domain.BaseEntity;

/**
 * 商品类型对象 tb_sku_class
 * 
 * @author itzhou
 * @date 2025-08-29
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SkuClass extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long classId;

    /** 类别名称 */
    @Excel(name = "类别名称")
    private String className;

    /** 上级id */
    private Long parentId;

}
