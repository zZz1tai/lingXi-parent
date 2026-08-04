package com.lingXi.ai.domain.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.web.multipart.MultipartFile;

/** 上传 AI 会话附件的 multipart 请求 VO。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiChatAttachmentUploadVO extends AiChatAttachmentSessionVO {
    @NotNull(message = "附件不能为空")
    private MultipartFile file;
}
