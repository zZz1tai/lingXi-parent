package com.lingXi.manage.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "工单详情实体类，用于存储工单的具体明细信息")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskDetails extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 详情ID */
    @Schema(description = "工单详情唯一标识ID", example = "1")
    private Long detailsId;

    /** 工单Id */
    @Excel(name = "工单Id")
    @Schema(description = "关联的工单ID", example = "1001")
    private Long taskId;

    /** 货道编号 */
    @Excel(name = "货道编号")
    @Schema(description = "商品所在货道的编号", example = "A01-B02")
    private String channelCode;

    /** 补货期望容量 */
    @Excel(name = "补货期望容量")
    @Schema(description = "该货道期望补货的数量", example = "50")
    private Long expectCapacity;

    /** 商品Id */
    @Excel(name = "商品Id")
    @Schema(description = "关联的商品SKU ID", example = "2001")
    private Long skuId;

    /** 商品名称 */
    @Excel(name = "商品名称")
    @Schema(description = "商品的名称", example = "可口可乐")
    private String skuName;

    /** 商品图片URL */
    @Excel(name = "商品图片")
    @Schema(description = "商品图片的URL地址", example = "/images/coca-cola.jpg")
    private String skuImage;

}
