package com.lingXi.ai.domain.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Data
public class ChatBaseVO {
    public static final int MAX_SESSION_ID_LENGTH = 128;
    public static final int MAX_CHAT_TEXT_LENGTH = 32_000;
    public static final String SESSION_ID_REGEX =
            "^[A-Za-z0-9][A-Za-z0-9._:@/-]{0,127}$";

    @NotBlank(message = "会话ID不能为空")
    @Size(max = MAX_SESSION_ID_LENGTH, message = "会话ID不能超过128个字符")
    @Pattern(regexp = SESSION_ID_REGEX, message = "会话ID格式无效")
    private String sessionId;
    private String userId;
    private String userName;
}
