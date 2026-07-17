package com.lingXi.manage.domain;

import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.HeadFontStyle;
import com.alibaba.excel.annotation.write.style.HeadRowHeight;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.lingXi.common.annotation.Excel;
import com.lingXi.common.core.domain.BaseEntity;

/**
 * 商品管理对象 tb_sku
 * 
 * @author itzhou
 * @date 2025-08-29
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@ExcelIgnoreUnannotated//忽略没有标记的字段
@ColumnWidth(16)//设置列宽
@HeadRowHeight(14)//设置表头行高
@HeadFontStyle(fontHeightInPoints = 11)//设置表头字体大小
public class Sku extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long skuId;

    /** 商品名称 */
    @Excel(name = "商品名称")
    @ExcelProperty(value = "商品名称")
    private String skuName;

    /** 商品图片 */
    @Excel(name = "商品图片")
    @ExcelProperty(value = "商品图片")
    private String skuImage;

    /** 品牌 */
    @Excel(name = "品牌")
    @ExcelProperty(value = "品牌")
    private String brandName;

    /** 规格(净含量) */
    @Excel(name = "规格(净含量)")
    @ExcelProperty(value = "规格(净含量)")
    private String unit;

    /** 商品价格，单位分 */
    @Excel(name = "商品价格，单位分")
    @ExcelProperty(value = "商品价格，单位分")
    private Long price;

    /** 商品类型Id */
    @Excel(name = "商品类型Id")
    @ExcelProperty(value = "商品类型Id")
    private Long classId;

    /** 是否打折促销 */
    private Integer isDiscount;


}
