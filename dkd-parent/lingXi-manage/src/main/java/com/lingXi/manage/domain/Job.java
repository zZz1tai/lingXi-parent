package com.lingXi.manage.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.lingXi.common.annotation.Excel;
import com.lingXi.common.core.domain.BaseEntity;

/**
 * 自动补货任务对象 tb_job
 * 
 * @author itzhou
 * @date 2025-09-01
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Job extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 警戒值百分比 */
    @Excel(name = "警戒值百分比")
    private Long alertValue;

}
