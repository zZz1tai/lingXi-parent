package com.lingXi.ai.domain.dto.tool;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 销售汇总工具参数。 */
public class SalesSummaryArguments {
    private String start;
    private String end;
    private String granularity = "day";
    @JsonProperty("region_id")
    private Long regionId;

    public String getStart() { return start; }
    public void setStart(String start) { this.start = start; }
    public String getEnd() { return end; }
    public void setEnd(String end) { this.end = end; }
    public String getGranularity() { return granularity; }
    public void setGranularity(String granularity) { this.granularity = granularity; }
    public Long getRegionId() { return regionId; }
    public void setRegionId(Long regionId) { this.regionId = regionId; }
}
