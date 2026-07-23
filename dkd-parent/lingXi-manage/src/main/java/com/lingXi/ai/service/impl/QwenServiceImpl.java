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
 * 千问服务实现类
 * <p>Java 侧只负责业务数据、会话记录和 Python Agent 响应的搬运。
 * 所有模型 Prompt、结构化输出解析和模型能力规则均由 lingXi-agent 负责。</p>
 */
@Slf4j
@Service
public class QwenServiceImpl implements IQwenService {

    /** 与请求对象保持一致的会话标识格式校验器。 */
    private static final Pattern SESSION_ID_FORMAT =
            Pattern.compile(ChatBaseVO.SESSION_ID_REGEX);

    /** 负责与 Python Agent 通信。 */
    private final AgentClient agentClient;
    /** 负责持久化用户消息和助手回答。 */
    private final IModelHistoryService modelHistoryService;
    /** 提供 Agent 分析所需的结构化看板数据。 */
    private final IDashBoardService dashBoardService;

    /**
     * 构造千问业务服务。
     *
     * @param agentClient Python Agent 客户端
     * @param modelHistoryService 对话历史服务
     * @param dashBoardService 数据看板服务
     */
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
        // 先保存用户消息；持久化失败时不调用模型，避免数据库历史与真实对话脱节。
        saveUserMessage(normalizedSessionId, userId, userName, normalizedMessage);
        String reply = agentClient.chat(normalizedMessage, normalizedSessionId, userId);
        // 仅在 Agent 成功返回完整回答后保存助手消息。
        saveAssistantReply(normalizedSessionId, userId, userName, reply);
        return reply;
    }

    @Override
    public String chatWithContext(String sessionId, String userId, String userName,
                                  String userMessage, Object contextData) {
        String normalizedSessionId = requireValidSessionId(sessionId);
        String normalizedMessage = requireValidText(userMessage, "问题");
        // 看板数据作为结构化上下文传输，提示词由 Python 统一构造。
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
        // 完成回调和异常边界可能竞争触发，使用原子标记保证助手回答最多落库一次。
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
        // 与普通流式聊天共享“仅保存一次”的持久化约束。
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
        // 使用固定键名形成稳定传输契约，Python 只负责格式化和分析，不反向查询 Java 数据库。
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
            // 快捷问题属于辅助能力，远端失败时返回固定兜底项，不中断主对话流程。
            List<String> defaults = new ArrayList<>(3);
            defaults.add("我想了解更多细节？");
            defaults.add("能不能具体说明一下？");
            defaults.add("还有哪些相关信息？");
            return defaults;
        }
    }

    @Override
    public void clearConversationMemory(String sessionId, String userId) {
        // 删除 Java 会话前同步清理 Python checkpoint，避免残留短期记忆被再次复用。
        agentClient.deleteThreadMemory(requireValidSessionId(sessionId), userId);
    }

    /**
     * 校验并标准化会话ID
     *
     * @param sessionId 原始会话ID
     * @return 标准化后的会话ID
     * @throws ServiceException 会话ID为空、超长或格式无效时抛出
     */
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

    /**
     * 校验并标准化文本值
     *
     * @param value     原始文本值
     * @param fieldName 字段名称（用于错误提示）
     * @return 标准化后的文本值
     * @throws ServiceException 文本为空或超长时抛出
     */
    private static String requireValidText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new ServiceException(fieldName + "不能为空");
        }
        if (value.length() > ChatBaseVO.MAX_CHAT_TEXT_LENGTH) {
            throw new ServiceException(fieldName + "不能超过32000个字符");
        }
        return value.trim();
    }

    /**
     * 保存用户消息到对话历史
     *
     * @param sessionId 会话唯一标识
     * @param userId    用户唯一标识
     * @param userName  用户名称
     * @param content   消息内容
     */
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

    /**
     * 保存助手回复到对话历史
     *
     * @param sessionId 会话唯一标识
     * @param userId    用户唯一标识
     * @param userName  用户名称
     * @param content   回复内容
     */
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

    /**
     * 保存流式助手回复（仅保存一次，避免重复）
     *
     * @param assistantSaved 助手回复已保存标记
     * @param sessionId      会话唯一标识
     * @param userId         用户唯一标识
     * @param userName       用户名称
     * @param content        回复内容
     */
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

    /**
     * 安全获取字符串长度（null 安全）
     *
     * @param value 字符串值
     * @return 字符串长度，null 时返回 0
     */
    private static int safeLength(String value) {
        return value == null ? 0 : value.length();
    }
}
