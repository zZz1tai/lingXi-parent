package com.lingXi.manage.domain;

import com.lingXi.common.core.domain.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天会话实体类
 * 
 * @author system
 * @date 2025-12-04
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatSession extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 会话唯一标识 */
    private String sessionId;

    /** 用户唯一标识 */
    private String userId;

    /** 会话名称 */
    private String sessionName;

    /** 会话状态：ACTIVE 正常/DELETING 删除中 */
    private String status;


}