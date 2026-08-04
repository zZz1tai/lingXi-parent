package com.lingXi.ai.domain.dto.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** 创建维修工单提案的严格参数；身份和区域只能来自工具令牌。 */
@Data
public class MaintenanceTaskProposalArguments {
    @JsonProperty("inner_code")
    private String innerCode;
    private String description;
    @JsonProperty("idempotency_key")
    private String idempotencyKey;
}

