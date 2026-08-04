package com.lingXi.ai.domain.dto;

import lombok.Data;

/** Java 经过归属校验后传给 Python Agent 的附件 DTO。 */
@Data
public class AiChatAttachmentAgentDTO {
    private String attachmentId;
    private String name;
    private String mimeType;
    private Long size;
    private String kind;
    private String imageUrl;
    private String extractedText;
    private Boolean truncated;
}
