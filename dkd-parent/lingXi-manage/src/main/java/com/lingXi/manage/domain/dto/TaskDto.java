package com.lingXi.manage.domain.dto;

import lombok.Data;

import java.util.List;

@Data
public class TaskDto {
    private Long createType;//创建类型
    private String innerCode;//设备编号
    private Long userId;//执行人id
    private Long assignorId;//指派人id
    private Long productTypeId;//产品类型
    private String desc;//描述信息
    private String agentActionId;//AI受控动作ID
    private List<TaskDetailsDto> details;//工单详情
}
