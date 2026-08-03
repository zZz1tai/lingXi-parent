package com.lingXi.ai.domain.vo;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 浏览器对 AI 受控动作作出决定的最小请求。 */
@Data
public class AgentActionDecisionVO {
    @NotBlank(message = "会话ID不能为空")
    @Size(max = 128, message = "会话ID不能超过128个字符")
    @Pattern(
            regexp = "^[A-Za-z0-9][A-Za-z0-9._:@/-]{0,127}$",
            message = "会话ID格式无效")
    private String sessionId;

    @NotBlank(message = "审批决定不能为空")
    @Pattern(regexp = "^(approve|reject)$", message = "审批决定无效")
    private String decision;

    @Size(max = 500, message = "工单描述不能超过500个字符")
    private String description;
}

