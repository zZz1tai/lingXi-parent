package com.lingXi.ai.domain.dto.tool;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 单台设备查询工具参数。 */
public class DeviceLookupArguments {
    @JsonProperty("inner_code")
    private String innerCode;
    @JsonProperty("region_id")
    private Long regionId;

    public String getInnerCode() { return innerCode; }
    public void setInnerCode(String innerCode) { this.innerCode = innerCode; }
    public Long getRegionId() { return regionId; }
    public void setRegionId(Long regionId) { this.regionId = regionId; }
}
