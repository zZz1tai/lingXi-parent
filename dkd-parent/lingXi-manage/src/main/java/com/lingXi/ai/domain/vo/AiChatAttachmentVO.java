package com.lingXi.ai.domain.vo;

import lombok.Data;

/** 返回给登录浏览器的安全附件展示 VO。 */
@Data
public class AiChatAttachmentVO {
    private String attachmentId;
    private String name;
    private String mimeType;
    private Long size;
    private String kind;
    private String previewUrl;
    private Boolean truncated;
}
