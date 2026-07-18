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

@Api(tags = "千问对话接口")
@Slf4j
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final IQwenService qwenService;
    private final IModelHistoryService modelHistoryService;
    private final IChatSessionService chatSessionService;

    public AiController(
            IQwenService qwenService,
            IModelHistoryService modelHistoryService,
            IChatSessionService chatSessionService) {
        this.qwenService = qwenService;
        this.modelHistoryService = modelHistoryService;
        this.chatSessionService = chatSessionService;
    }

    @ApiOperation("发送消息到大模型并返回回复")
    @PostMapping("/chat")
    public String chat(@Validated @RequestBody ChatVO chatVO) {
        String userId = currentUserId();
        requireOwnedSession(chatVO.getSessionId(), userId);
        return qwenService.chat(
                chatVO.getSessionId(), userId, currentUsername(), chatVO.getMessage());
    }

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

    @ApiOperation("流式发送消息到大模型并返回回复")
    @PostMapping("/chat/stream")
    public SseEmitter streamChat(@Validated @RequestBody ChatVO chatVO) {
        String userId = currentUserId();
        requireOwnedSession(chatVO.getSessionId(), userId);
        return qwenService.streamChat(
                chatVO.getSessionId(), userId, currentUsername(), chatVO.getMessage());
    }

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

    private AjaxResult safeError(String message, Exception error) {
        log.warn("{}，errorType={}", message, error.getClass().getSimpleName());
        return AjaxResult.error(message);
    }

    String currentUserId() {
        return String.valueOf(SecurityUtils.getUserId());
    }

    String currentUsername() {
        return SecurityUtils.getUsername();
    }

    ChatSession requireOwnedSession(String sessionId, String userId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new ServiceException("会话ID不能为空");
        }
        ChatSession session = chatSessionService.selectChatSessionBySessionId(sessionId.trim());
        if (session == null || session.getUserId() == null
                || !session.getUserId().equals(userId)) {
            // Do not reveal whether a foreign session exists.
            throw new ServiceException("会话不存在或无权访问");
        }
        return session;
    }
}
