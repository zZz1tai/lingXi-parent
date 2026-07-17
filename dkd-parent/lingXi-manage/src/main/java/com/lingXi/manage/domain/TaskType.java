package com.lingXi.manage.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.lingXi.common.annotation.Excel;
import com.lingXi.common.core.domain.BaseEntity;

/**
 * 工单类型对象 tb_task_type
 * 
 * @author itzhou
 * @date 2025-09-01
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskType extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** $column.columnComment */
    private Long typeId;

    /** 类型名称 */
    @Excel(name = "类型名称")
    private String typeName;

    /** 工单类型。1:维修工单;2:运营工单 */
    @Excel(name = "工单类型。1:维修工单;2:运营工单")
    private Long type;

}
