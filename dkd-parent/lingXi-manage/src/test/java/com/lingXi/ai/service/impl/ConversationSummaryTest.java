package com.lingXi.ai.service.impl;

import com.lingXi.ai.client.AgentClient;
import com.lingXi.common.exception.ServiceException;
import com.lingXi.manage.domain.ModelHistory;
import com.lingXi.manage.service.IDashBoardService;
import com.lingXi.manage.service.IModelHistoryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConversationSummaryTest {
    private final AgentClient client = mock(AgentClient.class);
    private final IModelHistoryService histories = mock(IModelHistoryService.class);
    private final QwenServiceImpl service = new QwenServiceImpl(
            client, histories, mock(IDashBoardService.class));

    @Test
    void filtersUntrustedAndUnfinishedRowsAndKeepsChronologicalTextOnly() {
        ModelHistory degraded = row("assistant", "SUCCEEDED", "降级");
        degraded.setErrorCode("AGENT_DEGRADED");
        ModelHistory foreign = row("user", "ACCEPTED", "不属于我");
        foreign.setUserId("another-user");
        ModelHistory otherSession = row("user", "ACCEPTED", "其他会话");
        otherSession.setSessionId("another-session");
        ModelHistory answer = row("assistant", "SUCCEEDED", " 明天检查 ");
        answer.setUiJson("私有渲染元数据");
        when(histories.selectSummaryHistory("session-1", "user-1")).thenReturn(List.of(
                foreign, otherSession, degraded, row("system", "SUCCEEDED", "指令"),
                row("assistant", "FAILED", "失败"), row("assistant", "CANCELLED", "取消"),
                row("assistant", "STREAMING", "未完成"), answer,
                row("user", "ACCEPTED", "下一步？"), row("assistant", null, "旧消息"),
                row("user", "SUCCEEDED", "  ")));
        when(client.summarizeConversation(anyString(), anyString(), anyList()))
                .thenReturn(Map.of("title", "纪要"));

        Map<String, Object> result = service.summarizeConversation("session-1", "user-1");

        verify(client).summarizeConversation("session-1", "user-1", List.of(
                Map.of("role", "assistant", "content", "旧消息"),
                Map.of("role", "user", "content", "下一步？"),
                Map.of("role", "assistant", "content", "明天检查")));
        assertEquals(3, result.get("message_count"));
        assertEquals(false, result.get("truncated"));
        verify(histories, never()).insertModelHistory(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void boundsTextAndPreservesNewestUnicodeContent() {
        when(histories.selectSummaryHistory("session-1", "user-1")).thenReturn(List.of(
                row("assistant", "SUCCEEDED", "x".repeat(1000) + "😀" + "a".repeat(31_999)),
                row("user", "ACCEPTED", "b".repeat(30_000)),
                row("user", "ACCEPTED", "更早")));
        when(client.summarizeConversation(anyString(), anyString(), anyList())).thenReturn(Map.of());

        Map<String, Object> result = service.summarizeConversation("session-1", "user-1");

        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);
        verify(client).summarizeConversation(eq("session-1"), eq("user-1"), captor.capture());
        List<Map<String, Object>> history = captor.getValue();
        assertEquals(2, history.size());
        assertEquals("a".repeat(31_999), history.get(1).get("content"));
        assertEquals("b".repeat(16_001), history.get(0).get("content"));
        assertEquals(true, result.get("truncated"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void limitsToLatestHundredMessagesAndReportsOmissions() {
        List<ModelHistory> rows = new ArrayList<>();
        for (int i = 101; i >= 1; i--) rows.add(row("user", "ACCEPTED", "消息" + i));
        when(histories.selectSummaryHistory("session-1", "user-1")).thenReturn(rows);
        when(client.summarizeConversation(anyString(), anyString(), anyList())).thenReturn(Map.of());
        Map<String, Object> result = service.summarizeConversation("session-1", "user-1");
        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);
        verify(client).summarizeConversation(eq("session-1"), eq("user-1"), captor.capture());
        assertEquals(100, result.get("message_count"));
        assertEquals(true, result.get("truncated"));
        assertEquals("消息2", captor.getValue().get(0).get("content"));
        assertEquals("消息101", captor.getValue().get(99).get("content"));
    }

    @Test
    void emptyHistoryAndInvalidIdentityNeverCallModel() {
        when(histories.selectSummaryHistory("session-1", "user-1")).thenReturn(List.of());
        assertThrows(ServiceException.class, () -> service.summarizeConversation("session-1", "user-1"));
        assertThrows(ServiceException.class, () -> service.summarizeConversation("bad session", "user-1"));
        assertThrows(ServiceException.class, () -> service.summarizeConversation("session-1", " "));
        verifyNoInteractions(client);
    }

    private static ModelHistory row(String role, String status, String content) {
        ModelHistory history = new ModelHistory();
        history.setUserId("user-1");
        history.setSessionId("session-1");
        history.setMessageType(role);
        history.setStatus(status);
        history.setContent(content);
        return history;
    }
}
