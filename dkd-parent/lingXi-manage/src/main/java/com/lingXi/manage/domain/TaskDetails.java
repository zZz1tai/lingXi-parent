package com.lingXi.manage.domain;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.lingXi.common.annotation.Excel;
import com.lingXi.common.core.domain.BaseEntity;

/**
 * 工单详情对象 tb_task_details
 *
 * @author itzhou
 * @date 2025-09-01
 */
@ApiModel(value = "TaskDetails", description = "工单详情实体类，用于存储工单的具体明细信息")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskDetails extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 详情ID */
    @ApiModelProperty(value = "工单详情唯一标识ID", example = "1", position = 1)
    private Long detailsId;

    /** 工单Id */
    @Excel(name = "工单Id")
    @ApiModelProperty(value = "关联的工单ID", example = "1001", position = 2)
    private Long taskId;

    /** 货道编号 */
    @Excel(name = "货道编号")
    @ApiModelProperty(value = "商品所在货道的编号", example = "A01-B02", position = 3)
    private String channelCode;

    /** 补货期望容量 */
    @Excel(name = "补货期望容量")
    @ApiModelProperty(value = "该货道期望补货的数量", example = "50", position = 4)
    private Long expectCapacity;

    /** 商品Id */
    @Excel(name = "商品Id")
    @ApiModelProperty(value = "关联的商品SKU ID", example = "2001", position = 5)
    private Long skuId;

    /** 商品名称 */
    @Excel(name = "商品名称")
    @ApiModelProperty(value = "商品的名称", example = "可口可乐", position = 6)
    private String skuName;

    /** 商品图片URL */
    @Excel(name = "商品图片")
    @ApiModelProperty(value = "商品图片的URL地址", example = "/images/coca-cola.jpg", position = 7)
    private String skuImage;

}
