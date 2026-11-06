package com.lingXi.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.lingXi.ai.service.impl.QwenServiceImpl;
import com.lingXi.manage.domain.ModelHistory;
import com.lingXi.manage.service.IModelHistoryService;

/** 失败和取消消息也必须符合真实数据库的正文非空约束。 */
class QwenTerminalHistoryTest
{
    @Test
    void terminalMessagesPersistEmptyBodyAndStatus()
    {
        IModelHistoryService histories = mock(IModelHistoryService.class);
        when(histories.insertModelHistory(any())).thenAnswer(invocation -> {
            ModelHistory history = invocation.getArgument(0);
            assertNotNull(history.getContent());
            assertEquals("", history.getContent());
            assertEquals("assistant", history.getMessageType());
            return 1;
        });
        QwenServiceImpl service = new QwenServiceImpl(null, histories, null);
        for (String status : new String[] {"FAILED", "CANCELLED"}) {
            ReflectionTestUtils.invokeMethod(service, "saveTerminalAssistantMessage",
                    "test-session", "1", "测试用户", status, null);
        }
    }
}
