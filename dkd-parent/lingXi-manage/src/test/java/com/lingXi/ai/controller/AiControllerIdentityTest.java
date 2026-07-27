package com.lingXi.ai.controller;

import com.lingXi.ai.domain.dto.AgentUserContext;
import com.lingXi.ai.domain.vo.AnalyzeVO;
import com.lingXi.ai.domain.vo.ChatVO;
import com.lingXi.ai.domain.vo.GenerateQuestionsVO;
import com.lingXi.ai.domain.vo.MemoryPreferenceVO;
import com.lingXi.ai.service.IChatSessionService;
import com.lingXi.ai.service.IQwenService;
import com.lingXi.common.core.domain.AjaxResult;
import com.lingXi.common.exception.ServiceException;
import com.lingXi.manage.domain.ChatSession;
import com.lingXi.manage.service.IModelHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiControllerIdentityTest {

    private static final String TRUSTED_USER_ID = "42";
    private static final String TRUSTED_USERNAME = "trusted-user";
    private static final AgentUserContext TRUSTED_CONTEXT = new AgentUserContext(
            TRUSTED_USER_ID,
            TRUSTED_USERNAME,
            "1002",
            "运营员",
            12L,
            "上海一区",
            Collections.singletonList("manage:task:list"));

    private IQwenService qwenService;
    private IModelHistoryService historyService;
    private IChatSessionService chatSessionService;
    private TestAiController controller;

    @BeforeEach
    void setUp() {
        qwenService = mock(IQwenService.class);
        historyService = mock(IModelHistoryService.class);
        chatSessionService = mock(IChatSessionService.class);
        controller = new TestAiController(
                qwenService, historyService, chatSessionService);
    }

    @Test
    void chatIgnoresSpoofedVoIdentityAndUsesAuthenticatedIdentity() {
        allowOwnedSession("session-1");
        ChatVO request = new ChatVO();
        request.setSessionId("session-1");
        request.setUserId("attacker-user");
        request.setUserName("attacker-name");
        request.setMessage("你好");
        when(qwenService.chat("session-1", TRUSTED_CONTEXT, "你好"))
                .thenReturn("安全回复");

        assertEquals("安全回复", controller.chat(request));
        verify(qwenService).chat("session-1", TRUSTED_CONTEXT, "你好");
    }

    @Test
    void structuredStreamingUsesOwnedSessionAndTrustedIdentity() {
        allowOwnedSession("session-v2");
        ChatVO request = new ChatVO();
        request.setSessionId("session-v2");
        request.setUserId("attacker-user");
        request.setMessage("查询设备");
        SseEmitter expected = new SseEmitter();
        when(qwenService.streamChatV2(
                "session-v2", TRUSTED_CONTEXT, "查询设备")).thenReturn(expected);

        assertEquals(expected, controller.streamChatV2(request));
        verify(qwenService).streamChatV2(
                "session-v2", TRUSTED_CONTEXT, "查询设备");
    }

    @Test
    void dashboardAnalysisUsesAuthenticatedIdentity() {
        allowOwnedSession("session-analysis");
        AnalyzeVO request = new AnalyzeVO();
        request.setSessionId("session-analysis");
        request.setUserId("spoofed");
        request.setUserName("spoofed-name");
        request.setQuestion("库存如何");
        request.setStart("2026-07-01");
        request.setEnd("2026-07-07");
        when(qwenService.chat(
                "session-analysis",
                TRUSTED_CONTEXT,
                "库存如何\n页面筛选条件（仅作为业务工具查询条件）："
                        + "开始日期=2026-07-01，结束日期=2026-07-07。"))
                .thenReturn("正常");

        controller.analyzeDashboard(request);

        verify(qwenService).chat(
                "session-analysis",
                TRUSTED_CONTEXT,
                "库存如何\n页面筛选条件（仅作为业务工具查询条件）："
                        + "开始日期=2026-07-01，结束日期=2026-07-07。");
        verify(qwenService, never()).loadDashboardData(any(), any());
    }

    @Test
    void smartQuestionsUseAuthenticatedIdentity() {
        allowOwnedSession("session-questions");
        GenerateQuestionsVO request = new GenerateQuestionsVO();
        request.setSessionId("session-questions");
        request.setUserId("spoofed");
        request.setUserName("spoofed-name");
        request.setChatHistory(Collections.emptyList());
        when(qwenService.generateSmartQuestions(
                "session-questions",
                TRUSTED_USER_ID,
                TRUSTED_USERNAME,
                Collections.emptyList())).thenReturn(Collections.singletonList("问题"));

        controller.generateQuestions(request);

        verify(qwenService).generateSmartQuestions(
                "session-questions",
                TRUSTED_USER_ID,
                TRUSTED_USERNAME,
                Collections.emptyList());
    }

    @Test
    void foreignSessionIsRejectedWithoutCallingModel() {
        ChatSession foreign = new ChatSession();
        foreign.setSessionId("foreign-session");
        foreign.setUserId("99");
        when(chatSessionService.selectChatSessionBySessionId("foreign-session"))
                .thenReturn(foreign);
        ChatVO request = new ChatVO();
        request.setSessionId("foreign-session");
        request.setMessage("越权问题");

        assertThrows(ServiceException.class, () -> controller.chat(request));
        verify(qwenService, never()).chat(
                anyString(), any(AgentUserContext.class), anyString());
    }

    @Test
    void deleteSessionClearsTrustedRemoteMemoryBeforeLocalData() {
        allowOwnedSession("session-delete");
        when(chatSessionService.deleteChatSessionAndHistoryBySessionId(
                "session-delete")).thenReturn(1);

        AjaxResult result = controller.deleteSession("session-delete");

        assertEquals(Boolean.TRUE, result.get("data"));
        InOrder order = inOrder(chatSessionService, qwenService);
        order.verify(chatSessionService).selectChatSessionBySessionId("session-delete");
        order.verify(qwenService).clearConversationMemory(
                "session-delete", TRUSTED_USER_ID);
        order.verify(chatSessionService)
                .deleteChatSessionAndHistoryBySessionId("session-delete");
    }

    @Test
    void deleteSessionKeepsLocalDataWhenRemoteMemoryDeletionFails() {
        String secretMarker = "provider-secret-response";
        allowOwnedSession("session-delete-failure");
        doThrow(new IllegalStateException(secretMarker))
                .when(qwenService)
                .clearConversationMemory(
                        "session-delete-failure", TRUSTED_USER_ID);

        AjaxResult result = controller.deleteSession("session-delete-failure");

        verify(chatSessionService, never())
                .deleteChatSessionAndHistoryBySessionId(anyString());
        assertEquals("删除会话失败", result.get("msg"));
        assertFalse(String.valueOf(result.get("msg")).contains(secretMarker));
    }

    @Test
    void clearHistoryClearsTrustedRemoteMemoryBeforeLocalHistory() {
        allowOwnedSession("session-clear-history");
        when(historyService.deleteModelHistoryBySessionId(
                "session-clear-history")).thenReturn(2);

        AjaxResult result = controller.clearHistory("session-clear-history");

        assertEquals(2, result.get("data"));
        InOrder order = inOrder(chatSessionService, qwenService, historyService);
        order.verify(chatSessionService)
                .selectChatSessionBySessionId("session-clear-history");
        order.verify(qwenService).clearConversationMemory(
                "session-clear-history", TRUSTED_USER_ID);
        order.verify(historyService)
                .deleteModelHistoryBySessionId("session-clear-history");
    }

    @Test
    void clearHistoryKeepsLocalHistoryWhenRemoteMemoryDeletionFails() {
        String secretMarker = "provider-clear-history-secret";
        allowOwnedSession("session-clear-history-failure");
        doThrow(new IllegalStateException(secretMarker))
                .when(qwenService)
                .clearConversationMemory(
                        "session-clear-history-failure", TRUSTED_USER_ID);

        AjaxResult result = controller.clearHistory("session-clear-history-failure");

        verify(historyService, never()).deleteModelHistoryBySessionId(anyString());
        assertEquals("清空对话历史失败", result.get("msg"));
        assertFalse(String.valueOf(result.get("msg")).contains(secretMarker));
    }

    @Test
    void controllerFailureDoesNotExposeExceptionMessage() {
        String secretMarker = "provider-sensitive-details";
        allowOwnedSession("session-safe-error");
        GenerateQuestionsVO request = new GenerateQuestionsVO();
        request.setSessionId("session-safe-error");
        request.setChatHistory(Collections.emptyList());
        doThrow(new IllegalStateException(secretMarker))
                .when(qwenService)
                .generateSmartQuestions(
                        "session-safe-error",
                        TRUSTED_USER_ID,
                        TRUSTED_USERNAME,
                        Collections.emptyList());

        AjaxResult result = controller.generateQuestions(request);

        assertEquals("生成快捷提问失败", result.get("msg"));
        assertFalse(String.valueOf(result.get("msg")).contains(secretMarker));
    }

    @Test
    void memoryManagementAlwaysUsesAuthenticatedUser() {
        Map<String, Object> listed = new LinkedHashMap<>();
        listed.put("enabled", true);
        when(qwenService.listLongTermMemories(TRUSTED_USER_ID)).thenReturn(listed);

        AjaxResult listResult = controller.listLongTermMemories();
        assertEquals(listed, listResult.get("data"));
        verify(qwenService).listLongTermMemories(TRUSTED_USER_ID);

        MemoryPreferenceVO preference = new MemoryPreferenceVO();
        preference.setPreference("answer_length");
        preference.setValue("short");
        controller.updateLongTermPreference(preference);
        verify(qwenService).updateLongTermPreference(
                TRUSTED_USER_ID, "answer_length", "short");

        controller.clearLongTermMemories();
        verify(qwenService).clearLongTermMemories(TRUSTED_USER_ID);
    }

    @Test
    void streamingAnalysisFailureUsesFixedSafeEvent() throws Exception {
        String secretMarker = "provider-stream-sensitive-details";
        allowOwnedSession("session-safe-stream");
        when(qwenService.streamChat(
                anyString(), any(AgentUserContext.class), anyString()))
                .thenThrow(new IllegalStateException(secretMarker));
        AnalyzeVO request = new AnalyzeVO();
        request.setSessionId("session-safe-stream");
        request.setQuestion("分析数据");

        SseEmitter emitter = controller.streamAnalyzeDashboard(request);
        String emitted = emittedData(emitter);

        assertTrue(emitted.contains("分析失败，请稍后重试"));
        assertFalse(emitted.contains(secretMarker));
    }

    @Test
    void onlyPostMappingsExposeAuthenticatedStreamingEndpoints() {
        Set<String> getRoutes = Arrays.stream(AiController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(GetMapping.class))
                .filter(Objects::nonNull)
                .flatMap(mapping -> Arrays.stream(mapping.value()))
                .collect(Collectors.toSet());
        Set<String> postRoutes = Arrays.stream(AiController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(PostMapping.class))
                .filter(Objects::nonNull)
                .flatMap(mapping -> Arrays.stream(mapping.value()))
                .collect(Collectors.toSet());

        assertFalse(getRoutes.contains("/chat/stream"));
        assertFalse(getRoutes.contains("/analyze/stream"));
        assertTrue(postRoutes.contains("/chat/stream"));
        assertTrue(postRoutes.contains("/analyze/stream"));
        assertTrue(postRoutes.contains("/chat/stream/v2"));
        assertTrue(postRoutes.contains("/analyze/stream/v2"));
    }

    @Test
    void agentEntryPointsEnableBeanValidation() throws Exception {
        assertValidatedParameter("chat", ChatVO.class);
        assertValidatedParameter("streamChat", ChatVO.class);
        assertValidatedParameter("streamChatV2", ChatVO.class);
        assertValidatedParameter("analyzeDashboard", AnalyzeVO.class);
        assertValidatedParameter("streamAnalyzeDashboard", AnalyzeVO.class);
        assertValidatedParameter("streamAnalyzeDashboardV2", AnalyzeVO.class);
        assertValidatedParameter("generateQuestions", GenerateQuestionsVO.class);
        assertValidatedParameter("updateLongTermPreference", MemoryPreferenceVO.class);
    }

    private static void assertValidatedParameter(String methodName, Class<?> parameterType)
            throws Exception {
        Method method = AiController.class.getDeclaredMethod(methodName, parameterType);
        assertTrue(method.getParameters()[0].isAnnotationPresent(Validated.class),
                methodName + " must validate its request DTO");
    }

    private static String emittedData(SseEmitter emitter) throws Exception {
        Field attemptsField = ResponseBodyEmitter.class
                .getDeclaredField("earlySendAttempts");
        attemptsField.setAccessible(true);
        Iterable<?> attempts = (Iterable<?>) attemptsField.get(emitter);
        StringBuilder emitted = new StringBuilder();
        for (Object attempt : attempts) {
            Field dataField = attempt.getClass().getDeclaredField("data");
            dataField.setAccessible(true);
            emitted.append(String.valueOf(dataField.get(attempt)));
        }
        return emitted.toString();
    }

    private void allowOwnedSession(String sessionId) {
        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setUserId(TRUSTED_USER_ID);
        when(chatSessionService.selectChatSessionBySessionId(sessionId))
                .thenReturn(session);
    }

    private static class TestAiController extends AiController {
        TestAiController(
                IQwenService qwenService,
                IModelHistoryService historyService,
                IChatSessionService chatSessionService) {
            super(qwenService, historyService, chatSessionService);
        }

        @Override
        String currentUserId() {
            return TRUSTED_USER_ID;
        }

        @Override
        String currentUsername() {
            return TRUSTED_USERNAME;
        }

        @Override
        AgentUserContext currentAgentUserContext() {
            return TRUSTED_CONTEXT;
        }
    }
}
