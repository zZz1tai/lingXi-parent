package com.lingXi.ai.domain.dto.tool;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 创建维修工单提案的严格参数；身份和区域只能来自工具令牌。 */
public class MaintenanceTaskProposalArguments {
    @JsonProperty("inner_code")
    private String innerCode;
    private String description;
    @JsonProperty("idempotency_key")
    private String idempotencyKey;

    public String getInnerCode() {
        return innerCode;
    }

    public void setInnerCode(String innerCode) {
        this.innerCode = innerCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}

