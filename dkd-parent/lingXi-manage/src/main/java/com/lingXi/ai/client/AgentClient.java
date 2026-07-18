package com.lingXi.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lingXi.ai.config.AgentConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Slf4j
@Component
public class AgentClient {

    static final int MAX_STREAM_REPLY_CHARS = 200_000;
    static final int MAX_STREAM_EVENT_CHARS = 1_048_576;

    private final AgentConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executorService;

    @Autowired
    public AgentClient(AgentConfig config) {
        this(config, createStreamExecutor(config));
    }

    AgentClient(AgentConfig config, ExecutorService executorService) {
        this.config = config;
        this.executorService = executorService;
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }

    private static ExecutorService createStreamExecutor(AgentConfig config) {
        int corePoolSize = positiveOrDefault(config.getStreamCorePoolSize(), 4);
        int maxPoolSize = Math.max(
                corePoolSize, positiveOrDefault(config.getStreamMaxPoolSize(), 16));
        int queueCapacity = positiveOrDefault(config.getStreamQueueCapacity(), 100);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                runnable -> {
                    Thread thread = new Thread(runnable, "agent-sse-forwarder");
                    thread.setDaemon(false);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private static int positiveOrDefault(Integer value, int fallback) {
        return value == null || value.intValue() <= 0 ? fallback : value.intValue();
    }

    /**
     * 同步调用 Agent 对话接口
     */
    public String chat(String message, String sessionId, String userId) {
        return chat(message, sessionId, userId, "chat", null);
    }

    /** 搬运结构化业务数据，由 Python 选择并构造数据分析 Prompt。 */
    public String chatWithContext(
            String message,
            Object contextData,
            String sessionId,
            String userId) {
        return chat(message, sessionId, userId, "context_analysis", contextData);
    }

    private String chat(
            String message,
            String sessionId,
            String userId,
            String mode,
            Object contextData) {
        try {
            String requestBody = buildRequest(
                    message, sessionId, userId, mode, contextData);
            JsonNode root = requestJson("POST", config.getChatInvokeUrl(), requestBody);
            requireSuccess(root, "AGENT_CHAT_FAILED", "Agent 对话请求失败");
            return extractResponse(root);
        } catch (Exception e) {
            log.error("调用 Agent 服务失败，errorType={}",
                    e.getClass().getSimpleName());
            throw new RuntimeException("调用 Agent 服务失败", e);
        }
    }

    /**
     * 流式调用 Agent 对话接口
     */
    public SseEmitter streamChat(
            String message,
            String sessionId,
            String userId,
            Consumer<String> completedReplyConsumer) {
        return streamChat(
                message, sessionId, userId, "chat", null, completedReplyConsumer);
    }

    /** 流式搬运结构化业务数据，由 Python 选择并构造数据分析 Prompt。 */
    public SseEmitter streamChatWithContext(
            String message,
            Object contextData,
            String sessionId,
            String userId,
            Consumer<String> completedReplyConsumer) {
        return streamChat(
                message,
                sessionId,
                userId,
                "context_analysis",
                contextData,
                completedReplyConsumer);
    }

    private SseEmitter streamChat(
            String message,
            String sessionId,
            String userId,
            String mode,
            Object contextData,
            Consumer<String> completedReplyConsumer) {
        long streamTimeout = config.getStreamTimeout() == null
                || config.getStreamTimeout().longValue() <= 0L
                        ? 310_000L : config.getStreamTimeout().longValue();
        SseEmitter emitter = new SseEmitter(streamTimeout);
        AtomicBoolean replyDelivered = new AtomicBoolean(false);
        AtomicReference<HttpURLConnection> connectionRef = new AtomicReference<>();
        AtomicReference<Future<?>> futureRef = new AtomicReference<>();

        Runnable streamTask = () -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(config.getBaseUrl() + config.getChatStreamUrl());
                conn = (HttpURLConnection) url.openConnection();
                connectionRef.set(conn);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "text/event-stream");
                applyServiceAuth(conn);
                conn.setConnectTimeout(config.getConnectTimeout());
                conn.setReadTimeout(config.getReadTimeout());
                conn.setDoOutput(true);

                String requestBody = buildRequest(
                        message, sessionId, userId, mode, contextData);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(requestBody.getBytes(StandardCharsets.UTF_8));
                }

                int statusCode = conn.getResponseCode();
                if (statusCode != HttpURLConnection.HTTP_OK) {
                    String responseBody = AgentResponseUtil.readResponseBody(conn, statusCode);
                    JsonNode error = AgentResponseUtil.normalizeError(
                            objectMapper,
                            responseBody,
                            statusCode,
                            "AGENT_STREAM_HTTP_ERROR",
                            "Agent 流式请求失败");
                    throw remoteFailure(error, "AGENT_STREAM_HTTP_ERROR", "Agent 流式请求失败");
                }

                StringBuilder fullReply = new StringBuilder();
                boolean streamFailed = false;
                boolean terminalReceived = false;
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = readBoundedLine(br, MAX_STREAM_EVENT_CHARS)) != null) {
                        line = line.trim();
                        if (!line.startsWith("data:")) {
                            continue;
                        }
                        String data = line.substring(5).trim();
                        if ("[DONE]".equals(data)) {
                            terminalReceived = true;
                            break;
                        }
                        try {
                            JsonNode node = objectMapper.readTree(data);
                            String eventType = node.path("type").asText("");
                            String content = node.path("content").asText("");

                            if ("token".equals(eventType) && !content.isEmpty()) {
                                if (content.length()
                                        > MAX_STREAM_REPLY_CHARS - fullReply.length()) {
                                    streamFailed = true;
                                    log.warn("Agent 流式回复超过大小限制");
                                    emitter.send(SseEmitter.event().name("error").data(
                                            "Agent 回复过长，请缩小问题范围"));
                                    break;
                                }
                                fullReply.append(content);
                                emitter.send(SseEmitter.event().data(content));
                            } else if ("done".equals(eventType) && !content.isEmpty()) {
                                // Python's done event repeats the complete response after tokens.
                                // Only use it when no token event was emitted, avoiding duplicates.
                                if (fullReply.length() == 0) {
                                    if (content.length() > MAX_STREAM_REPLY_CHARS) {
                                        streamFailed = true;
                                        log.warn("Agent 流式回复超过大小限制");
                                        emitter.send(SseEmitter.event().name("error").data(
                                                "Agent 回复过长，请缩小问题范围"));
                                        break;
                                    }
                                    fullReply.append(content);
                                    emitter.send(SseEmitter.event().data(content));
                                }
                            } else if ("error".equals(eventType)) {
                                streamFailed = true;
                                log.warn("Agent 流式响应返回错误事件");
                                emitter.send(SseEmitter.event().name("error").data(
                                        "Agent 流式请求失败，请稍后重试"));
                                break;
                            }
                        } catch (IOException parseError) {
                            streamFailed = true;
                            log.warn("解析 Agent 流式事件失败，errorType={}",
                                    parseError.getClass().getSimpleName());
                            emitter.send(SseEmitter.event().name("error").data(
                                    "Agent 流式响应格式无效，请稍后重试"));
                            break;
                        }
                    }
                }

                if (!streamFailed && !terminalReceived) {
                    streamFailed = true;
                    log.warn("Agent 流式响应缺少终止标记");
                    emitter.send(SseEmitter.event().name("error").data(
                            "Agent 流式响应不完整，请稍后重试"));
                }

                if (!streamFailed
                        && terminalReceived
                        && fullReply.length() > 0
                        && completedReplyConsumer != null
                        && replyDelivered.compareAndSet(false, true)) {
                    completedReplyConsumer.accept(fullReply.toString());
                }
                emitter.complete();
            } catch (Exception e) {
                log.error("Agent 流式调用失败，errorType={}",
                        e.getClass().getSimpleName());
                try {
                    emitter.send(SseEmitter.event().name("error").data(
                            "Agent 流式调用失败，请稍后重试"));
                    emitter.complete();
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            } finally {
                HttpURLConnection activeConnection = connectionRef.getAndSet(null);
                if (activeConnection != null) {
                    activeConnection.disconnect();
                }
            }
        };

        emitter.onCompletion(() -> cancelStream(connectionRef, futureRef));
        emitter.onTimeout(() -> {
            cancelStream(connectionRef, futureRef);
            completeWithSafeError(emitter, "Agent 流式请求超时");
        });
        emitter.onError(error -> cancelStream(connectionRef, futureRef));

        try {
            futureRef.set(executorService.submit(streamTask));
        } catch (RejectedExecutionException rejected) {
            log.warn("Agent 流式请求被限流，线程池与队列均已满");
            completeWithSafeError(emitter, "Agent 流式服务繁忙，请稍后重试");
        }

        return emitter;
    }

    private static String readBoundedLine(BufferedReader reader, int maxChars)
            throws IOException {
        StringBuilder line = new StringBuilder(Math.min(maxChars, 256));
        boolean readAny = false;
        int character;
        while ((character = reader.read()) != -1) {
            readAny = true;
            if (character == '\n' || character == '\r') {
                break;
            }
            if (line.length() >= maxChars) {
                throw new IOException("Agent stream event exceeds size limit");
            }
            line.append((char) character);
        }
        if (!readAny && line.length() == 0) {
            return null;
        }
        return line.toString();
    }

    private static void cancelStream(
            AtomicReference<HttpURLConnection> connectionRef,
            AtomicReference<Future<?>> futureRef) {
        HttpURLConnection connection = connectionRef.getAndSet(null);
        if (connection != null) {
            connection.disconnect();
        }
        Future<?> future = futureRef.get();
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
    }

    private static void completeWithSafeError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(message));
            emitter.complete();
        } catch (Exception sendFailure) {
            emitter.completeWithError(
                    new IllegalStateException("Agent 流式连接已终止"));
        }
    }

    /**
     * 搬运对话历史到 Python 的结构化快捷问题链，并只解析其结构化响应。
     */
    public List<String> generateSmartQuestions(List<Map<String, Object>> chatHistory, String userId) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.set("chat_history", normalizeChatHistory(chatHistory));
            root.put("user_id", userId == null ? "" : userId);
            putLlmConfig(root);

            JsonNode response = requestJson(
                    "POST",
                    config.getSmartQuestionsUrl(), objectMapper.writeValueAsString(root));
            requireSuccess(
                    response,
                    "AGENT_SMART_QUESTIONS_FAILED",
                    "Agent 快捷问题请求失败");
            JsonNode questions = response.path("data").path("questions");
            if (!questions.isArray() || questions.size() != 3) {
                throw new RuntimeException("Agent 快捷问题响应格式无效");
            }
            List<String> result = new ArrayList<>(3);
            for (JsonNode question : questions) {
                String text = question.asText("").trim();
                if (text.isEmpty()) {
                    throw new RuntimeException("Agent 快捷问题不能为空");
                }
                result.add(text);
            }
            return result;
        } catch (Exception e) {
            log.error("调用 Agent 快捷问题服务失败，errorType={}",
                    e.getClass().getSimpleName());
            throw new RuntimeException("调用 Agent 快捷问题服务失败", e);
        }
    }

    private ArrayNode normalizeChatHistory(List<Map<String, Object>> chatHistory) {
        if (chatHistory == null) {
            throw new IllegalArgumentException("对话历史不能为空");
        }
        ArrayNode normalized = objectMapper.createArrayNode();
        for (Map<String, Object> item : chatHistory) {
            if (item == null || !(item.get("content") instanceof String)) {
                throw new IllegalArgumentException("对话历史内容无效");
            }
            String content = (String) item.get("content");
            if (content.trim().isEmpty()) {
                throw new IllegalArgumentException("对话历史内容无效");
            }

            ObjectNode entry = normalized.addObject();
            entry.put("content", content);
            entry.put("role", normalizeHistoryRole(item));
        }
        return normalized;
    }

    private static String normalizeHistoryRole(Map<String, Object> item) {
        Object role = item.get("role");
        if (role != null && !String.valueOf(role).trim().isEmpty()) {
            return requireHistoryRole(String.valueOf(role));
        }

        Object messageType = item.get("messageType");
        if (messageType != null && !String.valueOf(messageType).trim().isEmpty()) {
            return requireHistoryRole(String.valueOf(messageType));
        }

        Object isUser = item.get("isUser");
        if (isUser instanceof Boolean) {
            return Boolean.TRUE.equals(isUser) ? "user" : "assistant";
        }
        throw new IllegalArgumentException("对话历史角色无效");
    }

    private static String requireHistoryRole(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!"user".equals(normalized) && !"assistant".equals(normalized)) {
            throw new IllegalArgumentException("对话历史角色无效");
        }
        return normalized;
    }

    /** 删除指定用户与会话对应的 Python checkpoint 记忆。 */
    public void deleteThreadMemory(String sessionId, String userId) {
        try {
            ObjectNode request = objectMapper.createObjectNode();
            request.put("user_id", userId == null ? "" : userId.trim());
            request.put("thread_id", sessionId == null ? "" : sessionId.trim());

            JsonNode response = requestJson(
                    "DELETE",
                    config.getThreadDeleteUrl(),
                    objectMapper.writeValueAsString(request));
            requireSuccess(
                    response,
                    "AGENT_THREAD_DELETE_FAILED",
                    "Agent 会话记忆删除失败");
        } catch (Exception e) {
            log.error("删除 Agent 会话记忆失败，errorType={}",
                    e.getClass().getSimpleName());
            throw new RuntimeException("删除 Agent 会话记忆失败", e);
        }
    }

    String buildRequest(
            String message,
            String sessionId,
            String userId,
            String mode,
            Object contextData) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("message", message);
            root.put("mode", mode);
            if (contextData != null) {
                root.set("context_data", objectMapper.valueToTree(contextData));
            }
            root.put("style", config.getStyle());
            if (sessionId != null && !sessionId.trim().isEmpty()) {
                root.put("thread_id", sessionId.trim());
            }
            if (userId != null && !userId.trim().isEmpty()) {
                root.put("user_id", userId.trim());
            }
            root.put("max_iterations", config.getMaxIterations());
            putLlmConfig(root);

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("构建请求失败", e);
        }
    }

    private JsonNode requestJson(
            String method, String endpointPath, String requestBody) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(config.getBaseUrl() + endpointPath);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            applyServiceAuth(conn);
            conn.setConnectTimeout(config.getConnectTimeout());
            conn.setReadTimeout(config.getReadTimeout());
            if (requestBody != null) {
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(requestBody.getBytes(StandardCharsets.UTF_8));
                }
            }

            int statusCode = conn.getResponseCode();
            String responseBody = AgentResponseUtil.readResponseBody(conn, statusCode);
            if (statusCode < 200 || statusCode >= 300) {
                log.warn("Python Agent HTTP error, method={}, status={}",
                        method, statusCode);
                return AgentResponseUtil.normalizeError(
                        objectMapper,
                        responseBody,
                        statusCode,
                        "AGENT_HTTP_ERROR",
                        "Python Agent 请求失败");
            }
            return AgentResponseUtil.parseSuccess(objectMapper, responseBody);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private void requireSuccess(JsonNode response, String fallbackCode, String fallbackMessage) {
        if (!response.path("success").asBoolean(false)) {
            throw remoteFailure(response, fallbackCode, fallbackMessage);
        }
    }

    private RuntimeException remoteFailure(
            JsonNode response,
            String fallbackCode,
            String fallbackMessage) {
        String code = AgentResponseUtil.errorCode(response, fallbackCode);
        String message = AgentResponseUtil.errorMessage(response, fallbackMessage);
        return new RuntimeException(code + ": " + message);
    }

    private void putLlmConfig(ObjectNode root) {
        if (config.getLlmApiKey() != null && !config.getLlmApiKey().isEmpty()) {
            ObjectNode llmConfig = root.putObject("llm_config");
            llmConfig.put("api_key", config.getLlmApiKey());
            llmConfig.put("model", config.getLlmModel());
            if (config.getLlmBaseUrl() != null && !config.getLlmBaseUrl().isEmpty()) {
                llmConfig.put("base_url", config.getLlmBaseUrl());
            }
        }
    }

    private String extractResponse(JsonNode root) {
        JsonNode nestedResponse = root.path("data").path("response");
        JsonNode responseNode = nestedResponse.isMissingNode()
                ? root.path("response") : nestedResponse;
        if (!responseNode.isTextual()) {
            throw new IllegalStateException("Agent 对话响应缺少有效内容");
        }
        String response = responseNode.asText();
        if (response.trim().isEmpty()) {
            throw new IllegalStateException("Agent 对话响应缺少有效内容");
        }
        return response;
    }

    private void applyServiceAuth(HttpURLConnection conn) {
        String serviceApiKey = config.getServiceApiKey();
        if (serviceApiKey == null || serviceApiKey.trim().isEmpty()) {
            throw new IllegalStateException("agent.service-api-key is not configured");
        }
        conn.setRequestProperty("X-Agent-Service-Key", serviceApiKey);
    }
}
