package com.lingXi.manage.domain;

import com.lingXi.common.core.domain.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 大模型对话历史记录对象 tb_model_history
 * 
 * @author system
 * @date 2025-12-01
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ModelHistory extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 会话唯一标识 */
    private String sessionId;

    /** 用户唯一标识（如用户ID） */
    private String userId;

    /** 用户名字（用于显示） */
    private String userName;

    /** 消息内容 */
    private String content;

    /** 消息类型：user（用户消息）/assistant（助手消息） */
    private String messageType;

    /** 使用的模型名称（如 Qwen、GPT-4 等） */
    private String modelName;

    /** 该条消息消耗的 token 数量 */
    private Integer tokens;

}
