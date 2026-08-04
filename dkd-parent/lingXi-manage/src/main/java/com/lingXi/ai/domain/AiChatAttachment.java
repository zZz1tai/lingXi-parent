package com.lingXi.ai.domain;

import lombok.Data;

import java.util.Date;

/** AI 聊天附件元数据；文件本体由 x-file-storage 保存。 */
@Data
public class AiChatAttachment {
    private Long id;
    private String attachmentId;
    private String sessionId;
    private String userId;
    private Long historyId;
    private String originalName;
    private String storagePlatform;
    private String storagePath;
    private String storageFilename;
    private String objectUrl;
    private String mimeType;
    private Long fileSize;
    private String attachmentKind;
    private String extractedText;
    private Boolean extractTruncated;
    private String status;
    private Date createTime;
    private Date updateTime;
}
