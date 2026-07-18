package com.lingXi.ai.client;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingXi.ai.config.AgentConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentClientContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<String> invokeRequest = new AtomicReference<>();
    private final AtomicReference<String> invokeResponse = new AtomicReference<>(
            "{\"success\":true,\"data\":{\"response\":\"同步回复\"}}");
    private final AtomicReference<String> streamRequest = new AtomicReference<>();
    private final AtomicReference<String> streamResponse = new AtomicReference<>(
            "data: {\"type\":\"token\",\"content\":\"完整\"}\n\n"
                    + "data: {\"type\":\"token\",\"content\":\"回复\"}\n\n"
                    + "data: {\"type\":\"done\",\"content\":\"完整回复\"}\n\n"
                    + "data: [DONE]\n\n");
    private final AtomicReference<String> deleteRequest = new AtomicReference<>();
    private final AtomicReference<String> deleteMethod = new AtomicReference<>();
    private final AtomicReference<String> deleteServiceKey = new AtomicReference<>();
    private final AtomicReference<String> questionsRequest = new AtomicReference<>();
    private HttpServer server;
    private ExecutorService serverExecutor;
    private ExecutorService clientExecutor;
    private AgentConfig config;
    private AgentClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.createContext("/invoke", exchange -> {
            invokeRequest.set(readRequest(exchange));
            send(exchange, 200, invokeResponse.get());
        });
        server.createContext("/stream", exchange -> {
            streamRequest.set(readRequest(exchange));
            send(exchange, 200, streamResponse.get());
        });
        server.createContext("/thread", exchange -> {
            deleteMethod.set(exchange.getRequestMethod());
            deleteServiceKey.set(exchange.getRequestHeaders().getFirst(
                    "X-Agent-Service-Key"));
            deleteRequest.set(readRequest(exchange));
            send(exchange, 200, "{\"success\":true,\"data\":{\"deleted\":true}}");
        });
        server.createContext("/thread-failure", exchange -> {
            readRequest(exchange);
            send(exchange, 200,
                    "{\"success\":false,\"error\":{\"code\":\"DELETE_FAILED\","
                            + "\"message\":\"remote delete failed\"}}");
        });
        server.createContext("/questions", exchange -> {
            questionsRequest.set(readRequest(exchange));
            send(exchange, 200,
                    "{\"success\":true,\"data\":{\"questions\":[\"问题1\","
                            + "\"问题2\",\"问题3\"]}}");
        });
        server.start();

        config = new AgentConfig();
        config.setBaseUrl("http://" + server.getAddress().getHostString()
                + ":" + server.getAddress().getPort());
        config.setServiceApiKey("offline-service-key");
        config.setChatInvokeUrl("/invoke");
        config.setChatStreamUrl("/stream");
        config.setThreadDeleteUrl("/thread");
        config.setSmartQuestionsUrl("/questions");
        config.setStyle("professional");
        config.setMaxIterations(5);
        config.setConnectTimeout(2000);
        config.setReadTimeout(5000);

        clientExecutor = Executors.newSingleThreadExecutor();
        client = new AgentClient(config, clientExecutor);
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.shutdown();
        }
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
    }

    @Test
    void synchronousChatCarriesDistinctThreadAndUserIds() throws Exception {
        String reply = client.chat("你好", "session-sync-1", "user-sync-9");

        assertEquals("同步回复", reply);
        assertIdentityContract(
                objectMapper.readTree(invokeRequest.get()),
                "session-sync-1",
                "user-sync-9");
    }

    @Test
    void contextChatCarriesDistinctThreadAndUserIds() throws Exception {
        client.chatWithContext(
                "分析库存",
                Collections.singletonMap("stock", 7),
                "session-context-2",
                "user-context-8");

        JsonNode request = objectMapper.readTree(invokeRequest.get());
        assertIdentityContract(request, "session-context-2", "user-context-8");
        assertEquals("context_analysis", request.path("mode").asText());
        assertEquals(7, request.path("context_data").path("stock").asInt());
    }

    @Test
    void synchronousChatRejectsMissingSuccessfulResponseContent() {
        invokeResponse.set("{\"success\":true,\"data\":{}}");

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> client.chat("空响应", "session-empty", "user-empty"));

        assertEquals("调用 Agent 服务失败", failure.getMessage());
    }

    @Test
    void contextChatRejectsBlankSuccessfulResponseContent() {
        invokeResponse.set("{\"success\":true,\"response\":\"   \"}");

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> client.chatWithContext(
                        "空响应",
                        Collections.singletonMap("stock", 7),
                        "session-context-empty",
                        "user-context-empty"));

        assertEquals("调用 Agent 服务失败", failure.getMessage());
    }

    @Test
    void streamingChatCarriesIdsAndCompletesOneDeduplicatedReply() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicInteger callbackCount = new AtomicInteger();
        AtomicReference<String> completedReply = new AtomicReference<>();

        SseEmitter emitter = client.streamChat(
                "流式问题",
                "session-stream-3",
                "user-stream-7",
                reply -> {
                    callbackCount.incrementAndGet();
                    completedReply.set(reply);
                    completed.countDown();
                });

        assertEquals(Long.valueOf(310_000L), emitter.getTimeout());
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        assertEquals(1, callbackCount.get());
        assertEquals("完整回复", completedReply.get());
        assertIdentityContract(
                objectMapper.readTree(streamRequest.get()),
                "session-stream-3",
                "user-stream-7");
    }

    @Test
    void invalidStreamTimeoutFallsBackBeyondPythonStreamLimit() throws Exception {
        config.setStreamTimeout(0L);

        SseEmitter emitter = client.streamChat(
                "超时契约", "session-timeout", "user-timeout", null);

        assertEquals(Long.valueOf(310_000L), emitter.getTimeout());
        awaitClientTasks();
    }

    @Test
    void streamEofWithoutTerminalSentinelDoesNotCompleteReply() throws Exception {
        streamResponse.set(
                "data: {\"type\":\"token\",\"content\":\"partial\"}\n\n");
        AtomicInteger callbackCount = new AtomicInteger();

        client.streamChat(
                "不完整流", "session-eof", "user-eof",
                ignored -> callbackCount.incrementAndGet());
        awaitClientTasks();

        assertEquals(0, callbackCount.get());
    }

    @Test
    void malformedStreamEventDoesNotCompletePartialReply() throws Exception {
        streamResponse.set(
                "data: {\"type\":\"token\",\"content\":\"partial\"}\n\n"
                        + "data: {not-valid-json}\n\n"
                        + "data: [DONE]\n\n");
        AtomicInteger callbackCount = new AtomicInteger();

        client.streamChat(
                "坏事件", "session-json", "user-json",
                ignored -> callbackCount.incrementAndGet());
        awaitClientTasks();

        assertEquals(0, callbackCount.get());
    }

    @Test
    void oversizedAccumulatedStreamReplyDoesNotCompleteReply() throws Exception {
        String oversized = new String(new char[AgentClient.MAX_STREAM_REPLY_CHARS + 1])
                .replace('\0', 'x');
        streamResponse.set(
                "data: {\"type\":\"token\",\"content\":\""
                        + oversized + "\"}\n\n"
                        + "data: [DONE]\n\n");
        AtomicInteger callbackCount = new AtomicInteger();

        client.streamChat(
                "超长流", "session-large", "user-large",
                ignored -> callbackCount.incrementAndGet());
        awaitClientTasks();

        assertEquals(0, callbackCount.get());
    }

    @Test
    void oversizedStreamLineIsRejectedByBoundedReader() throws Exception {
        String oversizedLine = new String(
                new char[AgentClient.MAX_STREAM_EVENT_CHARS + 1]).replace('\0', 'x');
        streamResponse.set(oversizedLine + "\n");
        AtomicInteger callbackCount = new AtomicInteger();

        client.streamChat(
                "超长事件", "session-line-large", "user-line-large",
                ignored -> callbackCount.incrementAndGet());
        awaitClientTasks();

        assertEquals(0, callbackCount.get());
    }

    @Test
    void upstreamErrorEventIsNotReflectedToClientOrCompletionCallback() throws Exception {
        String sentinel = "SENTINEL_UPSTREAM_STREAM_ERROR_MUST_NOT_ESCAPE";
        streamResponse.set(
                "data: {\"type\":\"error\",\"content\":\""
                        + sentinel + "\"}\n\n");
        AtomicInteger callbackCount = new AtomicInteger();
        Logger agentLogger = (Logger) LoggerFactory.getLogger(AgentClient.class);
        ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
        logAppender.start();
        agentLogger.addAppender(logAppender);

        SseEmitter emitter;
        StringBuilder logs = new StringBuilder();
        try {
            emitter = client.streamChat(
                    "错误流", "session-error", "user-error",
                    ignored -> callbackCount.incrementAndGet());
            awaitClientTasks();
            for (ILoggingEvent event : logAppender.list) {
                logs.append(event.getFormattedMessage());
            }
        } finally {
            agentLogger.detachAppender(logAppender);
            logAppender.stop();
        }

        String emitted = emittedData(emitter);
        assertEquals(0, callbackCount.get());
        assertTrue(emitted.contains("Agent 流式请求失败，请稍后重试"));
        assertFalse(emitted.contains(sentinel));
        assertFalse(logs.toString().contains(sentinel));
    }

    @Test
    void threadMemoryDeletionUsesDeleteJsonAndServiceAuthentication() throws Exception {
        client.deleteThreadMemory("session-delete-4", "user-delete-6");

        assertEquals("DELETE", deleteMethod.get());
        assertEquals("offline-service-key", deleteServiceKey.get());
        assertIdentityContract(
                objectMapper.readTree(deleteRequest.get()),
                "session-delete-4",
                "user-delete-6");
    }

    @Test
    void threadMemoryDeletionRequiresSuccessfulEnvelope() {
        config.setThreadDeleteUrl("/thread-failure");

        assertThrows(RuntimeException.class,
                () -> client.deleteThreadMemory("session-delete-5", "user-delete-7"));
    }

    @Test
    void smartQuestionHistoryWhitelistsContentAndDerivedRole() throws Exception {
        Map<String, Object> directRole = new LinkedHashMap<>();
        directRole.put("content", "用户问题");
        directRole.put("role", "user");
        directRole.put("id", 99);
        directRole.put("time", "sensitive-time");
        Map<String, Object> messageTypeRole = new LinkedHashMap<>();
        messageTypeRole.put("content", "助手回复");
        messageTypeRole.put("messageType", "assistant");
        messageTypeRole.put("createTime", "sensitive-create-time");
        Map<String, Object> booleanRole = new LinkedHashMap<>();
        booleanRole.put("content", "后续问题");
        booleanRole.put("isUser", true);

        client.generateSmartQuestions(
                Arrays.asList(directRole, messageTypeRole, booleanRole), "user-questions");

        JsonNode history = objectMapper.readTree(questionsRequest.get())
                .path("chat_history");
        assertEquals(3, history.size());
        assertHistoryEntry(history.get(0), "用户问题", "user");
        assertHistoryEntry(history.get(1), "助手回复", "assistant");
        assertHistoryEntry(history.get(2), "后续问题", "user");
    }

    @Test
    void smartQuestionHistoryRejectsMissingOrInvalidRole() {
        Map<String, Object> invalid = new LinkedHashMap<>();
        invalid.put("content", "不能发送");
        invalid.put("role", "system");

        assertThrows(RuntimeException.class,
                () -> client.generateSmartQuestions(
                        Collections.singletonList(invalid), "user-questions"));
    }

    private static void assertIdentityContract(
            JsonNode request, String expectedThreadId, String expectedUserId) {
        assertEquals(expectedThreadId, request.path("thread_id").asText());
        assertEquals(expectedUserId, request.path("user_id").asText());
        assertTrue(!request.path("thread_id").asText()
                .equals(request.path("user_id").asText()));
    }

    private static void assertHistoryEntry(
            JsonNode entry, String expectedContent, String expectedRole) {
        assertEquals(2, entry.size());
        assertEquals(expectedContent, entry.path("content").asText());
        assertEquals(expectedRole, entry.path("role").asText());
        assertFalse(entry.has("id"));
        assertFalse(entry.has("time"));
        assertFalse(entry.has("createTime"));
        assertFalse(entry.has("messageType"));
        assertFalse(entry.has("isUser"));
    }

    private void awaitClientTasks() throws Exception {
        clientExecutor.submit(() -> { }).get(5, TimeUnit.SECONDS);
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

    private static String readRequest(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void send(HttpExchange exchange, int statusCode, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
