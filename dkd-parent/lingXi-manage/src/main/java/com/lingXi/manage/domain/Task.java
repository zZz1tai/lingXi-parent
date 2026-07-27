package com.lingXi.manage.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.lingXi.common.annotation.Excel;
import com.lingXi.common.core.domain.BaseEntity;

/**
 * 工单对象 tb_task
 * 
 * @author itzhou
 * @date 2025-09-01
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Task extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 工单ID */
    private Long taskId;

    /** 工单编号 */
    @Excel(name = "工单编号")
    private String taskCode;

    /** 工单状态 */
    @Excel(name = "工单状态")
    private Long taskStatus;

    /** 创建类型 0：自动 1：手动 */
    @Excel(name = "创建类型 0：自动 1：手动")
    private Long createType;

    /** 售货机编码 */
    @Excel(name = "售货机编码")
    private String innerCode;

    /** 执行人id */
    @Excel(name = "执行人id")
    private Long userId;

    /** 执行人名称 */
    @Excel(name = "执行人名称")
    private String userName;

    /** 所属区域Id */
    @Excel(name = "所属区域Id")
    private Long regionId;

    /** 备注 */
    @Excel(name = "备注")
    private String desc;

    /** 工单类型id */
    @Excel(name = "工单类型id")
    private Long productTypeId;

    /** 指派人Id */
    @Excel(name = "指派人Id")
    private Long assignorId;

    /** 地址 */
    @Excel(name = "地址")
    private String addr;

    /** AI 受控动作ID；仅用于人工确认写操作的持久幂等。 */
    private String agentActionId;

}
