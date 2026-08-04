package com.lingXi.ai.domain.dto.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** 恢复人工确认后由 Python 内部调用的执行参数。 */
@Data
public class MaintenanceTaskExecuteArguments {
    @JsonProperty("action_id")
    private String actionId;
}

