package com.lingXi.ai.service.impl;

import com.lingXi.ai.client.AgentClient;
import com.lingXi.ai.service.IQwenService;
import com.lingXi.manage.domain.ModelHistory;
import com.lingXi.manage.service.IDashBoardService;
import com.lingXi.manage.service.IModelHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Java 侧只负责业务数据、会话记录和 Python Agent 响应的搬运。
 * 所有模型 Prompt、结构化输出解析和模型能力规则均由 lingXi-agent 负责。
 */
@Slf4j
@Service
public class QwenServiceImpl implements IQwenService {

    private final AgentClient agentClient;
    private final IModelHistoryService modelHistoryService;
    private final IDashBoardService dashBoardService;

    public QwenServiceImpl(AgentClient agentClient,
                           IModelHistoryService modelHistoryService,
                           IDashBoardService dashBoardService) {
        this.agentClient = agentClient;
        this.modelHistoryService = modelHistoryService;
        this.dashBoardService = dashBoardService;
    }

    @Override
    public String chat(String sessionId, String userMessage) {
        return agentClient.chat(userMessage, sessionId);
    }

    @Override
    public String chat(String sessionId, String userId, String userName, String userMessage) {
        saveUserMessage(sessionId, userId, userName, userMessage);
        String reply = chat(sessionId, userMessage);
        saveAssistantReply(sessionId, userId, userName, reply);
        return reply;
    }

    @Override
    public String chatWithContext(String sessionId, String userMessage, Object contextData) {
        return agentClient.chatWithContext(userMessage, contextData, sessionId);
    }

    @Override
    public String chatWithContext(String sessionId, String userId, String userName,
                                  String userMessage, Object contextData) {
        saveUserMessage(sessionId, userId, userName, userMessage);
        String reply = chatWithContext(sessionId, userMessage, contextData);
        saveAssistantReply(sessionId, userId, userName, reply);
        return reply;
    }

    @Override
    public SseEmitter streamChat(String sessionId, String userId, String userName, String userMessage) {
        saveUserMessage(sessionId, userId, userName, userMessage);
        SseEmitter emitter = agentClient.streamChat(userMessage, sessionId);
        emitter.onCompletion(() -> log.info("流式聊天完成，会话ID: {}", sessionId));
        return emitter;
    }

    @Override
    public SseEmitter streamChatWithContext(String sessionId, String userId, String userName,
                                             String userMessage, Object contextData) {
        saveUserMessage(sessionId, userId, userName, userMessage);
        SseEmitter emitter = agentClient.streamChatWithContext(userMessage, contextData, sessionId);
        emitter.onCompletion(() -> log.info("流式上下文聊天完成，会话ID: {}", sessionId));
        return emitter;
    }

    @Override
    public Map<String, Object> loadDashboardData(String start, String end) {
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> timeRange = new LinkedHashMap<>();
        timeRange.put("start", start);
        timeRange.put("end", end);
        data.put("timeRange", timeRange);
        data.put("taskStats", dashBoardService.getTaskStats(start, end));
        data.put("saleStats", dashBoardService.getSaleStats(start, end));
        data.put("skuSaleRank", dashBoardService.getSkuSaleRank(start, end));
        data.put("abnormalEquipment", dashBoardService.getAbnormalEquipment());
        return data;
    }

    @Override
    public List<String> generateSmartQuestions(String sessionId, String userId, String userName,
                                                List<Map<String, Object>> chatHistory) {
        try {
            return agentClient.generateSmartQuestions(chatHistory, userId);
        } catch (Exception ex) {
            log.error("生成智能快捷提问失败", ex);
            List<String> defaults = new ArrayList<>(3);
            defaults.add("我想了解更多细节？");
            defaults.add("能不能具体说明一下？");
            defaults.add("还有哪些相关信息？");
            return defaults;
        }
    }

    private void saveUserMessage(String sessionId, String userId, String userName, String content) {
        ModelHistory history = new ModelHistory();
        history.setSessionId(sessionId);
        history.setUserId(userId);
        history.setUserName(userName);
        history.setContent(content);
        history.setMessageType("user");
        history.setModelName("agent");
        modelHistoryService.insertModelHistory(history);
    }

    private void saveAssistantReply(String sessionId, String userId, String userName, String content) {
        ModelHistory history = new ModelHistory();
        history.setSessionId(sessionId);
        history.setUserId(userId);
        history.setUserName(userName);
        history.setContent(content);
        history.setMessageType("assistant");
        history.setModelName("agent");
        modelHistoryService.insertModelHistory(history);
    }
}
