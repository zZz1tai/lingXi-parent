package com.lingXi.ai.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
@EqualsAndHashCode(callSuper = true)
public class ChatVO extends ChatBaseVO {
    @NotBlank(message = "消息不能为空")
    @Size(max = MAX_CHAT_TEXT_LENGTH, message = "消息不能超过32000个字符")
    private String message;
}
