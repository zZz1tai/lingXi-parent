package com.lingXi.ai.service.impl;

import com.lingXi.ai.client.AgentClient;
import com.lingXi.ai.client.AgentClient.StreamOutcomeListener;
import com.lingXi.ai.domain.dto.AgentUserContext;
import com.lingXi.ai.domain.dto.AiChatAttachmentAgentDTO;
import com.lingXi.ai.domain.vo.ChatBaseVO;
import com.lingXi.ai.service.AiChatAttachmentService;
import com.lingXi.ai.service.IQwenService;
import com.dkd.framework.web.filter.RequestIdFilter;
import com.lingXi.common.exception.ServiceException;
import com.lingXi.manage.domain.ModelHistory;
import com.lingXi.manage.service.IDashBoardService;
import com.lingXi.manage.service.IModelHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
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

    /** 从异常消息中提取 Agent 错误码（如 {@code CODE:AGENT_STREAM_ERROR}）。 */
    private static final Pattern ERROR_CODE_PATTERN =
            Pattern.compile("CODE:\\s*([A-Z0-9_]+)");

    /** Agent 熔断打开时的固定兜底回复；降级消息以 SUCCEEDED + AGENT_DEGRADED 标记落库。 */
    private static final String DEGRADED_REPLY = "AI 服务暂时不可用，请稍后重试。";
    /** 降级回复落库使用的错误码，供监控识别降级消息。 */
    private static final String DEGRADED_ERROR_CODE = "AGENT_DEGRADED";

    /** 负责与 Python Agent 通信。 */
    private final AgentClient agentClient;
    /** 负责持久化用户消息和助手回答。 */
    private final IModelHistoryService modelHistoryService;
    /** 提供 Agent 分析所需的结构化看板数据。 */
    private final IDashBoardService dashBoardService;
    /** 负责解析、占用并绑定当前用户的会话附件。 */
    private final AiChatAttachmentService attachmentService;

    /**
     * 构造千问业务服务。
     *
     * @param agentClient Python Agent 客户端
     * @param modelHistoryService 对话历史服务
     * @param dashBoardService 数据看板服务
     */
    @Autowired
    public QwenServiceImpl(
            AgentClient agentClient,
            IModelHistoryService modelHistoryService,
            IDashBoardService dashBoardService,
            AiChatAttachmentService attachmentService) {
        this.agentClient = agentClient;
        this.modelHistoryService = modelHistoryService;
        this.dashBoardService = dashBoardService;
        this.attachmentService = attachmentService;
    }

    /** 保留既有测试使用的纯文本构造入口。 */
    public QwenServiceImpl(AgentClient agentClient,
                           IModelHistoryService modelHistoryService,
                           IDashBoardService dashBoardService) {
        this(agentClient, modelHistoryService, dashBoardService, null);
    }

    @Override
    public String chat(String sessionId, String userId, String userName, String userMessage) {
        return chat(sessionId, AgentUserContext.minimal(userId, userName), userMessage);
    }

    @Override
    public String chat(
            String sessionId, AgentUserContext userContext, String userMessage) {
        return chat(sessionId, userContext, userMessage, List.of());
    }

    @Override
    public String chat(
            String sessionId,
            AgentUserContext userContext,
            String userMessage,
            List<String> attachmentIds) {
        AgentUserContext trustedContext = requireUserContext(userContext);
        String normalizedSessionId = requireValidSessionId(sessionId);
        List<AiChatAttachmentAgentDTO> attachments = prepareAttachments(
                attachmentIds, normalizedSessionId, trustedContext.getUserId());
        String normalizedMessage = normalizeMessage(userMessage, !attachments.isEmpty());
        // 先保存用户消息；持久化失败时不调用模型，避免数据库历史与真实对话脱节。
        ModelHistory userHistory = saveUserMessage(
                normalizedSessionId,
                trustedContext.getUserId(),
                trustedContext.getUserName(),
                normalizedMessage);
        bindAttachments(
                attachmentIds, normalizedSessionId,
                trustedContext.getUserId(), userHistory.getId());
        String reply;
        try {
            reply = attachments.isEmpty()
                    ? agentClient.chat(normalizedMessage, normalizedSessionId, trustedContext)
                    : agentClient.chat(
                            normalizedMessage, normalizedSessionId, trustedContext, attachments);
        } catch (RuntimeException agentFailure) {
            if (agentClient.isCircuitOpen()) {
                // 熔断打开：Agent 或网络不可用，返回固定兜底回复保持对话可用。
                log.warn("Agent 熔断降级回复，sessionIdLength={}，错误={}",
                        safeLength(normalizedSessionId), agentFailure.getMessage());
                saveDegradedAssistantReply(
                        normalizedSessionId,
                        trustedContext.getUserId(),
                        trustedContext.getUserName());
                return DEGRADED_REPLY;
            }
            saveFailedAssistantMessage(
                    normalizedSessionId,
                    trustedContext.getUserId(),
                    trustedContext.getUserName(),
                    extractErrorCode(agentFailure));
            throw agentFailure;
        }
        // 仅在 Agent 成功返回完整回答后保存助手消息。
        saveAssistantReply(
                normalizedSessionId,
                trustedContext.getUserId(),
                trustedContext.getUserName(),
                reply);
        return reply;
    }

    @Override
    public String chatWithContext(String sessionId, String userId, String userName,
                                  String userMessage, Object contextData) {
        return chatWithContext(
                sessionId,
                AgentUserContext.minimal(userId, userName),
                userMessage,
                contextData);
    }

    @Override
    public String chatWithContext(
            String sessionId,
            AgentUserContext userContext,
            String userMessage,
            Object contextData) {
        AgentUserContext trustedContext = requireUserContext(userContext);
        String normalizedSessionId = requireValidSessionId(sessionId);
        String normalizedMessage = requireValidText(userMessage, "问题");
        // 看板数据作为结构化上下文传输，提示词由 Python 统一构造。
        saveUserMessage(
                normalizedSessionId,
                trustedContext.getUserId(),
                trustedContext.getUserName(),
                normalizedMessage);
        String reply;
        try {
            reply = agentClient.chatWithContext(
                    normalizedMessage, contextData, normalizedSessionId, trustedContext);
        } catch (RuntimeException agentFailure) {
            if (agentClient.isCircuitOpen()) {
                log.warn("Agent 熔断降级回复（上下文分析），sessionIdLength={}，错误={}",
                        safeLength(normalizedSessionId), agentFailure.getMessage());
                saveDegradedAssistantReply(
                        normalizedSessionId,
                        trustedContext.getUserId(),
                        trustedContext.getUserName());
                return DEGRADED_REPLY;
            }
            saveFailedAssistantMessage(
                    normalizedSessionId,
                    trustedContext.getUserId(),
                    trustedContext.getUserName(),
                    extractErrorCode(agentFailure));
            throw agentFailure;
        }
        saveAssistantReply(
                normalizedSessionId,
                trustedContext.getUserId(),
                trustedContext.getUserName(),
                reply);
        return reply;
    }

    @Override
    public SseEmitter streamChat(String sessionId, String userId, String userName, String userMessage) {
        return streamChat(
                sessionId, AgentUserContext.minimal(userId, userName), userMessage);
    }

    @Override
    public SseEmitter streamChat(
            String sessionId, AgentUserContext userContext, String userMessage) {
        return streamChat(sessionId, userContext, userMessage, List.of());
    }

    @Override
    public SseEmitter streamChat(
            String sessionId,
            AgentUserContext userContext,
            String userMessage,
            List<String> attachmentIds) {
        AgentUserContext trustedContext = requireUserContext(userContext);
        String normalizedSessionId = requireValidSessionId(sessionId);
        List<AiChatAttachmentAgentDTO> attachments = prepareAttachments(
                attachmentIds, normalizedSessionId, trustedContext.getUserId());
        String normalizedMessage = normalizeMessage(userMessage, !attachments.isEmpty());
        ModelHistory userHistory = saveUserMessage(
                normalizedSessionId,
                trustedContext.getUserId(),
                trustedContext.getUserName(),
                normalizedMessage);
        bindAttachments(
                attachmentIds, normalizedSessionId,
                trustedContext.getUserId(), userHistory.getId());
        if (agentClient.isCircuitOpen()) {
            log.warn("Agent 熔断，流式对话降级回复，sessionIdLength={}",
                    safeLength(normalizedSessionId));
            saveDegradedAssistantReply(
                    normalizedSessionId,
                    trustedContext.getUserId(),
                    trustedContext.getUserName());
            return degradedStreamEmitter(false);
        }
        // 终态统一由 outcome 回调落库：成功/失败/取消各一次，且只报告一次。
        StreamOutcomeListener outcome = createStreamOutcome(
                normalizedSessionId,
                trustedContext.getUserId(),
                trustedContext.getUserName());
        SseEmitter emitter = attachments.isEmpty()
                ? agentClient.streamChat(
                        normalizedMessage, normalizedSessionId,
                        trustedContext, null, outcome)
                : agentClient.streamChat(
                        normalizedMessage, normalizedSessionId,
                        trustedContext, attachments, null, outcome);
        emitter.onCompletion(() -> log.info(
                "流式聊天完成，sessionIdLength={}", safeLength(normalizedSessionId)));
        return emitter;
    }

    @Override
    public SseEmitter streamChatV2(
            String sessionId, AgentUserContext userContext, String userMessage) {
        return streamChatV2(sessionId, userContext, userMessage, List.of());
    }

    @Override
    public SseEmitter streamChatV2(
            String sessionId,
            AgentUserContext userContext,
            String userMessage,
            List<String> attachmentIds) {
        AgentUserContext trustedContext = requireUserContext(userContext);
        String normalizedSessionId = requireValidSessionId(sessionId);
        List<AiChatAttachmentAgentDTO> attachments = prepareAttachments(
                attachmentIds, normalizedSessionId, trustedContext.getUserId());
        String normalizedMessage = normalizeMessage(userMessage, !attachments.isEmpty());
        ModelHistory userHistory = saveUserMessage(
                normalizedSessionId,
                trustedContext.getUserId(),
                trustedContext.getUserName(),
                normalizedMessage);
        bindAttachments(
                attachmentIds, normalizedSessionId,
                trustedContext.getUserId(), userHistory.getId());
        if (agentClient.isCircuitOpen()) {
            log.warn("Agent 熔断，V2 流式对话降级回复，sessionIdLength={}",
                    safeLength(normalizedSessionId));
            saveDegradedAssistantReply(
                    normalizedSessionId,
                    trustedContext.getUserId(),
                    trustedContext.getUserName());
            return degradedStreamEmitter(true);
        }
        StreamOutcomeListener outcome = createStreamOutcome(
                normalizedSessionId,
                trustedContext.getUserId(),
                trustedContext.getUserName());
        SseEmitter emitter = attachments.isEmpty()
                ? agentClient.streamChatV2(
                        normalizedMessage, normalizedSessionId,
                        trustedContext, null, outcome)
                : agentClient.streamChatV2(
                        normalizedMessage, normalizedSessionId,
                        trustedContext, attachments, null, outcome);
        emitter.onCompletion(() -> log.info(
                "V2 流式聊天完成，sessionIdLength={}", safeLength(normalizedSessionId)));
        return emitter;
    }

    @Override
    public SseEmitter resumeActionV2(
            String sessionId,
            AgentUserContext userContext,
            String actionId,
            String decision) {
        AgentUserContext trustedContext = requireUserContext(userContext);
        String normalizedSessionId = requireValidSessionId(sessionId);
        String normalizedActionId = requireActionId(actionId);
        String normalizedDecision = requireDecision(decision);
        if (agentClient.isCircuitOpen()) {
            log.warn("Agent 熔断，受控动作恢复降级回复，sessionIdLength={}",
                    safeLength(normalizedSessionId));
            saveDegradedAssistantReply(
                    normalizedSessionId,
                    trustedContext.getUserId(),
                    trustedContext.getUserName());
            return degradedStreamEmitter(true);
        }
        StreamOutcomeListener outcome = createStreamOutcome(
                normalizedSessionId,
                trustedContext.getUserId(),
                trustedContext.getUserName());
        SseEmitter emitter = agentClient.streamResumeAction(
                normalizedSessionId,
                trustedContext,
                normalizedActionId,
                normalizedDecision,
                null,
                outcome);
        emitter.onCompletion(() -> log.info(
                "受控动作恢复流完成，sessionIdLength={}", safeLength(normalizedSessionId)));
        return emitter;
    }

    @Override
    public SseEmitter streamChatWithContext(String sessionId, String userId, String userName,
                                             String userMessage, Object contextData) {
        return streamChatWithContext(
                sessionId,
                AgentUserContext.minimal(userId, userName),
                userMessage,
                contextData);
    }

    @Override
    public SseEmitter streamChatWithContext(
            String sessionId,
            AgentUserContext userContext,
            String userMessage,
            Object contextData) {
        AgentUserContext trustedContext = requireUserContext(userContext);
        String normalizedSessionId = requireValidSessionId(sessionId);
        String normalizedMessage = requireValidText(userMessage, "问题");
        saveUserMessage(
                normalizedSessionId,
                trustedContext.getUserId(),
                trustedContext.getUserName(),
                normalizedMessage);
        if (agentClient.isCircuitOpen()) {
            log.warn("Agent 熔断，流式上下文分析降级回复，sessionIdLength={}",
                    safeLength(normalizedSessionId));
            saveDegradedAssistantReply(
                    normalizedSessionId,
                    trustedContext.getUserId(),
                    trustedContext.getUserName());
            return degradedStreamEmitter(false);
        }
        // 终态统一由 outcome 回调落库：成功/失败/取消各一次，且只报告一次。
        StreamOutcomeListener outcome = createStreamOutcome(
                normalizedSessionId,
                trustedContext.getUserId(),
                trustedContext.getUserName());
        SseEmitter emitter = agentClient.streamChatWithContext(
                normalizedMessage,
                contextData,
                normalizedSessionId,
                trustedContext,
                null,
                outcome);
        emitter.onCompletion(() -> log.info(
                "流式上下文聊天完成，sessionIdLength={}",
                safeLength(normalizedSessionId)));
        return emitter;
    }

    @Override
    public Map<String, Object> loadDashboardData(String start, String end) {
        // 兼容页面小快照入口：只保留筛选元数据，不再默认搬运全局看板指标。
        // 实时指标由普通 Agent 通过区域化 Java 只读工具按需查询。
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> timeRange = new LinkedHashMap<>();
        timeRange.put("start", start);
        timeRange.put("end", end);
        data.put("page", "dashboard");
        data.put("timeRange", timeRange);
        data.put("snapshot", null);
        data.put("requiresOnDemandTools", true);
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
            defaults.add("能用更简单的方式解释吗？");
            defaults.add("可以给我一个具体例子吗？");
            defaults.add("接下来我还能做什么？");
            return defaults;
        }
    }

    @Override
    public void clearConversationMemory(String sessionId, String userId) {
        // 删除 Java 会话前同步清理 Python checkpoint，避免残留短期记忆被再次复用。
        agentClient.deleteThreadMemory(requireValidSessionId(sessionId), userId);
    }

    @Override
    public Map<String, Object> listLongTermMemories(String userId) {
        return agentClient.listLongTermMemories(requireUserId(userId));
    }

    @Override
    public Map<String, Object> updateLongTermPreference(
            String userId, String preference, String value) {
        return agentClient.updateLongTermPreference(
                requireUserId(userId),
                requireValidText(preference, "偏好名称"),
                requireValidText(value, "偏好值"));
    }

    @Override
    public Map<String, Object> clearLongTermMemories(String userId) {
        return agentClient.clearLongTermMemories(requireUserId(userId));
    }

    private static String requireUserId(String userId) {
        if (userId == null || userId.trim().isEmpty() || userId.length() > 128) {
            throw new ServiceException("用户ID无效");
        }
        return userId.trim();
    }

    private static String requireActionId(String actionId) {
        if (actionId == null || !actionId.trim().matches("^[A-Za-z0-9_-]{1,64}$")) {
            throw new ServiceException("受控动作ID无效");
        }
        return actionId.trim();
    }

    private static String requireDecision(String decision) {
        String normalized = decision == null ? "" : decision.trim().toLowerCase();
        if (!"approve".equals(normalized) && !"reject".equals(normalized)) {
            throw new ServiceException("受控动作决定无效");
        }
        return normalized;
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

    /** 拒绝缺失的可信上下文，防止服务实现退回浏览器身份字段。 */
    private static AgentUserContext requireUserContext(AgentUserContext userContext) {
        if (userContext == null) {
            throw new ServiceException("用户上下文不能为空");
        }
        return userContext;
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

    /** 附件消息允许省略文字；此时保存并发送一个稳定的默认问题。 */
    private static String normalizeMessage(String value, boolean hasAttachments) {
        if ((value == null || value.trim().isEmpty()) && hasAttachments) {
            return "请分析我上传的附件。";
        }
        return requireValidText(value, "消息");
    }

    private List<AiChatAttachmentAgentDTO> prepareAttachments(
            List<String> attachmentIds, String sessionId, String userId) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return List.of();
        }
        if (attachmentService == null) {
            throw new ServiceException("聊天附件服务不可用");
        }
        return attachmentService.prepareForModel(attachmentIds, sessionId, userId);
    }

    private void bindAttachments(
            List<String> attachmentIds,
            String sessionId,
            String userId,
            Long historyId) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return;
        }
        if (attachmentService == null) {
            throw new ServiceException("聊天附件服务不可用");
        }
        attachmentService.bindToHistory(attachmentIds, sessionId, userId, historyId);
    }

    /**
     * 保存用户消息到对话历史
     *
     * @param sessionId 会话唯一标识
     * @param userId    用户唯一标识
     * @param userName  用户名称
     * @param content   消息内容
     */
    private ModelHistory saveUserMessage(
            String sessionId, String userId, String userName, String content) {
        ModelHistory history = new ModelHistory();
        history.setSessionId(sessionId);
        history.setUserId(userId);
        history.setUserName(userName);
        history.setContent(content);
        history.setMessageType("user");
        history.setModelName("agent");
        history.setStatus("ACCEPTED");
        history.setRequestId(RequestIdFilter.current());
        // 受理时间即开始处理时间，供断流/超时监控使用（started_at 后长期无终态 = 卡住）。
        history.setStartedAt(new Date());
        if (modelHistoryService.insertModelHistory(history) != 1) {
            throw new IllegalStateException("用户消息持久化失败");
        }
        return history;
    }

    /**
     * 保存助手回复到对话历史（终态：成功）
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
        history.setStatus("SUCCEEDED");
        history.setRequestId(RequestIdFilter.current());
        history.setCompletedAt(new Date());
        if (modelHistoryService.insertModelHistory(history) != 1) {
            throw new IllegalStateException("助手回复持久化失败");
        }
    }

    /**
     * 保存失败的助手消息（终态：失败，携带错误码）
     *
     * @param sessionId 会话唯一标识
     * @param userId    用户唯一标识
     * @param userName  用户名称
     * @param errorCode 错误码
     */
    private void saveFailedAssistantMessage(
            String sessionId, String userId, String userName, String errorCode) {
        saveTerminalAssistantMessage(
                sessionId, userId, userName, "FAILED", errorCode);
    }

    /**
     * 保存被取消的助手消息（终态：已取消）
     *
     * @param sessionId 会话唯一标识
     * @param userId    用户唯一标识
     * @param userName  用户名称
     */
    private void saveCancelledAssistantMessage(
            String sessionId, String userId, String userName) {
        saveTerminalAssistantMessage(sessionId, userId, userName, "CANCELLED", null);
    }

    /**
     * 保存降级回复到对话历史（终态：成功，携带降级错误码供监控识别）。
     *
     * @param sessionId 会话唯一标识
     * @param userId    用户唯一标识
     * @param userName  用户名称
     */
    private void saveDegradedAssistantReply(
            String sessionId, String userId, String userName) {
        ModelHistory history = new ModelHistory();
        history.setSessionId(sessionId);
        history.setUserId(userId);
        history.setUserName(userName);
        history.setContent(DEGRADED_REPLY);
        history.setMessageType("assistant");
        history.setModelName("agent");
        history.setStatus("SUCCEEDED");
        history.setErrorCode(DEGRADED_ERROR_CODE);
        history.setRequestId(RequestIdFilter.current());
        history.setCompletedAt(new Date());
        if (modelHistoryService.insertModelHistory(history) != 1) {
            throw new IllegalStateException("降级助手回复持久化失败");
        }
    }

    /**
     * 构造熔断降级用的流式响应：立即推送固定兜底文本并结束。
     *
     * @param structuredEvents V2 结构化事件使用 {@code type:token} 格式
     * @return 已推送兜底回复的 SseEmitter
     */
    private SseEmitter degradedStreamEmitter(boolean structuredEvents) {
        SseEmitter emitter = new SseEmitter(30_000L);
        try {
            if (structuredEvents) {
                emitter.send(SseEmitter.event().name("token")
                        .data("{\"type\":\"token\",\"content\":\"" + DEGRADED_REPLY + "\"}"));
                emitter.send(SseEmitter.event().name("done").data("{\"type\":\"done\"}"));
            } else {
                emitter.send(SseEmitter.event().data(DEGRADED_REPLY));
            }
            emitter.complete();
        } catch (Exception ex) {
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    /**
     * 保存终态（失败/取消）的助手消息，不包含回复内容
     *
     * @param sessionId 会话唯一标识
     * @param userId    用户唯一标识
     * @param userName  用户名称
     * @param status    终态状态
     * @param errorCode 错误码（可为空）
     */
    private void saveTerminalAssistantMessage(
            String sessionId,
            String userId,
            String userName,
            String status,
            String errorCode) {
        ModelHistory history = new ModelHistory();
        history.setSessionId(sessionId);
        history.setUserId(userId);
        history.setUserName(userName);
        history.setMessageType("assistant");
        history.setModelName("agent");
        history.setStatus(status);
        history.setErrorCode(errorCode);
        history.setRequestId(RequestIdFilter.current());
        history.setCompletedAt(new Date());
        if (modelHistoryService.insertModelHistory(history) != 1) {
            throw new IllegalStateException("助手消息终态持久化失败");
        }
    }

    /**
     * 构造流式终态回调：成功/失败/取消最多各落库一次，整体只报告一次。
     *
     * @param sessionId 会话唯一标识
     * @param userId    用户唯一标识
     * @param userName  用户名称
     * @return 终态监听器
     */
    private StreamOutcomeListener createStreamOutcome(
            String sessionId,
            String userId,
            String userName) {
        AtomicBoolean outcomeReported = new AtomicBoolean(false);
        return new StreamOutcomeListener() {
            @Override
            public void onReply(String reply) {
                if (!outcomeReported.compareAndSet(false, true)) {
                    log.warn("忽略重复的流式成功报告，sessionIdLength={}",
                            safeLength(sessionId));
                    return;
                }
                saveAssistantReply(sessionId, userId, userName, reply);
            }

            @Override
            public void onFailed(String errorCode) {
                if (!outcomeReported.compareAndSet(false, true)) {
                    log.warn("忽略重复的流式失败报告，sessionIdLength={}",
                            safeLength(sessionId));
                    return;
                }
                saveFailedAssistantMessage(sessionId, userId, userName, errorCode);
            }

            @Override
            public void onCancelled() {
                if (!outcomeReported.compareAndSet(false, true)) {
                    log.warn("忽略重复的流式取消报告，sessionIdLength={}",
                            safeLength(sessionId));
                    return;
                }
                saveCancelledAssistantMessage(sessionId, userId, userName);
            }
        };
    }

    /**
     * 从异常中提取稳定的错误码：优先解析消息中的 {@code CODE:xxx}，
     * 否则按异常类型兜底为通用错误码。
     *
     * @param failure 同步调用抛出的异常
     * @return 错误码
     */
    private static String extractErrorCode(RuntimeException failure) {
        String message = failure.getMessage();
        if (message != null && !message.isBlank()) {
            Matcher matcher = ERROR_CODE_PATTERN.matcher(message);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "AGENT_CALL_FAILED";
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
