package com.lingXi.ai.service.impl;

import com.lingXi.ai.client.AgentClient;
import com.lingXi.ai.domain.dto.AgentUserContext;
import com.lingXi.ai.domain.vo.ChatBaseVO;
import com.lingXi.common.exception.ServiceException;
import com.lingXi.manage.domain.ModelHistory;
import com.lingXi.manage.service.IDashBoardService;
import com.lingXi.manage.service.IModelHistoryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class QwenServiceImplTest {

    @Test
    void smartQuestionFailureFallsBackToGeneralAssistantPrompts() {
        AgentClient agentClient = mock(AgentClient.class);
        IModelHistoryService historyService = mock(IModelHistoryService.class);
        IDashBoardService dashboardService = mock(IDashBoardService.class);
        when(agentClient.generateSmartQuestions(any(), anyString()))
                .thenThrow(new RuntimeException("upstream unavailable"));
        QwenServiceImpl service = new QwenServiceImpl(
                agentClient, historyService, dashboardService);

        List<String> questions = service.generateSmartQuestions(
                "session-questions", "user-1", "用户", Collections.emptyList());

        assertEquals(List.of(
                "能用更简单的方式解释吗？",
                "可以给我一个具体例子吗？",
                "接下来我还能做什么？"), questions);
    }

    @Test
    void legacyDashboardContextContainsOnlyFiltersAndNoGlobalMetrics() {
        AgentClient agentClient = mock(AgentClient.class);
        IModelHistoryService historyService = mock(IModelHistoryService.class);
        IDashBoardService dashboardService = mock(IDashBoardService.class);
        QwenServiceImpl service = new QwenServiceImpl(
                agentClient, historyService, dashboardService);

        Map<String, Object> snapshot = service.loadDashboardData(
                "2026-07-01", "2026-07-07");

        assertEquals("dashboard", snapshot.get("page"));
        assertEquals(Boolean.TRUE, snapshot.get("requiresOnDemandTools"));
        assertFalse(snapshot.containsKey("taskStats"));
        assertFalse(snapshot.containsKey("saleStats"));
        assertFalse(snapshot.containsKey("skuSaleRank"));
        assertFalse(snapshot.containsKey("abnormalEquipment"));
        verifyNoInteractions(dashboardService);
    }

    @Test
    void invalidMessagesAndQuestionsAreRejectedBeforePersistenceOrAgentCall() {
        AgentClient agentClient = mock(AgentClient.class);
        IModelHistoryService historyService = mock(IModelHistoryService.class);
        IDashBoardService dashboardService = mock(IDashBoardService.class);
        QwenServiceImpl service = new QwenServiceImpl(
                agentClient, historyService, dashboardService);
        String oversized = new String(
                new char[ChatBaseVO.MAX_CHAT_TEXT_LENGTH + 1]).replace('\0', 'x');

        assertThrows(ServiceException.class,
                () -> service.chat("session-1", "user-1", "用户", "   "));
        assertThrows(ServiceException.class,
                () -> service.streamChat(
                        "session-1", "user-1", "用户", oversized));
        assertThrows(ServiceException.class,
                () -> service.chatWithContext(
                        "session-1", "user-1", "用户", "\t", Collections.emptyMap()));
        assertThrows(ServiceException.class,
                () -> service.streamChatWithContext(
                        "session-1", "user-1", "用户", oversized,
                        Collections.emptyMap()));

        verifyNoInteractions(historyService, agentClient);
    }

    @Test
    void invalidSessionIdsAreRejectedByEveryAgentEntryPoint() {
        AgentClient agentClient = mock(AgentClient.class);
        IModelHistoryService historyService = mock(IModelHistoryService.class);
        IDashBoardService dashboardService = mock(IDashBoardService.class);
        QwenServiceImpl service = new QwenServiceImpl(
                agentClient, historyService, dashboardService);
        String oversizedSession = new String(
                new char[ChatBaseVO.MAX_SESSION_ID_LENGTH + 1]).replace('\0', 's');

        assertThrows(ServiceException.class,
                () -> service.chat(" ", "user-1", "用户", "问题"));
        assertThrows(ServiceException.class,
                () -> service.streamChat(
                        oversizedSession, "user-1", "用户", "问题"));
        assertThrows(ServiceException.class,
                () -> service.chatWithContext(
                        "bad session", "user-1", "用户", "问题",
                        Collections.emptyMap()));
        assertThrows(ServiceException.class,
                () -> service.streamChatWithContext(
                        oversizedSession, "user-1", "用户", "问题",
                        Collections.emptyMap()));
        assertThrows(ServiceException.class,
                () -> service.generateSmartQuestions(
                        "", "user-1", "用户", Collections.emptyList()));

        verifyNoInteractions(historyService, agentClient);
    }

    @Test
    void failedUserMessagePersistencePreventsAgentCall() {
        AgentClient agentClient = mock(AgentClient.class);
        IModelHistoryService historyService = mock(IModelHistoryService.class);
        IDashBoardService dashboardService = mock(IDashBoardService.class);
        when(historyService.insertModelHistory(any(ModelHistory.class))).thenReturn(0);
        QwenServiceImpl service = new QwenServiceImpl(
                agentClient, historyService, dashboardService);

        assertThrows(IllegalStateException.class,
                () -> service.chat("session-fail", "user-1", "用户", "问题"));

        verifyNoInteractions(agentClient);
    }

    @SuppressWarnings("unchecked")
    @Test
    void streamingReplyIsPersistedCompletelyAndOnlyOnce() {
        AgentClient agentClient = mock(AgentClient.class);
        IModelHistoryService historyService = mock(IModelHistoryService.class);
        IDashBoardService dashboardService = mock(IDashBoardService.class);
        when(historyService.insertModelHistory(any(ModelHistory.class))).thenReturn(1);
        when(agentClient.streamChat(
                anyString(), anyString(), any(AgentUserContext.class),
                any(), any(AgentClient.StreamOutcomeListener.class)))
                .thenAnswer(invocation -> {
                    AgentClient.StreamOutcomeListener outcome =
                            invocation.getArgument(4);
                    outcome.onReply("完整助手回复");
                    outcome.onReply("完整助手回复");
                    return new SseEmitter();
                });

        QwenServiceImpl service = new QwenServiceImpl(
                agentClient, historyService, dashboardService);
        service.streamChat("session-1", "user-9", "测试用户", "问题");

        ArgumentCaptor<ModelHistory> captor = ArgumentCaptor.forClass(ModelHistory.class);
        verify(historyService, org.mockito.Mockito.times(2))
                .insertModelHistory(captor.capture());
        List<ModelHistory> saved = captor.getAllValues();
        long assistantCount = saved.stream()
                .filter(item -> "assistant".equals(item.getMessageType()))
                .count();
        assertEquals(1L, assistantCount);
        ModelHistory assistant = saved.stream()
                .filter(item -> "assistant".equals(item.getMessageType()))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertEquals("完整助手回复", assistant.getContent());
        assertEquals("session-1", assistant.getSessionId());
        assertEquals("user-9", assistant.getUserId());
        assertEquals("SUCCEEDED", assistant.getStatus());
    }

    @SuppressWarnings("unchecked")
    @Test
    void structuredStreamingReplyUsesTheSameSinglePersistenceBoundary() {
        AgentClient agentClient = mock(AgentClient.class);
        IModelHistoryService historyService = mock(IModelHistoryService.class);
        IDashBoardService dashboardService = mock(IDashBoardService.class);
        when(historyService.insertModelHistory(any(ModelHistory.class))).thenReturn(1);
        when(agentClient.streamChatV2(
                anyString(), anyString(), any(AgentUserContext.class),
                any(), any(AgentClient.StreamOutcomeListener.class)))
                .thenAnswer(invocation -> {
                    AgentClient.StreamOutcomeListener outcome =
                            invocation.getArgument(4);
                    outcome.onReply("V2 完整回复");
                    outcome.onReply("V2 完整回复");
                    return new SseEmitter();
                });
        QwenServiceImpl service = new QwenServiceImpl(
                agentClient, historyService, dashboardService);
        AgentUserContext context = AgentUserContext.minimal("user-v2", "用户");

        service.streamChatV2("session-v2", context, "问题");

        ArgumentCaptor<ModelHistory> captor = ArgumentCaptor.forClass(ModelHistory.class);
        verify(historyService, org.mockito.Mockito.times(2))
                .insertModelHistory(captor.capture());
        long assistantCount = captor.getAllValues().stream()
                .filter(item -> "assistant".equals(item.getMessageType()))
                .count();
        assertEquals(1L, assistantCount);
        ModelHistory assistant = captor.getAllValues().stream()
                .filter(item -> "assistant".equals(item.getMessageType()))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertEquals("SUCCEEDED", assistant.getStatus());
    }

    @SuppressWarnings("unchecked")
    @Test
    void streamingFailurePersistsFailedAssistantMessageWithErrorCode() {
        AgentClient agentClient = mock(AgentClient.class);
        IModelHistoryService historyService = mock(IModelHistoryService.class);
        IDashBoardService dashboardService = mock(IDashBoardService.class);
        when(historyService.insertModelHistory(any(ModelHistory.class))).thenReturn(1);
        when(agentClient.streamChat(
                anyString(), anyString(), any(AgentUserContext.class),
                any(), any(AgentClient.StreamOutcomeListener.class)))
                .thenAnswer(invocation -> {
                    AgentClient.StreamOutcomeListener outcome =
                            invocation.getArgument(4);
                    outcome.onFailed("AGENT_STREAM_OVER_LIMIT");
                    outcome.onFailed("AGENT_STREAM_OVER_LIMIT");
                    return new SseEmitter();
                });

        QwenServiceImpl service = new QwenServiceImpl(
                agentClient, historyService, dashboardService);
        service.streamChat("session-fail", "user-9", "测试用户", "问题");

        ArgumentCaptor<ModelHistory> captor = ArgumentCaptor.forClass(ModelHistory.class);
        verify(historyService, org.mockito.Mockito.times(2))
                .insertModelHistory(captor.capture());
        ModelHistory assistant = captor.getAllValues().stream()
                .filter(item -> "assistant".equals(item.getMessageType()))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertEquals("FAILED", assistant.getStatus());
        assertEquals("AGENT_STREAM_OVER_LIMIT", assistant.getErrorCode());
    }

    @SuppressWarnings("unchecked")
    @Test
    void streamingCancellationPersistsCancelledAssistantMessage() {
        AgentClient agentClient = mock(AgentClient.class);
        IModelHistoryService historyService = mock(IModelHistoryService.class);
        IDashBoardService dashboardService = mock(IDashBoardService.class);
        when(historyService.insertModelHistory(any(ModelHistory.class))).thenReturn(1);
        when(agentClient.streamChat(
                anyString(), anyString(), any(AgentUserContext.class),
                any(), any(AgentClient.StreamOutcomeListener.class)))
                .thenAnswer(invocation -> {
                    AgentClient.StreamOutcomeListener outcome =
                            invocation.getArgument(4);
                    outcome.onCancelled();
                    return new SseEmitter();
                });

        QwenServiceImpl service = new QwenServiceImpl(
                agentClient, historyService, dashboardService);
        service.streamChat("session-cancel", "user-9", "测试用户", "问题");

        ArgumentCaptor<ModelHistory> captor = ArgumentCaptor.forClass(ModelHistory.class);
        verify(historyService, org.mockito.Mockito.times(2))
                .insertModelHistory(captor.capture());
        ModelHistory assistant = captor.getAllValues().stream()
                .filter(item -> "assistant".equals(item.getMessageType()))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertEquals("CANCELLED", assistant.getStatus());
    }

    @Test
    void syncFailurePersistsFailedAssistantMessageWithExtractedCode() {
        AgentClient agentClient = mock(AgentClient.class);
        IModelHistoryService historyService = mock(IModelHistoryService.class);
        IDashBoardService dashboardService = mock(IDashBoardService.class);
        when(historyService.insertModelHistory(any(ModelHistory.class))).thenReturn(1);
        when(agentClient.chat(anyString(), anyString(), any(AgentUserContext.class)))
                .thenThrow(new RuntimeException("Agent 调用失败 CODE:AGENT_CALL_OVER_LIMIT"));

        QwenServiceImpl service = new QwenServiceImpl(
                agentClient, historyService, dashboardService);
        assertThrows(RuntimeException.class,
                () -> service.chat("session-sync-fail", "user-9", "测试用户", "问题"));

        ArgumentCaptor<ModelHistory> captor = ArgumentCaptor.forClass(ModelHistory.class);
        verify(historyService, org.mockito.Mockito.times(2))
                .insertModelHistory(captor.capture());
        ModelHistory assistant = captor.getAllValues().stream()
                .filter(item -> "assistant".equals(item.getMessageType()))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertEquals("FAILED", assistant.getStatus());
        assertEquals("AGENT_CALL_OVER_LIMIT", assistant.getErrorCode());
    }

    @Test
    void userMessageIsMarkedAcceptedWithRequestId() {
        AgentClient agentClient = mock(AgentClient.class);
        IModelHistoryService historyService = mock(IModelHistoryService.class);
        IDashBoardService dashboardService = mock(IDashBoardService.class);
        when(historyService.insertModelHistory(any(ModelHistory.class))).thenReturn(1);
        when(agentClient.chat(anyString(), anyString(), any(AgentUserContext.class)))
                .thenReturn("回复");
        QwenServiceImpl service = new QwenServiceImpl(
                agentClient, historyService, dashboardService);

        service.chat("session-acc", "user-9", "测试用户", "问题");

        ArgumentCaptor<ModelHistory> captor = ArgumentCaptor.forClass(ModelHistory.class);
        verify(historyService, org.mockito.Mockito.times(2))
                .insertModelHistory(captor.capture());
        ModelHistory userMessage = captor.getAllValues().stream()
                .filter(item -> "user".equals(item.getMessageType()))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertEquals("ACCEPTED", userMessage.getStatus());
    }
}
