package com.lingXi.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lingXi.ai.config.AgentConfig;
import com.lingXi.aiVedio.config.AiVideoModelConfigService;
import com.lingXi.aiVedio.domain.dto.AiVideoModelConfig;
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

/**
 * Agent 客户端
 * <p>负责与 Python Agent 服务进行 HTTP 通信，支持同步/流式对话、快捷问题生成和会话记忆管理。</p>
 */
@Slf4j
@Component
public class AgentClient {

    /** 单次流式回答允许累计的最大字符数，防止异常响应耗尽服务内存。 */
    static final int MAX_STREAM_REPLY_CHARS = 200_000;
    /** 单个 SSE 事件允许读取的最大字符数。 */
    static final int MAX_STREAM_EVENT_CHARS = 1_048_576;

    /** Python Agent 的地址、接口路径、超时和线程池配置。 */
    private final AgentConfig config;
    /** 用于构造请求体并解析 Agent JSON 响应。 */
    private final ObjectMapper objectMapper = new ObjectMapper();
    /** 独立执行 SSE 转发任务，避免占用 Web 请求线程。 */
    private final ExecutorService executorService;
    /** 提供当前启用的大模型配置，并随请求安全传递给 Python Agent。 */
    private final AiVideoModelConfigService modelConfigService;

    /**
     * 创建生产环境使用的 Agent 客户端。
     *
     * @param config Agent 通信配置
     * @param modelConfigService 大模型运行配置服务
     */
    @Autowired
    public AgentClient(AgentConfig config, AiVideoModelConfigService modelConfigService) {
        this(config, modelConfigService, createStreamExecutor(config));
    }

    /**
     * 使用外部执行器创建客户端，供单元测试控制异步任务生命周期。
     *
     * @param config Agent 通信配置
     * @param executorService 流式任务执行器
     */
    AgentClient(AgentConfig config, ExecutorService executorService) {
        this(config, null, executorService);
    }

    /** 统一保存依赖，避免不同构造入口产生不一致的初始化逻辑。 */
    private AgentClient(AgentConfig config, AiVideoModelConfigService modelConfigService,
            ExecutorService executorService) {
        this.config = config;
        this.modelConfigService = modelConfigService;
        this.executorService = executorService;
    }

    /** 应用关闭时中断仍在运行的流式转发任务并释放线程。 */
    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }

    /**
     * 创建容量受限的 SSE 转发线程池。
     * <p>队列满时直接拒绝新任务，让调用方得到“服务繁忙”提示，避免请求无限堆积。</p>
     */
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

    /** 配置值为空或非正数时使用安全默认值。 */
    private static int positiveOrDefault(Integer value, int fallback) {
        return value == null || value.intValue() <= 0 ? fallback : value.intValue();
    }

    /**
     * 同步调用 Agent 对话接口
     */
    public String chat(String message, String sessionId, String userId) {
        return chat(message, sessionId, userId, "chat", null);
    }

    /**
     * 搬运结构化业务数据，由 Python 选择并构造数据分析 Prompt
     */
    public String chatWithContext(
            String message,
            Object contextData,
            String sessionId,
            String userId) {
        return chat(message, sessionId, userId, "context_analysis", contextData);
    }

    /**
     * 同步对话的统一调用入口，根据 mode 区分普通聊天和上下文分析。
     *
     * @return Python Agent 返回的最终文本回答
     */
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

    /**
     * 流式搬运结构化业务数据，由 Python 选择并构造数据分析 Prompt
     */
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

    /**
     * 建立到 Python Agent 的 SSE 连接，并把 token 事件转发给浏览器。
     * <p>完成、超时和客户端断开都会取消后台任务及 HTTP 连接；仅在收到完整终止标记后
     * 才把聚合后的回答交给持久化回调。</p>
     */
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
                                // Python 的 done 事件会在 token 事件之后再次携带完整响应。
                                // 仅在没有收到 token 事件时采用该内容，避免客户端拼接出重复答案。
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

    /**
     * 读取一个有长度上限的 SSE 数据行。
     *
     * @return 读取到的行；流已结束且没有内容时返回 {@code null}
     * @throws IOException 事件超过上限或底层读取失败时抛出
     */
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

    /** 同时断开远端连接并取消本地异步任务，确保 SSE 资源成对释放。 */
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

    /** 尝试发送统一错误事件；连接已关闭时以安全异常结束发射器。 */
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
     * 搬运对话历史到 Python 的结构化快捷问题链，并解析其结构化响应
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

    /**
     * 把前端可能使用不同角色字段的历史记录转换为 Python 契约要求的结构。
     */
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

    /** 按 role、messageType、isUser 的兼容顺序解析消息角色。 */
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

    /** 仅允许 user 和 assistant 两种标准角色，拒绝未知角色进入提示词。 */
    private static String requireHistoryRole(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!"user".equals(normalized) && !"assistant".equals(normalized)) {
            throw new IllegalArgumentException("对话历史角色无效");
        }
        return normalized;
    }

    /**
     * 删除指定用户与会话对应的 Python checkpoint 记忆
     */
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

    /**
     * 构造发送给 Python Agent 的统一对话请求体。
     * <p>模型密钥只从服务端配置注入，不接受浏览器直接传入。</p>
     */
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

    /**
     * 执行普通 JSON HTTP 请求，并把成功与失败响应归一化为统一结构。
     */
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

    /** 校验 Agent 业务响应标志，失败时转换为包含稳定错误码的异常。 */
    private void requireSuccess(JsonNode response, String fallbackCode, String fallbackMessage) {
        if (!response.path("success").asBoolean(false)) {
            throw remoteFailure(response, fallbackCode, fallbackMessage);
        }
    }

    /** 从标准化响应中提取安全错误码与错误消息。 */
    private RuntimeException remoteFailure(
            JsonNode response,
            String fallbackCode,
            String fallbackMessage) {
        String code = AgentResponseUtil.errorCode(response, fallbackCode);
        String message = AgentResponseUtil.errorMessage(response, fallbackMessage);
        return new RuntimeException(code + ": " + message);
    }

    /** 将当前启用的文本模型配置写入请求，供 Python 创建本次调用的模型客户端。 */
    private void putLlmConfig(ObjectNode root) {
        if (modelConfigService == null) {
            return;
        }
        AiVideoModelConfig runtimeConfig = modelConfigService.getRequiredConfig();
        ObjectNode llmConfig = root.putObject("llm_config");
        llmConfig.put("api_key", runtimeConfig.getApiKey());
        llmConfig.put("model", runtimeConfig.getTextModel());
        llmConfig.put("base_url", runtimeConfig.getWorkspaceBaseUrl());
    }

    /** 兼容新旧响应信封并提取非空回答文本。 */
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

    /** 为 Java 到 Python 的每个请求附加服务间认证头。 */
    private void applyServiceAuth(HttpURLConnection conn) {
        String serviceApiKey = config.getServiceApiKey();
        if (serviceApiKey == null || serviceApiKey.trim().isEmpty()) {
            throw new IllegalStateException("agent.service-api-key is not configured");
        }
        conn.setRequestProperty("X-Agent-Service-Key", serviceApiKey);
    }
}
