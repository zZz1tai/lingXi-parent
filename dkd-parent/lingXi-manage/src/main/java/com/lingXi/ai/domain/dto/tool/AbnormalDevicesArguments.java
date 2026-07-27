package com.lingXi.ai.domain.dto.tool;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 当前异常设备工具参数。 */
public class AbnormalDevicesArguments {
    private Integer limit = 10;
    @JsonProperty("region_id")
    private Long regionId;

    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }
    public Long getRegionId() { return regionId; }
    public void setRegionId(Long regionId) { this.regionId = regionId; }
}
