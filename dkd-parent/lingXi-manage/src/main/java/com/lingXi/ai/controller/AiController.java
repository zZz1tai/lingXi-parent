package com.lingXi.ai.controller;

import com.lingXi.ai.domain.vo.AnalyzeVO;
import com.lingXi.ai.domain.vo.ChatVO;
import com.lingXi.ai.domain.vo.GenerateQuestionsVO;
import com.lingXi.ai.domain.vo.HistoryQueryVO;
import com.lingXi.common.core.domain.AjaxResult;
import com.lingXi.common.exception.ServiceException;
import com.lingXi.common.utils.SecurityUtils;
import com.lingXi.manage.domain.ChatSession;
import com.lingXi.manage.domain.ModelHistory;
import com.lingXi.ai.service.IChatSessionService;
import com.lingXi.manage.service.IModelHistoryService;
import com.lingXi.ai.service.IQwenService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 千问对话接口控制器
 * <p>提供大模型对话、流式对话、数据分析、会话管理等 REST API。</p>
 */
@Api(tags = "千问对话接口")
@Slf4j
@RestController
@RequestMapping("/api/ai")
public class AiController {

    /** 负责大模型调用、数据分析和对话历史持久化。 */
    private final IQwenService qwenService;
    /** 提供对话历史的查询与写入能力。 */
    private final IModelHistoryService modelHistoryService;
    /** 提供会话创建、归属校验、改名和删除能力。 */
    private final IChatSessionService chatSessionService;

    /**
     * 构造 AI 接口控制器。
     *
     * @param qwenService 千问业务服务
     * @param modelHistoryService 对话历史服务
     * @param chatSessionService 会话管理服务
     */
    public AiController(
            IQwenService qwenService,
            IModelHistoryService modelHistoryService,
            IChatSessionService chatSessionService) {
        this.qwenService = qwenService;
        this.modelHistoryService = modelHistoryService;
        this.chatSessionService = chatSessionService;
    }

    /**
     * 发送消息到大模型并返回回复
     *
     * @param chatVO 聊天请求视图对象，包含会话ID和用户消息
     * @return 大模型的文本回复
     */
    @ApiOperation("发送消息到大模型并返回回复")
    @PostMapping("/chat")
    public String chat(@Validated @RequestBody ChatVO chatVO) {
        String userId = currentUserId();
        requireOwnedSession(chatVO.getSessionId(), userId);
        return qwenService.chat(
                chatVO.getSessionId(), userId, currentUsername(), chatVO.getMessage());
    }

    /**
     * 生成智能快捷提问
     *
     * @param generateQuestionsVO 智能快捷提问生成请求视图对象，包含会话ID和对话历史
     * @return 生成的智能快捷提问列表
     */
    @ApiOperation("生成智能快捷提问")
    @PostMapping("/generate-questions")
    public AjaxResult generateQuestions(
            @Validated @RequestBody GenerateQuestionsVO generateQuestionsVO) {
        try {
            String userId = currentUserId();
            requireOwnedSession(generateQuestionsVO.getSessionId(), userId);
            List<String> questions = qwenService.generateSmartQuestions(
                    generateQuestionsVO.getSessionId(),
                    userId,
                    currentUsername(),
                    generateQuestionsVO.getChatHistory());
            return AjaxResult.success(questions);
        } catch (Exception e) {
            return safeError("生成快捷提问失败", e);
        }
    }

    /**
     * 流式发送消息到大模型并返回回复
     *
     * @param chatVO 聊天请求视图对象，包含会话ID和用户消息
     * @return SSE流式响应发射器
     */
    @ApiOperation("流式发送消息到大模型并返回回复")
    @PostMapping("/chat/stream")
    public SseEmitter streamChat(@Validated @RequestBody ChatVO chatVO) {
        String userId = currentUserId();
        requireOwnedSession(chatVO.getSessionId(), userId);
        return qwenService.streamChat(
                chatVO.getSessionId(), userId, currentUsername(), chatVO.getMessage());
    }

