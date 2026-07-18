package com.lingXi.ai.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
@EqualsAndHashCode(callSuper = true)
public class AnalyzeVO extends ChatBaseVO {
    @NotBlank(message = "问题不能为空")
    @Size(max = MAX_CHAT_TEXT_LENGTH, message = "问题不能超过32000个字符")
    private String question;
    private String start;
    private String end;
}
