package com.lingXi.ai.domain.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 用户在设置界面明确修改的一项长期回答偏好。 */
@Data
public class MemoryPreferenceVO {

    @NotBlank(message = "偏好名称不能为空")
    @Pattern(
            regexp = "^(answer_length|answer_structure|number_format)$",
            message = "偏好名称无效")
    private String preference;

    @NotBlank(message = "偏好值不能为空")
    @Size(max = 64, message = "偏好值不能超过64个字符")
    private String value;

    @JsonIgnore
    @AssertTrue(message = "偏好值与偏好名称不匹配")
    public boolean isSupportedCombination() {
        if (preference == null || value == null) {
            return true;
        }
        switch (preference) {
            case "answer_length":
                return "short".equals(value)
                        || "balanced".equals(value)
                        || "detailed".equals(value);
            case "answer_structure":
                return "conclusion_first".equals(value) || "natural".equals(value);
            case "number_format":
                return "two_decimals".equals(value) || "adaptive".equals(value);
            default:
                return false;
        }
    }
}
