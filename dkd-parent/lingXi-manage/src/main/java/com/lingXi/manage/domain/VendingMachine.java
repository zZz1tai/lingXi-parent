package com.lingXi.manage.domain;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.lingXi.common.annotation.Excel;
import com.lingXi.common.core.domain.BaseEntity;

/**
 * 设备管理对象 tb_vending_machine
 * 
 * @author itzhou
 * @date 2025-08-26
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class VendingMachine extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 设备编号 */
    @Excel(name = "设备编号")
    private String innerCode;

    /** 设备容量 */
    private Long channelMaxCapacity;

    /** 点位Id */
    private Long nodeId;

    /** 详细地址 */
    @Excel(name = "详细地址")
    private String addr;

    /** 上次补货时间 */
    private Date lastSupplyTime;

    /** 商圈类型 */
    private Long businessType;

    /** 区域Id */
    private Long regionId;

    /** 合作商Id */
    @Excel(name = "合作商Id")
    private Long partnerId;

    /** 设备型号 */
    @Excel(name = "设备型号")
    private Long vmTypeId;

    /** 设备状态，0:未投放;1-运营;3-撤机 */
    @Excel(name = "设备状态，0:未投放;1-运营;3-撤机")
    private Long vmStatus;

    /** 运行状态 */
    private String runningStatus;

    /** 经度 */
    private Long longitudes;

    /** 维度 */
    private Long latitude;

    /** 客户端连接Id,做emq认证用 */
    private String clientId;

    /** 策略id */
    private Long policyId;

}
