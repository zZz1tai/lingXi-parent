package com.lingXi.ai.service;

import com.lingXi.ai.domain.dto.AgentUserContext;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 千问大模型服务接口
 * <p>定义对话、流式对话、上下文分析、智能问题生成等核心方法。</p>
 */
public interface IQwenService {

    /**
     * 带会话ID的聊天，会保存对话历史
     * @param sessionId 会话唯一标识
     * @param userId 用户唯一标识
     * @param userName 用户名称
     * @param userMessage 用户消息
     * @return 大模型的回复
     */
    String chat(String sessionId, String userId, String userName, String userMessage);

    /** 使用 Java 登录态生成的可信上下文进行聊天。 */
    String chat(String sessionId, AgentUserContext userContext, String userMessage);

    /** 使用可信上下文和经过会话校验的附件进行聊天。 */
    default String chat(
            String sessionId,
            AgentUserContext userContext,
            String userMessage,
            List<String> attachmentIds) {
        if (attachmentIds != null && !attachmentIds.isEmpty()) {
            throw new UnsupportedOperationException("当前实现不支持聊天附件");
        }
        return chat(sessionId, userContext, userMessage);
    }

    /**
     * 带会话ID的上下文分析，会保存对话历史
     * @param sessionId 会话唯一标识
     * @param userId 用户唯一标识
     * @param userName 用户名称
     * @param userMessage 用户消息
     * @param contextData 上下文数据
     * @return 大模型的回复
     */
    String chatWithContext(String sessionId, String userId, String userName, String userMessage, Object contextData);

    /** 使用可信用户上下文进行看板小快照分析。 */
    String chatWithContext(String sessionId, AgentUserContext userContext,
                           String userMessage, Object contextData);

    /**
     * 流式聊天，会保存对话历史
     * @param sessionId 会话唯一标识
     * @param userId 用户唯一标识
     * @param userName 用户名称
     * @param userMessage 用户消息
     * @return SseEmitter 用于发送流式响应
     */
    SseEmitter streamChat(String sessionId, String userId, String userName, String userMessage);

    /** 使用可信用户上下文进行流式聊天。 */
    SseEmitter streamChat(String sessionId, AgentUserContext userContext, String userMessage);

    /** 使用可信用户上下文和会话附件进行流式聊天。 */
    default SseEmitter streamChat(
            String sessionId,
            AgentUserContext userContext,
            String userMessage,
            List<String> attachmentIds) {
        if (attachmentIds != null && !attachmentIds.isEmpty()) {
            throw new UnsupportedOperationException("当前实现不支持聊天附件");
        }
        return streamChat(sessionId, userContext, userMessage);
    }

    /** 使用可信用户上下文返回结构化白名单事件的 V2 流。 */
    SseEmitter streamChatV2(
            String sessionId, AgentUserContext userContext, String userMessage);

    /** 数据分析 V2 流：携带业务标签，Python 端启用 OpenUI 表现层。 */
    SseEmitter streamAnalyzeV2(
            String sessionId, AgentUserContext userContext, String userMessage);

    /** 使用可信用户上下文和会话附件返回结构化 V2 流。 */
    default SseEmitter streamChatV2(
            String sessionId,
            AgentUserContext userContext,
            String userMessage,
            List<String> attachmentIds) {
        if (attachmentIds != null && !attachmentIds.isEmpty()) {
            throw new UnsupportedOperationException("当前实现不支持聊天附件");
        }
        return streamChatV2(sessionId, userContext, userMessage);
    }

    /** 恢复已经由登录用户决定的受控动作，不重复保存用户消息。 */
    SseEmitter resumeActionV2(
            String sessionId,
            AgentUserContext userContext,
            String actionId,
            String decision);

    /**
     * 流式上下文分析，会保存对话历史
     * @param sessionId 会话唯一标识
     * @param userId 用户唯一标识
     * @param userName 用户名称
     * @param userMessage 用户消息
     * @param contextData 上下文数据
     * @return SseEmitter 用于发送流式响应
     */
    SseEmitter streamChatWithContext(String sessionId, String userId, String userName, String userMessage, Object contextData);

    /** 使用可信用户上下文进行流式看板小快照分析。 */
    SseEmitter streamChatWithContext(String sessionId, AgentUserContext userContext,
                                     String userMessage, Object contextData);
    
    /**
     * 构造不含业务指标的兼容页面小快照；实时数据必须由业务工具按需查询。
     * @param start 开始时间
     * @param end 结束时间
     * @return 页面筛选元数据
     */
    Map<String, Object> loadDashboardData(String start, String end);

    /**
     * 生成智能快捷提问
     * @param sessionId 会话唯一标识
     * @param userId 用户唯一标识
     * @param userName 用户名称
     * @param chatHistory 对话历史
     * @return 生成的智能快捷提问列表
     */
    List<String> generateSmartQuestions(String sessionId, String userId, String userName, List<java.util.Map<String, Object>> chatHistory);

    /**
     * 清理指定用户会话在 Python Agent 中保存的 checkpoint 记忆。
     * @param sessionId 会话唯一标识
     * @param userId 用户唯一标识
     */
    void clearConversationMemory(String sessionId, String userId);

    /** 查看当前用户的长期回答偏好。 */
    Map<String, Object> listLongTermMemories(String userId);

    /** 修改当前用户的一项长期回答偏好。 */
    Map<String, Object> updateLongTermPreference(
            String userId, String preference, String value);

    /** 清空当前用户的全部长期回答偏好。 */
    Map<String, Object> clearLongTermMemories(String userId);
}
