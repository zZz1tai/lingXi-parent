package com.lingXi.ai.domain.dto.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** 当前异常设备工具参数。 */
@Data
public class AbnormalDevicesArguments {
    private Integer limit = 10;
    @JsonProperty("region_id")
    private Long regionId;

}
