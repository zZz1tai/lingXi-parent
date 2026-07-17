package com.lingXi.ai.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface IQwenService {

    /**
     * 带会话ID的聊天，会保存对话历史
     * @param sessionId 会话唯一标识
     * @param userMessage 用户消息
     * @return 大模型的回复
     */
    String chat(String sessionId, String userMessage);


    /**
     * 带会话ID的聊天，会保存对话历史
     * @param sessionId 会话唯一标识
     * @param userMessage 用户消息
     * @return 大模型的回复
     */
    String chatWithContext(String sessionId, String userMessage, String contextData);

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
    String chatWithContext(String sessionId, String userId, String userName, String userMessage, String contextData);

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
    SseEmitter streamChatWithContext(String sessionId, String userId, String userName, String userMessage, String contextData);

    /**
     * 对用户输入进行简单 JSON 转义
     */
    String escapeJson(String str);
    
    /**
     * 格式化看板数据为文本
     * @param start 开始时间
     * @param end 结束时间
     * @return 格式化后的看板数据文本
     */
    String formatDashboardData(String start, String end);

    /**
     * 生成智能快捷提问
     * @param sessionId 会话唯一标识
     * @param userId 用户唯一标识
     * @param userName 用户名称
     * @param chatHistory 对话历史
     * @return 生成的智能快捷提问列表
     */
    List<String> generateSmartQuestions(String sessionId, String userId, String userName, List<java.util.Map<String, Object>> chatHistory);
}
