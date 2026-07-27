package com.lingXi.ai.domain.dto.tool;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 工单统计工具参数。 */
public class TaskStatisticsArguments {
    private String start;
    private String end;
    @JsonProperty("task_type")
    private Integer taskType;
    @JsonProperty("region_id")
    private Long regionId;

    public String getStart() { return start; }
    public void setStart(String start) { this.start = start; }
    public String getEnd() { return end; }
    public void setEnd(String end) { this.end = end; }
    public Integer getTaskType() { return taskType; }
    public void setTaskType(Integer taskType) { this.taskType = taskType; }
    public Long getRegionId() { return regionId; }
    public void setRegionId(Long regionId) { this.regionId = regionId; }
}
