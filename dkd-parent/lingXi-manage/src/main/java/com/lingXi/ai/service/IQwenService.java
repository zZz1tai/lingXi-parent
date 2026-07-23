package com.lingXi.ai.service;

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

    /**
     * 流式聊天，会保存对话历史
     * @param sessionId 会话唯一标识
     * @param userId 用户唯一标识
     * @param userName 用户名称
     * @param userMessage 用户消息
     * @return SseEmitter 用于发送流式响应
     */
    SseEmitter streamChat(String sessionId, String userId, String userName, String userMessage);

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
    
    /**
     * 读取看板结构化数据，由 Python Agent 负责格式化与分析 Prompt
     * @param start 开始时间
     * @param end 结束时间
     * @return 原始看板数据
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
}