    /**
     * 基于数据看板分析用户问题
     *
     * @param analyzeVO 数据看板分析请求视图对象，包含会话ID、问题和时间范围
     * @return 分析结果
     */
    @ApiOperation("基于数据看板分析用户问题")
    @PostMapping("/analyze")
    public AjaxResult analyzeDashboard(@Validated AnalyzeVO analyzeVO) {
        try {
            String userId = currentUserId();
            requireOwnedSession(analyzeVO.getSessionId(), userId);
            Map<String, Object> contextData = qwenService.loadDashboardData(analyzeVO.getStart(), analyzeVO.getEnd());
            String answer = qwenService.chatWithContext(
                    analyzeVO.getSessionId(), userId, currentUsername(),
                    analyzeVO.getQuestion(), contextData);
            return AjaxResult.success(answer);
        } catch (Exception e) {
            return safeError("分析失败", e);
        }
    }

    /**
     * 流式基于数据看板分析用户问题
     *
     * @param analyzeVO 数据看板分析请求视图对象，包含会话ID、问题和时间范围
     * @return SSE流式响应发射器
     */
    @ApiOperation("流式基于数据看板分析用户问题")
    @PostMapping("/analyze/stream")
    public SseEmitter streamAnalyzeDashboard(@Validated AnalyzeVO analyzeVO) {
        try {
            String userId = currentUserId();
            requireOwnedSession(analyzeVO.getSessionId(), userId);
            Map<String, Object> contextData = qwenService.loadDashboardData(analyzeVO.getStart(), analyzeVO.getEnd());
            return qwenService.streamChatWithContext(
                    analyzeVO.getSessionId(), userId, currentUsername(),
                    analyzeVO.getQuestion(), contextData);
        } catch (Exception e) {
            log.warn("流式分析失败，errorType={}", e.getClass().getSimpleName());
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event().name("error").data("分析失败，请稍后重试"));
                emitter.complete();
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
            return emitter;
        }
    }

    /**
     * 获取指定会话的对话历史记录
     *
     * @param historyQueryVO 对话历史查询请求视图对象，包含会话ID和查询范围
     * @return 对话历史记录列表
     */
    @ApiOperation("获取指定会话的对话历史记录")
    @GetMapping("/history")
    public AjaxResult getHistory(HistoryQueryVO historyQueryVO) {
        try {
            String userId = currentUserId();
            List<ModelHistory> history;
            if ("all".equals(historyQueryVO.getQueryScope())) {
                ModelHistory modelHistory = new ModelHistory();
                modelHistory.setUserId(userId);
                history = modelHistoryService.selectModelHistoryList(modelHistory);
            } else {
                requireOwnedSession(historyQueryVO.getSessionId(), userId);
                history = modelHistoryService.selectModelHistoryBySessionId(historyQueryVO.getSessionId());
            }
            return AjaxResult.success(history);
        } catch (Exception e) {
            return safeError("获取对话历史失败", e);
        }
    }

    /**
     * 保存对话历史记录
     *
     * @param history 对话历史记录对象
     * @return 保存结果
     */
    @ApiOperation("保存对话历史记录")
    @PostMapping("/history")
    public AjaxResult saveHistory(@RequestBody ModelHistory history) {
        try {
            if (history.getContent() == null || history.getContent().isEmpty()) {
                return AjaxResult.error("内容不能为空");
            }
            String userId = currentUserId();
            requireOwnedSession(history.getSessionId(), userId);
            history.setUserId(userId);
            history.setUserName(currentUsername());
            int result = modelHistoryService.insertModelHistory(history);
            return AjaxResult.success(result);
        } catch (Exception e) {
            return safeError("保存对话历史失败", e);
        }
    }

    /**
     * 批量保存对话历史记录
     *
     * @param histories 对话历史记录对象列表
     * @return 保存结果
     */
    @ApiOperation("批量保存对话历史记录")
    @PostMapping("/history/batch")
    public AjaxResult batchSaveHistory(@RequestBody List<ModelHistory> histories) {
        try {
            String userId = currentUserId();
            String username = currentUsername();
            for (ModelHistory history : histories) {
                if (history.getContent() == null || history.getContent().isEmpty()) {
                    return AjaxResult.error("内容不能为空");
                }
                requireOwnedSession(history.getSessionId(), userId);
                history.setUserId(userId);
                history.setUserName(username);
            }
            int result = modelHistoryService.batchInsertModelHistory(histories);
            return AjaxResult.success(result);
        } catch (Exception e) {
            return safeError("批量保存对话历史失败", e);
        }
    }

    /**
     * 清空指定会话的对话历史记录
     *
     * @param sessionId 会话唯一标识
     * @return 清空结果
     */
    @ApiOperation("清空指定会话的对话历史记录")
    @DeleteMapping("/history")
    public AjaxResult clearHistory(@RequestParam String sessionId) {
        try {
            String userId = currentUserId();
            requireOwnedSession(sessionId, userId);
            String normalizedSessionId = sessionId.trim();
            qwenService.clearConversationMemory(normalizedSessionId, userId);
            int result = modelHistoryService.deleteModelHistoryBySessionId(
                    normalizedSessionId);
            return AjaxResult.success(result);
        } catch (Exception e) {
            return safeError("清空对话历史失败", e);
        }
    }

    /**
     * 获取用户的会话列表
     *
     * @param userId 用户唯一标识
     * @return 会话列表
     */
    @ApiOperation("获取用户的会话列表")
    @GetMapping("/sessions")
    public AjaxResult getSessions(@RequestParam String userId) {
        try {
            List<ChatSession> sessions = chatSessionService.selectChatSessionByUserId(
                    currentUserId());
            return AjaxResult.success(sessions);
        } catch (Exception e) {
            return safeError("获取会话列表失败", e);
        }
    }

    /**
     * 创建新会话
     *
     * @param userId 用户唯一标识
     * @return 新创建的会话对象
     */
    @ApiOperation("创建新会话")
    @PostMapping("/sessions")
    public AjaxResult createSession(@RequestParam String userId) {
        try {
            ChatSession chatSession = chatSessionService.insertChatSession(currentUserId());
            return AjaxResult.success(chatSession);
        } catch (Exception e) {
            return safeError("创建会话失败", e);
        }
    }

    /**
     * 更新会话名称
     *
     * @param chatSession 会话对象，包含新的会话名称
     * @return 更新结果
     */
    @ApiOperation("更新会话名称")
    @PutMapping("/sessions")
    public AjaxResult updateSession(@RequestBody ChatSession chatSession) {
        try {
            String userId = currentUserId();
            ChatSession existing = requireOwnedSession(chatSession.getSessionId(), userId);
            chatSession.setId(existing.getId());
            chatSession.setUserId(userId);
            int result = chatSessionService.updateChatSession(chatSession);
            return AjaxResult.success(result > 0);
        } catch (Exception e) {
            return safeError("更新会话名称失败", e);
        }
    }

    /**
     * 删除会话
     *
     * @param sessionId 会话唯一标识
     * @return 删除结果
     */
    @ApiOperation("删除会话")
    @DeleteMapping("/sessions")
    public AjaxResult deleteSession(@RequestParam String sessionId) {
        try {
            String userId = currentUserId();
            requireOwnedSession(sessionId, userId);
            String normalizedSessionId = sessionId.trim();
            qwenService.clearConversationMemory(normalizedSessionId, userId);
            int result = chatSessionService.deleteChatSessionAndHistoryBySessionId(
                    normalizedSessionId);
            return AjaxResult.success(result > 0);
        } catch (Exception e) {
            return safeError("删除会话失败", e);
        }
    }

    /**
     * 安全返回错误结果，记录警告日志
     *
     * @param message 错误提示信息
     * @param error   异常对象
     * @return 错误的AjaxResult
     */
    private AjaxResult safeError(String message, Exception error) {
        log.warn("{}，errorType={}", message, error.getClass().getSimpleName());
        return AjaxResult.error(message);
    }

    /**
     * 获取当前登录用户ID
     *
     * @return 用户ID字符串
     */
    String currentUserId() {
        return String.valueOf(SecurityUtils.getUserId());
    }

    /**
     * 获取当前登录用户名
     *
     * @return 用户名
     */
    String currentUsername() {
        return SecurityUtils.getUsername();
    }

    /**
     * 校验会话归属权，确保当前用户拥有指定会话
     *
     * @param sessionId 会话唯一标识
     * @param userId    用户唯一标识
     * @return 会话对象
     * @throws ServiceException 会话不存在或无权访问时抛出
     */
    ChatSession requireOwnedSession(String sessionId, String userId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new ServiceException("会话ID不能为空");
        }
        ChatSession session = chatSessionService.selectChatSessionBySessionId(sessionId.trim());
        if (session == null || session.getUserId() == null
                || !session.getUserId().equals(userId)) {
            // 统一返回无权访问，避免泄露其他用户的会话是否真实存在。
            throw new ServiceException("会话不存在或无权访问");
        }
        return session;
    }
}
