package com.lingXi.ai.domain.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** AI 附件接口共用的会话请求 VO。 */
@Data
public class AiChatAttachmentSessionVO {
    @NotBlank(message = "会话ID不能为空")
    @Size(max = 128, message = "会话ID不能超过128个字符")
    private String sessionId;
}
