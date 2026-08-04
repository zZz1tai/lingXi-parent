package com.lingXi.ai.domain.dto.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** 销售汇总工具参数。 */
@Data
public class SalesSummaryArguments {
    private String start;
    private String end;
    private String granularity = "day";
    @JsonProperty("region_id")
    private Long regionId;
}
