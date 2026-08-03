package com.lingXi.ai.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 聊天请求视图对象
 * <p>继承 ChatBaseVO，包含用户消息字段。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ChatVO extends ChatBaseVO {
    /** 用户本轮发送给 Agent 的消息正文。 */
    @NotBlank(message = "消息不能为空")
    @Size(max = MAX_CHAT_TEXT_LENGTH, message = "消息不能超过32000个字符")
    private String message;
}
