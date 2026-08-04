package com.lingXi.ai.domain.dto.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** 单台设备查询工具参数。 */
@Data
public class DeviceLookupArguments {
    @JsonProperty("inner_code")
    private String innerCode;
    @JsonProperty("region_id")
    private Long regionId;
}
