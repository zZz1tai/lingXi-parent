package com.lingXi.ai.domain.dto.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** 工单统计工具参数。 */
@Data
public class TaskStatisticsArguments {
    private String start;
    private String end;
    @JsonProperty("task_type")
    private Integer taskType;
    @JsonProperty("region_id")
    private Long regionId;
}
