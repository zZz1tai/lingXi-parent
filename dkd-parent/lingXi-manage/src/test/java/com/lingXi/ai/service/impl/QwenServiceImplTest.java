package com.lingXi.ai.service.impl;

import com.lingXi.ai.client.AgentClient;
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
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class QwenServiceImplTest {

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
                anyString(), anyString(), anyString(), any(Consumer.class)))
                .thenAnswer(invocation -> {
                    Consumer<String> completed = invocation.getArgument(3);
                    completed.accept("完整助手回复");
                    completed.accept("完整助手回复");
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
    }
}
