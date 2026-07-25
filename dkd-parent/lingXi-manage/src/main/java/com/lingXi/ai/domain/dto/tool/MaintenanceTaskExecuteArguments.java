package com.lingXi.ai.domain.dto.tool;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 恢复人工确认后由 Python 内部调用的执行参数。 */
public class MaintenanceTaskExecuteArguments {
    @JsonProperty("action_id")
    private String actionId;

    public String getActionId() {
        return actionId;
    }

    public void setActionId(String actionId) {
        this.actionId = actionId;
    }
}

