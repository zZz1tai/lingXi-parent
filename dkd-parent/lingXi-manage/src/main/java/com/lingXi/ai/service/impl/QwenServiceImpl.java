package com.lingXi.ai.service.impl;

import com.lingXi.ai.client.AgentClient;
import com.lingXi.ai.domain.vo.ChatBaseVO;
import com.lingXi.ai.service.IQwenService;
import com.lingXi.common.exception.ServiceException;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Java 侧只负责业务数据、会话记录和 Python Agent 响应的搬运。
 * 所有模型 Prompt、结构化输出解析和模型能力规则均由 lingXi-agent 负责。
 */
@Slf4j
@Service
public class QwenServiceImpl implements IQwenService {

    private static final Pattern SESSION_ID_FORMAT =
            Pattern.compile(ChatBaseVO.SESSION_ID_REGEX);

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
    public String chat(String sessionId, String userId, String userName, String userMessage) {
        String normalizedSessionId = requireValidSessionId(sessionId);
        String normalizedMessage = requireValidText(userMessage, "消息");
        saveUserMessage(normalizedSessionId, userId, userName, normalizedMessage);
        String reply = agentClient.chat(normalizedMessage, normalizedSessionId, userId);
        saveAssistantReply(normalizedSessionId, userId, userName, reply);
        return reply;
    }

    @Override
    public String chatWithContext(String sessionId, String userId, String userName,
                                  String userMessage, Object contextData) {
        String normalizedSessionId = requireValidSessionId(sessionId);
        String normalizedMessage = requireValidText(userMessage, "问题");
        saveUserMessage(normalizedSessionId, userId, userName, normalizedMessage);
        String reply = agentClient.chatWithContext(
                normalizedMessage, contextData, normalizedSessionId, userId);
        saveAssistantReply(normalizedSessionId, userId, userName, reply);
        return reply;
    }

    @Override
    public SseEmitter streamChat(String sessionId, String userId, String userName, String userMessage) {
        String normalizedSessionId = requireValidSessionId(sessionId);
        String normalizedMessage = requireValidText(userMessage, "消息");
        saveUserMessage(normalizedSessionId, userId, userName, normalizedMessage);
        AtomicBoolean assistantSaved = new AtomicBoolean(false);
        SseEmitter emitter = agentClient.streamChat(
                normalizedMessage,
                normalizedSessionId,
                userId,
                reply -> saveStreamReplyOnce(
                        assistantSaved, normalizedSessionId, userId, userName, reply));
        emitter.onCompletion(() -> log.info(
                "流式聊天完成，sessionIdLength={}", safeLength(normalizedSessionId)));
        return emitter;
    }

    @Override
    public SseEmitter streamChatWithContext(String sessionId, String userId, String userName,
                                             String userMessage, Object contextData) {
        String normalizedSessionId = requireValidSessionId(sessionId);
        String normalizedMessage = requireValidText(userMessage, "问题");
        saveUserMessage(normalizedSessionId, userId, userName, normalizedMessage);
        AtomicBoolean assistantSaved = new AtomicBoolean(false);
        SseEmitter emitter = agentClient.streamChatWithContext(
                normalizedMessage,
                contextData,
                normalizedSessionId,
                userId,
                reply -> saveStreamReplyOnce(
                        assistantSaved, normalizedSessionId, userId, userName, reply));
        emitter.onCompletion(() -> log.info(
                "流式上下文聊天完成，sessionIdLength={}",
                safeLength(normalizedSessionId)));
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
        requireValidSessionId(sessionId);
        try {
            return agentClient.generateSmartQuestions(chatHistory, userId);
        } catch (Exception ex) {
            log.error("生成智能快捷提问失败，errorType={}",
                    ex.getClass().getSimpleName());
            List<String> defaults = new ArrayList<>(3);
            defaults.add("我想了解更多细节？");
            defaults.add("能不能具体说明一下？");
            defaults.add("还有哪些相关信息？");
            return defaults;
        }
    }

    @Override
    public void clearConversationMemory(String sessionId, String userId) {
        agentClient.deleteThreadMemory(requireValidSessionId(sessionId), userId);
    }

    private static String requireValidSessionId(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new ServiceException("会话ID不能为空");
        }
        if (sessionId.length() > ChatBaseVO.MAX_SESSION_ID_LENGTH) {
            throw new ServiceException("会话ID不能超过128个字符");
        }
        String normalized = sessionId.trim();
        if (!SESSION_ID_FORMAT.matcher(normalized).matches()) {
            throw new ServiceException("会话ID格式无效");
        }
        return normalized;
    }

    private static String requireValidText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new ServiceException(fieldName + "不能为空");
        }
        if (value.length() > ChatBaseVO.MAX_CHAT_TEXT_LENGTH) {
            throw new ServiceException(fieldName + "不能超过32000个字符");
        }
        return value.trim();
    }

    private void saveUserMessage(String sessionId, String userId, String userName, String content) {
        ModelHistory history = new ModelHistory();
        history.setSessionId(sessionId);
        history.setUserId(userId);
        history.setUserName(userName);
        history.setContent(content);
        history.setMessageType("user");
        history.setModelName("agent");
        if (modelHistoryService.insertModelHistory(history) != 1) {
            throw new IllegalStateException("用户消息持久化失败");
        }
    }

    private void saveAssistantReply(String sessionId, String userId, String userName, String content) {
        if (content == null || content.trim().isEmpty()) {
            log.warn("忽略空的助手回复，sessionIdLength={}", safeLength(sessionId));
            return;
        }
        ModelHistory history = new ModelHistory();
        history.setSessionId(sessionId);
        history.setUserId(userId);
        history.setUserName(userName);
        history.setContent(content);
        history.setMessageType("assistant");
        history.setModelName("agent");
        if (modelHistoryService.insertModelHistory(history) != 1) {
            throw new IllegalStateException("助手回复持久化失败");
        }
    }

    private void saveStreamReplyOnce(
            AtomicBoolean assistantSaved,
            String sessionId,
            String userId,
            String userName,
            String content) {
        if (content == null || content.trim().isEmpty()) {
            log.warn("忽略空的流式助手回复，sessionIdLength={}", safeLength(sessionId));
            return;
        }
        if (assistantSaved.compareAndSet(false, true)) {
            saveAssistantReply(sessionId, userId, userName, content);
        } else {
            log.warn("忽略重复的流式助手回复，sessionIdLength={}",
                    safeLength(sessionId));
        }
    }

    private static int safeLength(String value) {
        return value == null ? 0 : value.length();
    }
}
