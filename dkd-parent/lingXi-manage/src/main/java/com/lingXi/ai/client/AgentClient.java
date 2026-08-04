package com.lingXi.ai.client;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.lingXi.ai.config.AgentConfig;
import com.lingXi.ai.domain.dto.AgentUserContext;
import com.lingXi.ai.domain.dto.tool.AgentToolAccess;
import com.lingXi.ai.service.AgentToolTokenService;
import com.lingXi.aiVedio.config.AiVideoModelConfigService;
import com.lingXi.aiVedio.domain.dto.AiVideoModelConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;
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
    /** 签发与对话生命周期绑定的短期 Java Tool Gateway 令牌。 */
    private final AgentToolTokenService toolTokenService;

    /**
     * 创建生产环境使用的 Agent 客户端。
     *
     * @param config Agent 通信配置
     * @param modelConfigService 大模型运行配置服务
     * @param toolTokenService 短期工具令牌服务
     */
    @Autowired
    public AgentClient(
            AgentConfig config,
            AiVideoModelConfigService modelConfigService,
            AgentToolTokenService toolTokenService) {
        this(config, modelConfigService, toolTokenService, createStreamExecutor(config));
    }

    /**
     * 使用外部执行器创建客户端，供单元测试控制异步任务生命周期。
     *
     * @param config Agent 通信配置
     * @param executorService 流式任务执行器
     */
    AgentClient(AgentConfig config, ExecutorService executorService) {
        this(config, null, new AgentToolTokenService(config), executorService);
    }

    /** 使用共享令牌服务和外部执行器创建客户端，供契约测试验证撤销生命周期。 */
    AgentClient(
            AgentConfig config,
            AgentToolTokenService toolTokenService,
            ExecutorService executorService) {
        this(config, null, toolTokenService, executorService);
    }

    /** 统一保存依赖，避免不同构造入口产生不一致的初始化逻辑。 */
    private AgentClient(
            AgentConfig config,
            AiVideoModelConfigService modelConfigService,
            AgentToolTokenService toolTokenService,
            ExecutorService executorService) {
        this.config = config;
        this.modelConfigService = modelConfigService;
        this.toolTokenService = toolTokenService;
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
        return chat(message, sessionId, userId, "chat", null, null);
    }

    /** 使用可信 Java 登录上下文同步调用 Agent。 */
    public String chat(
            String message, String sessionId, AgentUserContext userContext) {
        return chat(
                message,
                sessionId,
                userContext.getUserId(),
                "chat",
                null,
                userContext);
    }

    /**
     * 搬运结构化业务数据，由 Python 选择并构造数据分析 Prompt
     */
    public String chatWithContext(
            String message,
            Object contextData,
            String sessionId,
            String userId) {
        return chat(
                message, sessionId, userId, "context_analysis", contextData, null);
    }

    /** 使用可信 Java 登录上下文同步分析结构化页面快照。 */
    public String chatWithContext(
            String message,
            Object contextData,
            String sessionId,
            AgentUserContext userContext) {
        return chat(
                message,
                sessionId,
                userContext.getUserId(),
                "context_analysis",
                contextData,
                userContext);
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
            Object contextData,
            AgentUserContext userContext) {
        AgentToolAccess toolAccess = createToolAccess(userContext, sessionId);
        try {
            String requestBody = buildRequest(
                    message, sessionId, userId, mode, contextData, userContext, toolAccess);
            JsonNode root = requestJson("POST", config.getChatInvokeUrl(), requestBody);
            requireSuccess(root, "AGENT_CHAT_FAILED", "Agent 对话请求失败");
            return extractResponse(root);
        } catch (Exception e) {
            log.error("调用 Agent 服务失败，errorType={}",
                    e.getClass().getSimpleName());
            throw new RuntimeException("调用 Agent 服务失败", e);
        } finally {
            toolTokenService.revoke(toolAccess);
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
                message,
                sessionId,
                userId,
                "chat",
                null,
                null,
                completedReplyConsumer);
    }

    /** 使用可信 Java 登录上下文流式调用 Agent。 */
    public SseEmitter streamChat(
            String message,
            String sessionId,
            AgentUserContext userContext,
            Consumer<String> completedReplyConsumer) {
        return streamChat(
                message,
                sessionId,
                userContext.getUserId(),
                "chat",
                null,
                userContext,
                completedReplyConsumer);
    }

    /** 使用可信 Java 登录上下文并保留白名单化结构事件的 V2 流式调用。 */
    public SseEmitter streamChatV2(
            String message,
            String sessionId,
            AgentUserContext userContext,
            Consumer<String> completedReplyConsumer) {
        return streamChat(
                message,
                sessionId,
                userContext.getUserId(),
                "chat",
                null,
                userContext,
                completedReplyConsumer,
                true);
    }

    /** 使用当前登录态的新令牌恢复一个已经由 Java 记录决定的受控动作。 */
    public SseEmitter streamResumeAction(
            String sessionId,
            AgentUserContext userContext,
            String actionId,
            String decision,
            Consumer<String> completedReplyConsumer) {
        return streamAgent(
                "",
                sessionId,
                userContext.getUserId(),
                "chat",
                null,
                userContext,
                completedReplyConsumer,
                true,
                actionId,
                decision);
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
                null,
                completedReplyConsumer);
    }

    /** 使用可信 Java 登录上下文流式分析结构化页面快照。 */
    public SseEmitter streamChatWithContext(
            String message,
            Object contextData,
            String sessionId,
            AgentUserContext userContext,
            Consumer<String> completedReplyConsumer) {
        return streamChat(
                message,
                sessionId,
                userContext.getUserId(),
                "context_analysis",
                contextData,
                userContext,
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
            AgentUserContext userContext,
            Consumer<String> completedReplyConsumer) {
        return streamChat(
                message,
                sessionId,
                userId,
                mode,
                contextData,
                userContext,
                completedReplyConsumer,
                false);
    }

    /** V1 聚合文本与 V2 结构事件共用同一条受控上游读取链路。 */
    private SseEmitter streamChat(
            String message,
            String sessionId,
            String userId,
            String mode,
            Object contextData,
            AgentUserContext userContext,
            Consumer<String> completedReplyConsumer,
            boolean structuredEvents) {
        return streamAgent(
                message,
                sessionId,
                userId,
                mode,
                contextData,
                userContext,
                completedReplyConsumer,
                structuredEvents,
                null,
                null);
    }

    /** 普通聊天和动作恢复共用同一条有界、可取消的 SSE 转发实现。 */
    private SseEmitter streamAgent(
            String message,
            String sessionId,
            String userId,
            String mode,
            Object contextData,
            AgentUserContext userContext,
            Consumer<String> completedReplyConsumer,
            boolean structuredEvents,
            String actionId,
            String decision) {
        long streamTimeout = config.getStreamTimeout() == null
                || config.getStreamTimeout().longValue() <= 0L
                        ? 310_000L : config.getStreamTimeout().longValue();
        SseEmitter emitter = new SseEmitter(streamTimeout);
        AtomicBoolean replyDelivered = new AtomicBoolean(false);
        AtomicReference<HttpURLConnection> connectionRef = new AtomicReference<>();
        AtomicReference<Future<?>> futureRef = new AtomicReference<>();
        AgentToolAccess toolAccess = createToolAccess(userContext, sessionId);

        Runnable streamTask = () -> {
            HttpURLConnection conn = null;
            try {
                String streamPath = actionId == null
                        ? (structuredEvents
                                ? config.getChatStreamV2Url() : config.getChatStreamUrl())
                        : config.getChatResumeUrl();
                URL url = new URL(config.getBaseUrl() + streamPath);
                conn = (HttpURLConnection) url.openConnection();
                connectionRef.set(conn);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "text/event-stream");
                applyServiceAuth(conn);
                conn.setConnectTimeout(config.getConnectTimeout());
                conn.setReadTimeout(config.getReadTimeout());
                conn.setDoOutput(true);

                String requestBody = actionId == null
                        ? buildRequest(
                                message, sessionId, userId, mode, contextData,
                                userContext, toolAccess)
                        : buildResumeRequest(
                                sessionId, userId, userContext, toolAccess,
                                actionId, decision);
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
                boolean approvalPending = false;
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
                                    sendSafeStreamError(emitter, structuredEvents,
                                            "Agent 回复过长，请缩小问题范围");
                                    break;
                                }
                                fullReply.append(content);
                                if (structuredEvents) {
                                    sendStructuredEvent(emitter, eventType, node);
                                } else {
                                    emitter.send(SseEmitter.event().data(content));
                                }
                            } else if ("done".equals(eventType) && !content.isEmpty()) {
                                if (fullReply.length() == 0) {
                                    if (content.length() > MAX_STREAM_REPLY_CHARS) {
                                        streamFailed = true;
                                        log.warn("Agent 流式回复超过大小限制");
                                        sendSafeStreamError(emitter, structuredEvents,
                                                "Agent 回复过长，请缩小问题范围");
                                        break;
                                    }
                                    fullReply.append(content);
                                    if (!structuredEvents) {
                                        emitter.send(SseEmitter.event().data(content));
                                    }
                                }
                                if (structuredEvents) {
                                    sendStructuredEvent(emitter, eventType, node);
                                }
                            } else if ("done".equals(eventType)) {
                                if (structuredEvents) {
                                    sendStructuredEvent(emitter, eventType, node);
                                }
                            } else if ("error".equals(eventType)) {
                                streamFailed = true;
                                log.warn("Agent 流式响应返回错误事件");
                                sendSafeStreamError(emitter, structuredEvents,
                                        "Agent 流式请求失败，请稍后重试");
                                break;
                            } else if (structuredEvents && isStructuredEvent(eventType)) {
                                if ("approval_required".equals(eventType)) {
                                    approvalPending = true;
                                }
                                sendStructuredEvent(emitter, eventType, node);
                            }
                        } catch (IOException parseError) {
                            streamFailed = true;
                            log.warn("解析 Agent 流式事件失败，errorType={}",
                                    parseError.getClass().getSimpleName());
                            sendSafeStreamError(emitter, structuredEvents,
                                    "Agent 流式响应格式无效，请稍后重试");
                            break;
                        }
                    }
                }

                if (!streamFailed && !terminalReceived) {
                    streamFailed = true;
                    log.warn("Agent 流式响应缺少终止标记");
                    sendSafeStreamError(emitter, structuredEvents,
                            "Agent 流式响应不完整，请稍后重试");
                }

                if (!streamFailed
                        && terminalReceived
                        && !approvalPending
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
                    sendSafeStreamError(emitter, structuredEvents,
                            "Agent 流式调用失败，请稍后重试");
                    emitter.complete();
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            } finally {
                HttpURLConnection activeConnection = connectionRef.getAndSet(null);
                if (activeConnection != null) {
                    activeConnection.disconnect();
                }
                toolTokenService.revoke(toolAccess);
            }
        };

        emitter.onCompletion(() -> {
            toolTokenService.revoke(toolAccess);
            cancelStream(connectionRef, futureRef);
        });
        emitter.onTimeout(() -> {
            toolTokenService.revoke(toolAccess);
            cancelStream(connectionRef, futureRef);
            completeWithSafeError(emitter, "Agent 流式请求超时", structuredEvents);
        });
        emitter.onError(error -> {
            toolTokenService.revoke(toolAccess);
            cancelStream(connectionRef, futureRef);
        });

        try {
            futureRef.set(executorService.submit(streamTask));
        } catch (RejectedExecutionException rejected) {
            toolTokenService.revoke(toolAccess);
            log.warn("Agent 流式请求被限流，线程池与队列均已满");
            completeWithSafeError(
                    emitter, "Agent 流式服务繁忙，请稍后重试", structuredEvents);
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

    /** 只允许前端协议声明的结构事件通过 Java 边界。 */
    private static boolean isStructuredEvent(String eventType) {
        return "tool_start".equals(eventType)
                || "tool_progress".equals(eventType)
                || "tool_end".equals(eventType)
                || "citation".equals(eventType)
                || "clarification".equals(eventType)
                || "memory_saved".equals(eventType)
                || "approval_required".equals(eventType)
                || "action_completed".equals(eventType)
                || "action_rejected".equals(eventType)
                || "heartbeat".equals(eventType);
    }

    /** 重建用户可见事件，禁止透传工具原始参数、结果、内部节点和任意扩展字段。 */
    private void sendStructuredEvent(
            SseEmitter emitter, String eventType, JsonNode source) throws IOException {
        ObjectNode safe = objectMapper.createObjectNode();
        safe.put("type", eventType);

        if ("token".equals(eventType)
                || "done".equals(eventType)
                || "clarification".equals(eventType)) {
            String content = source.path("content").asText("");
            if (!content.isEmpty()) {
                safe.put("content", content);
            }
        }

        if (eventType.startsWith("tool_")) {
            String tool = source.path("tool").asText("unknown");
            safe.put("tool", safeToolName(tool));
        }

        JsonNode data = source.path("data");
        if (("tool_progress".equals(eventType) || "tool_end".equals(eventType))
                && data.isObject()) {
            ObjectNode safeData = safe.putObject("data");
            String status = data.path("status").asText("");
            if (status.matches("^[a-z_]{1,32}$")) {
                safeData.put("status", status);
            }
            if (data.path("result_count").canConvertToInt()
                    && data.path("result_count").asInt() >= 0) {
                safeData.put("result_count", data.path("result_count").asInt());
            }
        } else if ("tool_start".equals(eventType)) {
            safe.putObject("data").put("status", "started");
        } else if ("citation".equals(eventType) && data.isObject()) {
            ObjectNode citation = safe.putObject("data");
            copyDisplayText(data, citation, "title", 256);
            copyDisplayText(data, citation, "section", 256);
            copyDisplayText(data, citation, "version", 128);
            copyDisplayText(data, citation, "source_id", 256);
            if (data.path("score").isNumber()) {
                citation.put("score", data.path("score").asDouble());
            }
        } else if ("memory_saved".equals(eventType) && data.isObject()) {
            ObjectNode memory = safe.putObject("data");
            copyLabel(data, memory, "preference", 64);
            copyLabel(data, memory, "value", 64);
        } else if (("approval_required".equals(eventType)
                || "action_completed".equals(eventType)
                || "action_rejected".equals(eventType)) && data.isObject()) {
            copySafeAction(data, safe.putObject("data"));
        }

        emitter.send(SseEmitter.event()
                .name(eventType)
                .data(objectMapper.writeValueAsString(safe)));
    }

    private void sendSafeStreamError(
            SseEmitter emitter, boolean structuredEvents, String message) throws IOException {
        if (!structuredEvents) {
            emitter.send(SseEmitter.event().name("error").data(message));
            return;
        }
        ObjectNode safe = objectMapper.createObjectNode();
        safe.put("type", "error");
        safe.put("content", message);
        emitter.send(SseEmitter.event()
                .name("error")
                .data(objectMapper.writeValueAsString(safe)));
    }

    private static String safeToolName(String value) {
        return value != null && value.matches("^[a-z_]{1,64}$") ? value : "unknown";
    }

    private static void copyDisplayText(
            JsonNode source, ObjectNode target, String field, int maxLength) {
        JsonNode value = source.path(field);
        if (value.isTextual()) {
            String text = value.asText();
            target.put(field, text.substring(0, Math.min(text.length(), maxLength)));
        }
    }

    private static void copyLabel(
            JsonNode source, ObjectNode target, String field, int maxLength) {
        JsonNode value = source.path(field);
        if (value.isTextual()) {
            String text = value.asText();
            if (text.matches("^[a-z_]{1," + maxLength + "}$")) {
                target.put(field, text);
            }
        }
    }

    /** 复制受控动作公开字段，拒绝 user/thread/region/idempotency/token 等内部数据。 */
    private static void copySafeAction(JsonNode source, ObjectNode target) {
        copyIdentifier(source, target, "action_id", 64);
        copyIdentifier(source, target, "action_type", 64);
        copyIdentifier(source, target, "status", 20);
        copyDisplayText(source, target, "description", 500);
        copyDisplayText(source, target, "impact", 256);
        copyDisplayText(source, target, "expires_at", 128);
        JsonNode rawTarget = source.path("target");
        if (rawTarget.isObject()) {
            ObjectNode safeTarget = target.putObject("target");
            copyIdentifier(rawTarget, safeTarget, "inner_code", 64);
        }
        JsonNode result = source.path("result");
        if (result.isObject()) {
            ObjectNode safeResult = target.putObject("result");
            if (result.path("task_id").canConvertToLong()
                    && result.path("task_id").asLong() > 0L) {
                safeResult.put("task_id", result.path("task_id").asLong());
            }
            copyIdentifier(result, safeResult, "task_code", 64);
        }
    }

    private static void copyIdentifier(
            JsonNode source, ObjectNode target, String field, int maxLength) {
        JsonNode value = source.path(field);
        if (value.isTextual()) {
            String text = value.asText();
            if (text.length() <= maxLength
                    && text.matches("^[A-Za-z0-9:_-]+$")) {
                target.put(field, text);
            }
        }
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
    private void completeWithSafeError(
            SseEmitter emitter, String message, boolean structuredEvents) {
        try {
            sendSafeStreamError(emitter, structuredEvents, message);
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

    /** 获取当前认证用户的规范化长期回答偏好。 */
    public Map<String, Object> listLongTermMemories(String userId) {
        try {
            ObjectNode request = memoryUserRequest(userId);
            JsonNode response = requestJson(
                    "POST",
                    config.getMemoryListUrl(),
                    objectMapper.writeValueAsString(request));
            requireSuccess(response, "AGENT_MEMORY_LIST_FAILED", "获取长期记忆失败");
            return responseDataMap(response, "长期记忆响应格式无效");
        } catch (Exception e) {
            log.error("获取 Agent 长期记忆失败，errorType={}",
                    e.getClass().getSimpleName());
            throw new RuntimeException("获取长期记忆失败", e);
        }
    }

    /** 修改当前认证用户的一项规范化长期回答偏好。 */
    public Map<String, Object> updateLongTermPreference(
            String userId, String preference, String value) {
        try {
            ObjectNode request = memoryUserRequest(userId);
            request.put("preference", preference);
            request.put("value", value);
            JsonNode response = requestJson(
                    "PUT",
                    config.getMemoryPreferenceUrl(),
                    objectMapper.writeValueAsString(request));
            requireSuccess(response, "AGENT_MEMORY_UPDATE_FAILED", "更新长期记忆失败");
            return responseDataMap(response, "长期记忆响应格式无效");
        } catch (Exception e) {
            log.error("更新 Agent 长期记忆失败，errorType={}",
                    e.getClass().getSimpleName());
            throw new RuntimeException("更新长期记忆失败", e);
        }
    }

    /** 幂等清空当前认证用户的全部长期回答偏好。 */
    public Map<String, Object> clearLongTermMemories(String userId) {
        try {
            ObjectNode request = memoryUserRequest(userId);
            JsonNode response = requestJson(
                    "DELETE",
                    config.getMemoryClearUrl(),
                    objectMapper.writeValueAsString(request));
            requireSuccess(response, "AGENT_MEMORY_CLEAR_FAILED", "清空长期记忆失败");
            return responseDataMap(response, "长期记忆响应格式无效");
        } catch (Exception e) {
            log.error("清空 Agent 长期记忆失败，errorType={}",
                    e.getClass().getSimpleName());
            throw new RuntimeException("清空长期记忆失败", e);
        }
    }

    private ObjectNode memoryUserRequest(String userId) {
        if (userId == null || userId.trim().isEmpty() || userId.length() > 128) {
            throw new IllegalArgumentException("用户ID无效");
        }
        ObjectNode request = objectMapper.createObjectNode();
        request.put("user_id", userId.trim());
        return request;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> responseDataMap(JsonNode response, String errorMessage) {
        JsonNode data = response.path("data");
        if (!data.isObject()) {
            throw new IllegalStateException(errorMessage);
        }
        return objectMapper.convertValue(data, Map.class);
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
        return buildRequest(message, sessionId, userId, mode, contextData, null);
    }

    /** 构造动作恢复请求；决定已经由 Java 登录端持久化，不接受浏览器身份字段。 */
    String buildResumeRequest(
            String sessionId,
            String userId,
            AgentUserContext userContext,
            AgentToolAccess toolAccess,
            String actionId,
            String decision) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("action_id", actionId);
            root.put("decision", decision);
            root.put("style", config.getStyle());
            root.put("thread_id", sessionId.trim());
            root.put("user_id", userId.trim());
            putUserContext(root, userContext);
            putToolAccess(root, toolAccess);
            root.put("max_iterations", config.getMaxIterations());
            putLlmConfig(root);
            return objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new RuntimeException("构建动作恢复请求失败", exception);
        }
    }

    /** 构造包含可选可信用户上下文的统一对话请求体。 */
    String buildRequest(
            String message,
            String sessionId,
            String userId,
            String mode,
            Object contextData,
            AgentUserContext userContext) {
        return buildRequest(
                message, sessionId, userId, mode, contextData, userContext, null);
    }

    /** 构造包含可信上下文和不可打印短期工具凭据的统一对话请求体。 */
    String buildRequest(
            String message,
            String sessionId,
            String userId,
            String mode,
            Object contextData,
            AgentUserContext userContext,
            AgentToolAccess toolAccess) {
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
            putUserContext(root, userContext);
            putToolAccess(root, toolAccess);
            root.put("max_iterations", config.getMaxIterations());
            putLlmConfig(root);

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("构建请求失败", e);
        }
    }

    /** 令牌只写入运行时字段，不进入用户上下文、提示词或日志。 */
    private static void putToolAccess(ObjectNode root, AgentToolAccess toolAccess) {
        if (toolAccess == null) {
            return;
        }
        root.put("agent_request_id", toolAccess.getAgentRequestId());
        root.put("tool_access_token", toolAccess.getToken());
    }

    /** 旧内部调用没有可信上下文时不签发工具令牌。 */
    private AgentToolAccess createToolAccess(
            AgentUserContext userContext, String sessionId) {
        if (userContext == null || sessionId == null || sessionId.trim().isEmpty()) {
            return null;
        }
        return toolTokenService.issue(userContext, sessionId.trim());
    }

    /** 白名单序列化可信用户上下文，避免整个登录对象或敏感字段进入请求。 */
    private void putUserContext(ObjectNode root, AgentUserContext userContext) {
        if (userContext == null) {
            return;
        }
        ObjectNode contextNode = root.putObject("user_context");
        contextNode.put("user_name", userContext.getUserName());
        putOptionalText(contextNode, "role_code", userContext.getRoleCode());
        putOptionalText(contextNode, "role_name", userContext.getRoleName());
        if (userContext.getRegionId() != null) {
            contextNode.put("region_id", userContext.getRegionId());
        }
        putOptionalText(contextNode, "region_name", userContext.getRegionName());
        ArrayNode permissions = contextNode.putArray("permissions");
        for (String permission : userContext.getPermissions()) {
            permissions.add(permission);
        }
    }

    /** 仅在非空时写入可选单行标签。 */
    private static void putOptionalText(ObjectNode node, String field, String value) {
        if (value != null && !value.trim().isEmpty()) {
            node.put(field, value.trim());
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
