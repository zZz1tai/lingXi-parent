package com.lingXi.ai.domain.vo;

import lombok.Data;

import java.util.Date;
import java.util.List;

/** AI 对话历史响应 VO，避免给持久化实体混入界面字段。 */
@Data
public class AiChatHistoryVO {
    private Long id;
    private String sessionId;
    private String userId;
    private String userName;
    private String content;
    private String uiJson;
    private String messageType;
    private String modelName;
    private Integer tokens;
    private Date createTime;
    private Date updateTime;
    private List<AiChatAttachmentVO> attachments;
}
