package com.lingXi.ai.client;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.lingXi.ai.config.AgentConfig;
import com.lingXi.ai.domain.dto.AgentUserContext;
import com.lingXi.ai.domain.dto.AiChatAttachmentAgentDTO;
import com.lingXi.ai.domain.dto.AiImageOcrRequestDTO;
import com.lingXi.ai.domain.dto.AiImageOcrResultDTO;
import com.lingXi.ai.domain.dto.tool.AgentToolAccess;
import com.lingXi.ai.service.AgentToolTokenService;
import com.lingXi.aiVedio.config.AiVideoModelConfigService;
import com.lingXi.aiVedio.domain.dto.AiVideoModelConfig;
import com.lingXi.system.config.SystemSecurityConfigService;
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
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Agent 客户端
 * <p>负责与 Python Agent 服务进行 HTTP 通信，支持同步/流式对话、快捷问题生成和会话记忆管理。</p>
 */
@Slf4j
@Component
public class AgentClient {

    /**
     * 流式对话终态回调。
     * <p>一次流式请求只会报告一个终态：成功（完整回答）、失败（稳定错误码）或取消（客户端断开）。</p>
     */
    public interface StreamOutcomeListener {

        /** 收到完整回答并确认流正常终止。 */
        void onReply(String fullReply);

        /** 收到 OpenUI 渲染历史（成功回答时携带，未产生则为 null）。 */
        default void onUiArtifacts(String uiJson) {
        }

        /** 流异常终止，携带稳定错误码。 */
        void onFailed(String errorCode);

        /** 客户端在终态前断开。 */
        void onCancelled();
    }

    /** 单次流式回答允许累计的最大字符数，防止异常响应耗尽服务内存。 */
    static final int MAX_STREAM_REPLY_CHARS = 200_000;
    /** 单个 SSE 事件允许读取的最大字符数。 */
    static final int MAX_STREAM_EVENT_CHARS = 1_048_576;

    // ── OpenUI 表现层限制（与 Python app/openui 保持一致）──────────────
    private static final int OPENUI_MAX_SPEC_BYTES = 256 * 1024;
    private static final int OPENUI_MAX_NODES = 120;
    private static final int OPENUI_MAX_DEPTH = 8;
    private static final int OPENUI_MAX_TEXT = 4096;
    private static final int OPENUI_MAX_TITLE = 200;
    private static final int OPENUI_MAX_LABEL = 256;
    private static final int OPENUI_MAX_CARDS = 12;
    private static final int OPENUI_MAX_COLUMNS = 8;
    private static final int OPENUI_MAX_ROWS = 60;
    private static final int OPENUI_MAX_SERIES = 6;
    private static final int OPENUI_MAX_LABELS = 90;
    private static final int OPENUI_MAX_MEDIA_URL = 2048;
    private static final long OPENUI_MAX_NUMBER_ABS = 1_000_000_000_000_000L;
    private static final Set<String> OPENUI_ALLOWED_TYPES = Set.of(
            "Text", "Markdown", "Notice", "MetricGrid", "MetricCard", "DataTable",
            "LineChart", "BarChart", "PieChart", "DeviceStatusCard",
            "MaintenanceTaskCard", "ImageResult", "VideoResult");
    private static final Map<String, Set<String>> OPENUI_FIELDS = Map.ofEntries(
            Map.entry("Text", Set.of("text")),
            Map.entry("Markdown", Set.of("text")),
            Map.entry("Notice", Set.of("tone", "text")),
            Map.entry("MetricGrid", Set.of("title", "columns", "cards")),
            Map.entry("MetricCard", Set.of("label", "value", "unit", "tone")),
            Map.entry("DataTable", Set.of("title", "columns", "rows")),
            Map.entry("LineChart", Set.of("title", "labels", "series", "x_label", "y_label")),
            Map.entry("BarChart", Set.of("title", "labels", "series", "x_label", "y_label")),
            Map.entry("PieChart", Set.of("title", "series")),
            Map.entry("DeviceStatusCard", Set.of(
                    "inner_code", "name", "region", "status", "updated_at")),
            Map.entry("MaintenanceTaskCard", Set.of(
                    "task_code", "device_name", "type", "priority", "status", "notes")),
            Map.entry("ImageResult", Set.of("src", "alt")),
            Map.entry("VideoResult", Set.of("src", "poster", "alt")));
    private static final Map<String, Integer> OPENUI_TEXT_FIELDS = Map.ofEntries(
            Map.entry("text", OPENUI_MAX_TEXT),
            Map.entry("title", OPENUI_MAX_TITLE),
            Map.entry("label", OPENUI_MAX_LABEL),
            Map.entry("value", OPENUI_MAX_LABEL),
            Map.entry("unit", OPENUI_MAX_LABEL),
            Map.entry("name", OPENUI_MAX_LABEL),
            Map.entry("status", OPENUI_MAX_LABEL),
            Map.entry("notes", OPENUI_MAX_LABEL),
            Map.entry("task_code", OPENUI_MAX_LABEL),
            Map.entry("device_name", OPENUI_MAX_LABEL),
            Map.entry("type", OPENUI_MAX_LABEL),
            Map.entry("priority", OPENUI_MAX_LABEL),
            Map.entry("inner_code", OPENUI_MAX_LABEL),
            Map.entry("region", OPENUI_MAX_LABEL),
            Map.entry("updated_at", OPENUI_MAX_LABEL),
            Map.entry("x_label", OPENUI_MAX_LABEL),
            Map.entry("y_label", OPENUI_MAX_LABEL),
            Map.entry("alt", OPENUI_MAX_LABEL));

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
    /** Agent 连接层熔断器：连续连接故障快速失败，避免每个请求等待超时并加剧雪崩。 */
    private final AgentCircuitBreaker circuitBreaker;
    /** 提供数据库安全配置（含 Tavily Search API Key）；可选依赖，测试构造可缺省。 */
    @Autowired(required = false)
    private SystemSecurityConfigService securityConfigService;

    /**
     * 当前是否处于熔断快速失败窗口。
     *
     * @return 熔断打开返回 true
     */
    public boolean isCircuitOpen() {
        return circuitBreaker.isOpen();
    }

    /** 判断异常链中是否包含连接层故障（连接失败/读取超时/网络中断）。 */
    private static boolean isConnectivityFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.io.IOException) {
                return true;
            }
        }
        return false;
    }

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
        this.circuitBreaker = new AgentCircuitBreaker(
                positiveOrDefault(config.getCircuitFailureThreshold(), 5),
                positiveOrDefault(config.getCircuitOpenTimeoutMs(), 30_000L));
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

    /** 配置值为空或非正数时使用安全默认值（毫秒级 long）。 */
    private static long positiveOrDefault(Long value, long fallback) {
        return value == null || value.longValue() <= 0L ? fallback : value.longValue();
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
        return chat(message, sessionId, userContext, List.of());
    }

    /** 使用可信 Java 登录上下文和服务端解析的附件同步调用 Agent。 */
    public String chat(
            String message,
            String sessionId,
            AgentUserContext userContext,
            List<AiChatAttachmentAgentDTO> attachments) {
        return chat(
                message,
                sessionId,
                userContext.getUserId(),
                "chat",
                null,
                userContext,
                attachments);
    }

    /** 调用隔离的视觉模型端点提取私有 OSS 图片文字。 */
    public AiImageOcrResultDTO recognizeImageText(AiImageOcrRequestDTO request) {
        if (request == null || request.getImageUrl() == null
                || request.getImageUrl().isBlank()) {
            throw new IllegalArgumentException("image OCR request is required");
        }
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("name", request.getName());
            root.put("mime_type", request.getMimeType());
            root.put("image_url", request.getImageUrl());
            putLlmConfig(root);
            JsonNode response = requestJson(
                    "POST", config.getImageOcrUrl(), objectMapper.writeValueAsString(root));
            requireSuccess(response, "IMAGE_OCR_FAILED", "图片文字识别失败");
            JsonNode data = response.path("data");
            AiImageOcrResultDTO result = new AiImageOcrResultDTO();
            if (data.path("text").isTextual()) {
                result.setText(data.path("text").asText());
            }
            result.setTruncated(data.path("truncated").asBoolean(false));
            return result;
        } catch (Exception exception) {
            log.warn("调用图片 OCR 失败，errorType={}",
                    exception.getClass().getSimpleName());
            throw new RuntimeException("图片文字识别失败", exception);
        }
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
        return chat(message, sessionId, userId, mode, contextData, userContext, List.of());
    }

    private String chat(
            String message,
            String sessionId,
            String userId,
            String mode,
            Object contextData,
            AgentUserContext userContext,
            List<AiChatAttachmentAgentDTO> attachments) {
        AgentToolAccess toolAccess = createToolAccess(userContext, sessionId);
        long startedAt = System.currentTimeMillis();
        try {
            if (!circuitBreaker.tryAcquire()) {
                log.warn("Agent 熔断打开，同步对话快速失败，sessionIdLength={}",
                        safeLength(sessionId));
                throw new RuntimeException(
                        "CODE:AGENT_CIRCUIT_OPEN: AI 服务暂不可用，请稍后重试");
            }
            String requestBody = buildRequest(
                    message, sessionId, userId, mode, contextData,
                    userContext, toolAccess, attachments);
            JsonNode root = requestJson("POST", config.getChatInvokeUrl(), requestBody);
            requireSuccess(root, "AGENT_CHAT_FAILED", "Agent 对话请求失败");
            String reply = extractResponse(root);
            log.info("Agent 同步调用完成，mode={}，sessionIdLength={}，耗时={}ms，agentRequestId={}",
                    mode, safeLength(sessionId),
                    System.currentTimeMillis() - startedAt,
                    requestIdOf(toolAccess));
            return reply;
        } catch (Exception e) {
            log.error("调用 Agent 服务失败，errorType={}，耗时={}ms，agentRequestId={}",
                    e.getClass().getSimpleName(),
                    System.currentTimeMillis() - startedAt,
                    requestIdOf(toolAccess));
            // 错误码化异常（含 CODE:xxx 的熔断快速失败等）保持原样，便于上层提取稳定错误码。
            if (e instanceof RuntimeException && e.getMessage() != null
                    && e.getMessage().contains("CODE:")) {
                throw (RuntimeException) e;
            }
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
                message, sessionId, userContext, List.of(), completedReplyConsumer);
    }

    /** 使用可信 Java 登录上下文和附件进行普通流式聊天。 */
    public SseEmitter streamChat(
            String message,
            String sessionId,
            AgentUserContext userContext,
            List<AiChatAttachmentAgentDTO> attachments,
            Consumer<String> completedReplyConsumer) {
        return streamChat(
                message,
                sessionId,
                userContext.getUserId(),
                "chat",
                null,
                userContext,
                attachments,
                completedReplyConsumer,
                false);
    }

    /** 普通流式聊天并报告成功/失败/取消终态，供消息生命周期落库。 */
    public SseEmitter streamChat(
            String message,
            String sessionId,
            AgentUserContext userContext,
            Consumer<String> completedReplyConsumer,
            StreamOutcomeListener outcomeListener) {
        return streamChat(
                message,
                sessionId,
                userContext.getUserId(),
                "chat",
                null,
                userContext,
                List.of(),
                completedReplyConsumer,
                false,
                outcomeListener);
    }

    /** 普通流式聊天（含附件）并报告终态。 */
    public SseEmitter streamChat(
            String message,
            String sessionId,
            AgentUserContext userContext,
            List<AiChatAttachmentAgentDTO> attachments,
            Consumer<String> completedReplyConsumer,
            StreamOutcomeListener outcomeListener) {
        return streamChat(
                message,
                sessionId,
                userContext.getUserId(),
                "chat",
                null,
                userContext,
                attachments,
                completedReplyConsumer,
                false,
                outcomeListener);
    }

    /** 使用可信 Java 登录上下文并保留白名单化结构事件的 V2 流式调用。 */
    public SseEmitter streamChatV2(
            String message,
            String sessionId,
            AgentUserContext userContext,
            Consumer<String> completedReplyConsumer) {
        return streamChatV2(
                message, sessionId, userContext, List.of(), completedReplyConsumer);
    }

    /** 使用可信 Java 登录上下文和附件返回结构化 V2 流。 */
    public SseEmitter streamChatV2(
            String message,
            String sessionId,
            AgentUserContext userContext,
            List<AiChatAttachmentAgentDTO> attachments,
            Consumer<String> completedReplyConsumer) {
        return streamChat(
                message,
                sessionId,
                userContext.getUserId(),
                "chat",
                null,
                userContext,
                attachments,
                completedReplyConsumer,
                true);
    }

    /** 结构化 V2 流并报告成功/失败/取消终态，供消息生命周期落库。 */
    public SseEmitter streamChatV2(
            String message,
            String sessionId,
            AgentUserContext userContext,
            Consumer<String> completedReplyConsumer,
            StreamOutcomeListener outcomeListener) {
        return streamChatV2(
                message, sessionId, userContext, List.of(),
                completedReplyConsumer, outcomeListener);
    }

    /** 结构化 V2 流（含附件）并报告成功/失败/取消终态，供消息生命周期落库。 */
    public SseEmitter streamChatV2(
            String message,
            String sessionId,
            AgentUserContext userContext,
            List<AiChatAttachmentAgentDTO> attachments,
            Consumer<String> completedReplyConsumer,
            StreamOutcomeListener outcomeListener) {
        return streamChat(
                message,
                sessionId,
                userContext.getUserId(),
                "chat",
                null,
                userContext,
                attachments,
                completedReplyConsumer,
                true,
                outcomeListener);
    }

    /** 数据分析 V2 流：携带业务标签，Python 端据此启用 OpenUI 表现层。 */
    public SseEmitter streamAnalyzeV2(
            String message,
            String sessionId,
            AgentUserContext userContext,
            Consumer<String> completedReplyConsumer,
            StreamOutcomeListener outcomeListener) {
        return streamChat(
                message,
                sessionId,
                userContext.getUserId(),
                "chat",
                null,
                userContext,
                List.of(),
                completedReplyConsumer,
                true,
                outcomeListener,
                "data_analysis");
    }

    /** 使用当前登录态的新令牌恢复一个已经由 Java 记录决定的受控动作。 */
    public SseEmitter streamResumeAction(
            String sessionId,
            AgentUserContext userContext,
            String actionId,
            String decision,
            Consumer<String> completedReplyConsumer) {
        return streamResumeAction(
                sessionId, userContext, actionId, decision,
                completedReplyConsumer, null);
    }

    /** 受控动作恢复流并报告终态，供消息生命周期落库。 */
    public SseEmitter streamResumeAction(
            String sessionId,
            AgentUserContext userContext,
            String actionId,
            String decision,
            Consumer<String> completedReplyConsumer,
            StreamOutcomeListener outcomeListener) {
        return streamAgent(
                "",
                sessionId,
                userContext.getUserId(),
                "chat",
                null,
                userContext,
                List.of(),
                completedReplyConsumer,
                true,
                actionId,
                decision,
                outcomeListener);
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

    /** 流式分析结构化页面快照并报告终态，供消息生命周期落库。 */
    public SseEmitter streamChatWithContext(
            String message,
            Object contextData,
            String sessionId,
            AgentUserContext userContext,
            Consumer<String> completedReplyConsumer,
            StreamOutcomeListener outcomeListener) {
        return streamChat(
                message,
                sessionId,
                userContext.getUserId(),
                "context_analysis",
                contextData,
                userContext,
                List.of(),
                completedReplyConsumer,
                false,
                outcomeListener);
    }

    /**
     * 使用服务端组装的作品上下文调用小说创作智能体。
     * <p>作品上下文从作品库加载，模型密钥与 Tavily 密钥服务端注入；
     * 返回白名单化的结构化事件流，与 V2 聊天共用同一事件协议。</p>
     */
    public SseEmitter streamNovelWrite(
            String message,
            String sessionId,
            String userId,
            Object workContext,
            Consumer<String> completedReplyConsumer) {
        return streamNovel(message, sessionId, userId, workContext, completedReplyConsumer);
    }

    /**
     * 根据书名与题材调用 Python 生成一段故事梗概。
     * <p>直接使用白名单 LLM，不进入 Agent 图、不产生会话记忆与历史记录；
     * 供新建作品表单在填好书名后一键自动拟写梗概。</p>
     *
     * @param workName 书名（必填）
     * @param workType 作品类型：short/novel
     * @param genre    题材，可空
     * @return 生成的梗概文本
     */
    public String generateNovelSynopsis(String workName, String workType, String genre) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("work_name", workName == null ? "" : workName.trim());
            root.put("work_type", workType == null ? "novel" : workType.trim());
            if (genre != null && !genre.trim().isEmpty()) {
                root.put("genre", genre.trim());
            }
            putLlmConfig(root);

            JsonNode response = requestJson(
                    "POST",
                    config.getNovelSynopsisUrl(),
                    objectMapper.writeValueAsString(root));
            requireSuccess(response, "AGENT_SYNOPSIS_FAILED", "AI 拟写梗概失败");
            JsonNode data = response.get("data");
            if (data == null || data.get("synopsis") == null
                    || !data.get("synopsis").isTextual()
                    || data.get("synopsis").asText().trim().isEmpty()) {
                throw new RuntimeException("AI 返回了空的梗概");
            }
            return data.get("synopsis").asText().trim();
        } catch (Exception e) {
            log.error("AI 拟写梗概失败，errorType={}", e.getClass().getSimpleName());
            throw new RuntimeException("AI 拟写梗概失败", e);
        }
    }

    /**
     * 调用 Python 生成小说三层大纲（全书 → 卷 → 章）并执行断链检查。
     * <p>直连白名单 LLM，不进入 Agent 图；请求携带作品上下文、现有章节列表
     * 与现有大纲树，响应包含完整大纲树与断链报告。</p>
     *
     * @param workContext 作品上下文（设定卡/梗概/伏笔等，由 workService 组装）
     * @param chapters    现有章节列表（chapterNo/title/brief）
     * @param outlineTree 现有大纲树（可为 null）
     * @return 生成结果 data 节点（tree + gaps）
     */
    public JsonNode generateNovelOutline(
            Object workContext, List<?> chapters, Object outlineTree) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.set("work_context", objectMapper.valueToTree(workContext));
            root.set("chapters", objectMapper.valueToTree(chapters));
            if (outlineTree != null) {
                root.set("outline_tree", objectMapper.valueToTree(outlineTree));
            }
            putLlmConfig(root);

            JsonNode response = requestJson(
                    "POST",
                    config.getNovelOutlineUrl(),
                    objectMapper.writeValueAsString(root));
            requireSuccess(response, "AGENT_OUTLINE_FAILED", "AI 生成大纲失败");
            JsonNode data = response.get("data");
            if (data == null || !data.has("tree")) {
                throw new RuntimeException("AI 返回了空的大纲");
            }
            return data;
        } catch (Exception e) {
            log.error("AI 生成大纲失败，errorType={}", e.getClass().getSimpleName());
            throw new RuntimeException("AI 生成大纲失败", e);
        }
    }

    /**
     * 调用 Python 分析小说章节节奏。
     * <p>直连白名单 LLM，不进入 Agent 图；请求携带作品节奏档位与章节正文，
     * 响应包含节奏评分、实际档位、四维分析与问题建议列表。</p>
     *
     * @param pacingRequest 节奏分析请求（作品名/题材/章节标题/档位/正文）
     * @return 分析结果 data 节点（score/level/dimensions/issues/suggestions）
     */
    public JsonNode analyzeNovelPacing(Object pacingRequest) {
        try {
            ObjectNode root = (ObjectNode) objectMapper.valueToTree(pacingRequest);
            putLlmConfig(root);

            JsonNode response = requestJson(
                    "POST",
                    config.getNovelPacingUrl(),
                    objectMapper.writeValueAsString(root));
            requireSuccess(response, "AGENT_PACING_FAILED", "AI 分析章节节奏失败");
            JsonNode data = response.get("data");
            if (data == null || data.get("score") == null || !data.get("score").isNumber()) {
                throw new RuntimeException("AI 返回了空的节奏分析结果");
            }
            return data;
        } catch (Exception e) {
            log.error("AI 分析章节节奏失败，errorType={}", e.getClass().getSimpleName());
            throw new RuntimeException("AI 分析章节节奏失败", e);
        }
    }

    /**
     * 根据书名流式拟写故事梗概，将 Python 侧的 token 事件逐字转发给浏览器。
     * <p>与 {@link #generateNovelSynopsis} 同构，但不做聚合等待，
     * 前端可在梗概文本框中实时看到生成过程。</p>
     *
     * @param workName 书名（必填）
     * @param workType 作品类型：short/novel
     * @param genre    题材，可空
     * @return 转发 Python SSE 事件的 SseEmitter
     */
    public SseEmitter streamNovelSynopsis(String workName, String workType, String genre) {
        long streamTimeout = config.getStreamTimeout() == null
                || config.getStreamTimeout().longValue() <= 0L
                        ? 310_000L : config.getStreamTimeout().longValue();
        SseEmitter emitter = new SseEmitter(streamTimeout);
        AtomicReference<HttpURLConnection> connectionRef = new AtomicReference<>();
        AtomicReference<Future<?>> futureRef = new AtomicReference<>();

        Runnable streamTask = () -> {
            HttpURLConnection conn = null;
            try {
                if (!circuitBreaker.tryAcquire()) {
                    log.warn("Agent 熔断打开，梗概流快速失败，workNameLength={}",
                            safeLength(workName));
                    sendSafeStreamError(emitter, true, "AI 服务暂不可用，请稍后重试");
                    return;
                }
                ObjectNode root = objectMapper.createObjectNode();
                root.put("work_name", workName == null ? "" : workName.trim());
                root.put("work_type", workType == null ? "novel" : workType.trim());
                if (genre != null && !genre.trim().isEmpty()) {
                    root.put("genre", genre.trim());
                }
                putLlmConfig(root);

                URL url = new URL(config.getBaseUrl() + config.getNovelSynopsisStreamUrl());
                conn = (HttpURLConnection) url.openConnection();
                connectionRef.set(conn);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "text/event-stream");
                applyServiceAuth(conn);
                conn.setConnectTimeout(config.getConnectTimeout());
                conn.setReadTimeout(config.getReadTimeout());
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(objectMapper.writeValueAsBytes(root));
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
                    if (statusCode >= 500) {
                        circuitBreaker.recordFailure();
                        log.warn("Agent 流式服务端故障，status={}，熔断状态={}",
                                statusCode, circuitBreaker.describe());
                    }
                    throw remoteFailure(error, "AGENT_STREAM_HTTP_ERROR", "Agent 流式请求失败");
                }

                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    boolean streamFailed = false;
                    while ((line = readBoundedLine(br, MAX_STREAM_EVENT_CHARS)) != null) {
                        line = line.trim();
                        if (!line.startsWith("data:")) {
                            continue;
                        }
                        String data = line.substring(5).trim();
                        if (data.isEmpty()) {
                            continue;
                        }
                        try {
                            JsonNode node = objectMapper.readTree(data);
                            String eventType = node.path("type").asText("");
                            String content = node.path("content").asText("");
                            if ("error".equals(eventType)) {
                                streamFailed = true;
                                emitter.send(SseEmitter.event().data(data));
                                break;
                            }
                            if ("token".equals(eventType) && !content.isEmpty()) {
                                emitter.send(SseEmitter.event().data(data));
                            } else if ("done".equals(eventType)) {
                                emitter.send(SseEmitter.event().data(data));
                                break;
                            }
                        } catch (IOException parseError) {
                            streamFailed = true;
                            log.warn("解析梗概流式事件失败，errorType={}",
                                    parseError.getClass().getSimpleName());
                            emitter.send(SseEmitter.event().data(
                                    "{\"type\":\"error\",\"content\":\"AI 拟写梗概失败，请稍后重试\"}"));
                            break;
                        }
                    }
                    if (!streamFailed) {
                        circuitBreaker.recordSuccess();
                    }
                }
                emitter.complete();
            } catch (Exception e) {
                log.error("AI 拟写梗概流式调用失败，errorType={}",
                        e.getClass().getSimpleName());
                if (isConnectivityFailure(e)) {
                    circuitBreaker.recordFailure();
                    log.warn("Agent 流式连接层故障，熔断状态={}",
                            circuitBreaker.describe());
                }
                try {
                    emitter.send(SseEmitter.event().data(
                            "{\"type\":\"error\",\"content\":\"AI 拟写梗概失败，请稍后重试\"}"));
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
            completeWithSafeError(emitter, "AI 拟写梗概超时，请稍后重试", true);
        });
        emitter.onError(error -> cancelStream(connectionRef, futureRef));

        try {
            futureRef.set(executorService.submit(streamTask));
        } catch (RejectedExecutionException rejected) {
            log.warn("梗概流式请求被限流，线程池与队列均已满");
            completeWithSafeError(emitter, "AI 服务繁忙，请稍后重试", true);
        }

        return emitter;
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
                List.of(),
                completedReplyConsumer,
                false);
    }

    private SseEmitter streamChat(
            String message,
            String sessionId,
            String userId,
            String mode,
            Object contextData,
            AgentUserContext userContext,
            List<AiChatAttachmentAgentDTO> attachments,
            Consumer<String> completedReplyConsumer) {
        return streamChat(
                message, sessionId, userId, mode, contextData, userContext,
                attachments, completedReplyConsumer, false);
    }

    /** V1 聚合文本与 V2 结构事件共用同一条受控上游读取链路。 */
    private SseEmitter streamChat(
            String message,
            String sessionId,
            String userId,
            String mode,
            Object contextData,
            AgentUserContext userContext,
            List<AiChatAttachmentAgentDTO> attachments,
            Consumer<String> completedReplyConsumer,
            boolean structuredEvents) {
        return streamChat(
                message,
                sessionId,
                userId,
                mode,
                contextData,
                userContext,
                attachments,
                completedReplyConsumer,
                structuredEvents,
                null);
    }

    /** 支持报告终态的回调变体。 */
    private SseEmitter streamChat(
            String message,
            String sessionId,
            String userId,
            String mode,
            Object contextData,
            AgentUserContext userContext,
            List<AiChatAttachmentAgentDTO> attachments,
            Consumer<String> completedReplyConsumer,
            boolean structuredEvents,
            StreamOutcomeListener outcomeListener) {
        return streamAgent(
                message,
                sessionId,
                userId,
                mode,
                contextData,
                userContext,
                attachments,
                completedReplyConsumer,
                structuredEvents,
                null,
                null,
                outcomeListener,
                null);
    }

    /** 带业务标签的结构化 V2 流，用于数据分析模式的 OpenUI 试点。 */
    private SseEmitter streamChat(
            String message,
            String sessionId,
            String userId,
            String mode,
            Object contextData,
            AgentUserContext userContext,
            List<AiChatAttachmentAgentDTO> attachments,
            Consumer<String> completedReplyConsumer,
            boolean structuredEvents,
            StreamOutcomeListener outcomeListener,
            String businessTag) {
        return streamAgent(
                message,
                sessionId,
                userId,
                mode,
                contextData,
                userContext,
                attachments,
                completedReplyConsumer,
                structuredEvents,
                null,
                null,
                outcomeListener,
                businessTag);
    }

    /** 普通聊天和动作恢复共用同一条有界、可取消的 SSE 转发实现。 */
    private SseEmitter streamAgent(
            String message,
            String sessionId,
            String userId,
            String mode,
            Object contextData,
            AgentUserContext userContext,
            List<AiChatAttachmentAgentDTO> attachments,
            Consumer<String> completedReplyConsumer,
            boolean structuredEvents,
            String actionId,
            String decision,
            StreamOutcomeListener outcomeListener) {
        return streamAgent(
                message, sessionId, userId, mode, contextData, userContext,
                attachments, completedReplyConsumer, structuredEvents,
                actionId, decision, outcomeListener, null);
    }

    /** 普通聊天和动作恢复共用同一条有界、可取消的 SSE 转发实现。 */
    private SseEmitter streamAgent(
            String message,
            String sessionId,
            String userId,
            String mode,
            Object contextData,
            AgentUserContext userContext,
            List<AiChatAttachmentAgentDTO> attachments,
            Consumer<String> completedReplyConsumer,
            boolean structuredEvents,
            String actionId,
            String decision,
            StreamOutcomeListener outcomeListener,
            String businessTag) {
        long streamTimeout = config.getStreamTimeout() == null
                || config.getStreamTimeout().longValue() <= 0L
                        ? 310_000L : config.getStreamTimeout().longValue();
        SseEmitter emitter = new SseEmitter(streamTimeout);
        AtomicBoolean replyDelivered = new AtomicBoolean(false);
        AtomicReference<HttpURLConnection> connectionRef = new AtomicReference<>();
        AtomicReference<Future<?>> futureRef = new AtomicReference<>();
        AtomicBoolean outcomeReported = new AtomicBoolean(false);
        AtomicReference<String> failureCode = new AtomicReference<>();
        AgentToolAccess toolAccess = createToolAccess(userContext, sessionId);

        Runnable streamTask = () -> {
            List<ObjectNode> collectedUi = new ArrayList<>();
            HttpURLConnection conn = null;
            long startedAt = System.currentTimeMillis();
            try {
                if (!circuitBreaker.tryAcquire()) {
                    log.warn("Agent 熔断打开，流式请求快速失败，sessionIdLength={}",
                            safeLength(sessionId));
                    reportFailed(
                            outcomeListener, outcomeReported, "AGENT_CIRCUIT_OPEN");
                    sendSafeStreamError(emitter, structuredEvents,
                            "AI 服务暂不可用，请稍后重试");
                    return;
                }
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
                                userContext, toolAccess, attachments, businessTag)
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
                    if (statusCode >= 500) {
                        circuitBreaker.recordFailure();
                        log.warn("Agent 流式服务端故障，status={}，熔断状态={}",
                                statusCode, circuitBreaker.describe());
                    }
                    reportFailed(
                            outcomeListener, outcomeReported, "AGENT_STREAM_HTTP_ERROR");
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
                                    failureCode.compareAndSet(null, "AGENT_STREAM_OVER_LIMIT");
                                    log.warn("Agent 流式回复超过大小限制");
                                    sendSafeStreamError(emitter, structuredEvents,
                                            "Agent 回复过长，请缩小问题范围");
                                    break;
                                }
                                fullReply.append(content);
                                if (structuredEvents) {
                                    sendStructuredEvent(emitter, eventType, node, collectedUi);
                                } else {
                                    emitter.send(SseEmitter.event().data(content));
                                }
                            } else if ("done".equals(eventType) && !content.isEmpty()) {
                                if (fullReply.length() == 0) {
                                    if (content.length() > MAX_STREAM_REPLY_CHARS) {
                                        streamFailed = true;
                                        failureCode.compareAndSet(null, "AGENT_STREAM_OVER_LIMIT");
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
                                    sendStructuredEvent(emitter, eventType, node, collectedUi);
                                }
                            } else if ("done".equals(eventType)) {
                                if (structuredEvents) {
                                    sendStructuredEvent(emitter, eventType, node, collectedUi);
                                }
                            } else if ("error".equals(eventType)) {
                                streamFailed = true;
                                failureCode.compareAndSet(null, "AGENT_STREAM_ERROR");
                                log.warn("Agent 流式响应返回错误事件");
                                sendSafeStreamError(emitter, structuredEvents,
                                        "Agent 流式请求失败，请稍后重试");
                                break;
                            } else if (structuredEvents && isStructuredEvent(eventType)) {
                                if ("approval_required".equals(eventType)) {
                                    approvalPending = true;
                                }
                                sendStructuredEvent(emitter, eventType, node, collectedUi);
                            }
                        } catch (IOException parseError) {
                            streamFailed = true;
                            failureCode.compareAndSet(null, "AGENT_STREAM_INVALID_EVENT");
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
                    failureCode.compareAndSet(null, "AGENT_STREAM_INCOMPLETE");
                    log.warn("Agent 流式响应缺少终止标记");
                    sendSafeStreamError(emitter, structuredEvents,
                            "Agent 流式响应不完整，请稍后重试");
                }

                if (!streamFailed
                        && terminalReceived
                        && !approvalPending
                        && fullReply.length() > 0
                        && replyDelivered.compareAndSet(false, true)
                        && completedReplyConsumer != null) {
                    completedReplyConsumer.accept(fullReply.toString());
                }
                if (!streamFailed && terminalReceived && !approvalPending) {
                    circuitBreaker.recordSuccess();
                    if (outcomeListener != null
                            && outcomeReported.compareAndSet(false, true)) {
                        outcomeListener.onUiArtifacts(serializeUiArtifacts(collectedUi));
                        outcomeListener.onReply(fullReply.toString());
                    }
                }
                if (streamFailed) {
                    reportFailed(outcomeListener, outcomeReported, failureCode.get());
                }
                emitter.complete();
            } catch (Exception e) {
                log.error("Agent 流式调用失败，errorType={}",
                        e.getClass().getSimpleName());
                if (isConnectivityFailure(e)) {
                    circuitBreaker.recordFailure();
                    log.warn("Agent 流式连接层故障，熔断状态={}",
                            circuitBreaker.describe());
                }
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
                log.info("Agent 流式调用结束，mode={}，sessionIdLength={}，耗时={}ms，agentRequestId={}",
                        mode, safeLength(sessionId),
                        System.currentTimeMillis() - startedAt,
                        toolAccess.getAgentRequestId());
                toolTokenService.revoke(toolAccess);
            }
        };

        emitter.onCompletion(() -> {
            toolTokenService.revoke(toolAccess);
            cancelStream(connectionRef, futureRef);
            if (outcomeListener != null && outcomeReported.compareAndSet(false, true)) {
                outcomeListener.onCancelled();
            }
        });
        emitter.onTimeout(() -> {
            toolTokenService.revoke(toolAccess);
            cancelStream(connectionRef, futureRef);
            reportFailed(outcomeListener, outcomeReported, "AGENT_STREAM_TIMEOUT");
            completeWithSafeError(emitter, "Agent 流式请求超时", structuredEvents);
        });
        emitter.onError(error -> {
            toolTokenService.revoke(toolAccess);
            cancelStream(connectionRef, futureRef);
            if (outcomeListener != null && outcomeReported.compareAndSet(false, true)) {
                outcomeListener.onCancelled();
            }
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
     * 建立到 Python 小说创作智能体的 SSE 连接，并把结构化事件转发给浏览器。
     * <p>完成、超时和客户端断开都会取消后台任务及 HTTP 连接；仅在收到完整终止标记后
     * 才把聚合后的回答交给持久化回调。此链路不使用 Java 业务工具令牌。</p>
     */
    private SseEmitter streamNovel(
            String message,
            String sessionId,
            String userId,
            Object workContext,
            Consumer<String> completedReplyConsumer) {
        long streamTimeout = config.getStreamTimeout() == null
                || config.getStreamTimeout().longValue() <= 0L
                        ? 310_000L : config.getStreamTimeout().longValue();
        SseEmitter emitter = new SseEmitter(streamTimeout);
        AtomicBoolean replyDelivered = new AtomicBoolean(false);
        AtomicReference<HttpURLConnection> connectionRef = new AtomicReference<>();
        AtomicReference<Future<?>> futureRef = new AtomicReference<>();

        Runnable streamTask = () -> {
            List<ObjectNode> collectedUi = new ArrayList<>();
            HttpURLConnection conn = null;
            try {
                if (!circuitBreaker.tryAcquire()) {
                    log.warn("Agent 熔断打开，小说创作流快速失败，sessionIdLength={}",
                            safeLength(sessionId));
                    sendSafeStreamError(emitter, true, "AI 服务暂不可用，请稍后重试");
                    return;
                }
                URL url = new URL(config.getBaseUrl() + config.getNovelStreamUrl());
                conn = (HttpURLConnection) url.openConnection();
                connectionRef.set(conn);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "text/event-stream");
                applyServiceAuth(conn);
                conn.setConnectTimeout(config.getConnectTimeout());
                conn.setReadTimeout(config.getReadTimeout());
                conn.setDoOutput(true);

                String requestBody = buildNovelRequest(message, sessionId, userId, workContext);
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
                    if (statusCode >= 500) {
                        circuitBreaker.recordFailure();
                        log.warn("Agent 流式服务端故障，status={}，熔断状态={}",
                                statusCode, circuitBreaker.describe());
                    }
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
                                    sendSafeStreamError(emitter, true,
                                            "Agent 回复过长，请缩小问题范围");
                                    break;
                                }
                                fullReply.append(content);
                                sendStructuredEvent(emitter, eventType, node, collectedUi);
                            } else if ("done".equals(eventType) && !content.isEmpty()) {
                                if (fullReply.length() == 0) {
                                    if (content.length() > MAX_STREAM_REPLY_CHARS) {
                                        streamFailed = true;
                                        log.warn("Agent 流式回复超过大小限制");
                                        sendSafeStreamError(emitter, true,
                                                "Agent 回复过长，请缩小问题范围");
                                        break;
                                    }
                                    fullReply.append(content);
                                }
                                sendStructuredEvent(emitter, eventType, node, collectedUi);
                            } else if ("done".equals(eventType)) {
                                sendStructuredEvent(emitter, eventType, node, collectedUi);
                            } else if ("error".equals(eventType)) {
                                streamFailed = true;
                                log.warn("Agent 流式响应返回错误事件");
                                sendSafeStreamError(emitter, true,
                                        "Agent 流式请求失败，请稍后重试");
                                break;
                            } else if (isStructuredEvent(eventType)) {
                                sendStructuredEvent(emitter, eventType, node, collectedUi);
                            }
                        } catch (IOException parseError) {
                            streamFailed = true;
                            log.warn("解析 Agent 流式事件失败，errorType={}",
                                    parseError.getClass().getSimpleName());
                            sendSafeStreamError(emitter, true,
                                    "Agent 流式响应格式无效，请稍后重试");
                            break;
                        }
                    }
                }

                if (!streamFailed && !terminalReceived) {
                    streamFailed = true;
                    log.warn("Agent 流式响应缺少终止标记");
                    sendSafeStreamError(emitter, true,
                            "Agent 流式响应不完整，请稍后重试");
                }

                if (!streamFailed
                        && terminalReceived
                        && fullReply.length() > 0
                        && completedReplyConsumer != null
                        && replyDelivered.compareAndSet(false, true)) {
                    completedReplyConsumer.accept(fullReply.toString());
                }
                if (!streamFailed && terminalReceived) {
                    circuitBreaker.recordSuccess();
                }
                emitter.complete();
            } catch (Exception e) {
                log.error("Agent 流式调用失败，errorType={}",
                        e.getClass().getSimpleName());
                if (isConnectivityFailure(e)) {
                    circuitBreaker.recordFailure();
                    log.warn("Agent 流式连接层故障，熔断状态={}",
                            circuitBreaker.describe());
                }
                try {
                    sendSafeStreamError(emitter, true, "Agent 流式调用失败，请稍后重试");
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
            completeWithSafeError(emitter, "Agent 流式请求超时", true);
        });
        emitter.onError(error -> cancelStream(connectionRef, futureRef));

        try {
            futureRef.set(executorService.submit(streamTask));
        } catch (RejectedExecutionException rejected) {
            log.warn("Agent 流式请求被限流，线程池与队列均已满");
            completeWithSafeError(emitter, "Agent 流式服务繁忙，请稍后重试", true);
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
                || "ui_start".equals(eventType)
                || "ui_delta".equals(eventType)
                || "ui_complete".equals(eventType)
                || "ui_error".equals(eventType)
                || "citation".equals(eventType)
                || "clarification".equals(eventType)
                || "memory_saved".equals(eventType)
                || "approval_required".equals(eventType)
                || "action_completed".equals(eventType)
                || "action_rejected".equals(eventType)
                || "heartbeat".equals(eventType);
    }

    /** 安全获取字符串长度（null 安全），仅用于日志脱敏统计。 */
    private static int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    /** 仅用于日志的请求标识，工具令牌不可用时返回空串。 */
    private static String requestIdOf(AgentToolAccess toolAccess) {
        return toolAccess == null ? "" : toolAccess.getAgentRequestId();
    }

    /** 报告流式失败终态；同一流只报告一次。 */
    private static void reportFailed(            StreamOutcomeListener listener,
            AtomicBoolean reported,
            String errorCode) {
        if (listener != null && reported.compareAndSet(false, true)) {
            listener.onFailed(errorCode == null || errorCode.isEmpty()
                    ? "AGENT_STREAM_ERROR" : errorCode);
        }
    }

    /** 重建用户可见事件，禁止透传工具原始参数、结果、内部节点和任意扩展字段。 */
    private void sendStructuredEvent(
            SseEmitter emitter, String eventType, JsonNode source,
            List<ObjectNode> uiArtifacts) throws IOException {
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
            copyIdentifier(source, safe, "call_id", 64);
            JsonNode sequence = source.path("sequence");
            if (sequence.isInt() && sequence.asInt() > 0 && sequence.asInt() <= 200) {
                safe.put("sequence", sequence.asInt());
            }
        }

        if (eventType.startsWith("ui_")) {
            if (!forwardOpenUiEvent(emitter, eventType, source, uiArtifacts)) {
                return;
            }
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
            if ("tool_end".equals(eventType)
                    && data.path("elapsed_ms").canConvertToInt()
                    && data.path("elapsed_ms").asInt() >= 0
                    && data.path("elapsed_ms").asInt() <= 3_600_000) {
                safeData.put("elapsed_ms", data.path("elapsed_ms").asInt());
            }
        } else if ("tool_start".equals(eventType)) {
            safe.putObject("data").put("status", "started");
            copyDisplayText(source, safe, "input_summary", 256);
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

    /**
     * 校验并转发 OpenUI 表现层事件。
     * <p>只允许白名单组件、受控字段和受控深度/节点数；超限时改写为
     * {@code ui_error}，让前端自动降级为 Markdown。</p>
     *
     * @return 是否继续发送原始事件；{@code false} 表示已改写为 ui_error
     */
    private boolean forwardOpenUiEvent(
            SseEmitter emitter, String eventType, JsonNode source,
            List<ObjectNode> uiArtifacts) throws IOException {
        ObjectNode safe = objectMapper.createObjectNode();
        safe.put("type", eventType);
        String renderId = source.path("render_id").asText("");
        if (renderId.matches("^[A-Za-z0-9:_-]{1,64}$")) {
            safe.put("render_id", renderId);
        }
        if ("ui_start".equals(eventType) || "ui_complete".equals(eventType)) {
            if (source.path("schema_version").canConvertToInt()
                    && source.path("schema_version").asInt() >= 1
                    && source.path("schema_version").asInt() <= 99) {
                safe.put("schema_version", source.path("schema_version").asInt());
            }
        } else if ("ui_delta".equals(eventType)) {
            if (source.path("sequence").canConvertToInt()
                    && source.path("sequence").asInt() > 0
                    && source.path("sequence").asInt() <= 200) {
                safe.put("sequence", source.path("sequence").asInt());
            }
        } else if ("ui_error".equals(eventType)) {
            copyIdentifier(source, safe, "code", 64);
        }
        if ("ui_delta".equals(eventType) || "ui_complete".equals(eventType)) {
            String payloadField = "ui_delta".equals(eventType) ? "delta" : "spec";
            JsonNode cleaned = sanitizeOpenUiSections(source.path(payloadField));
            if (cleaned == null) {
                return sendOpenUiError(emitter, renderId, "OPENUI_FILTER_REJECTED");
            }
            try {
                if (objectMapper.writeValueAsBytes(cleaned).length > OPENUI_MAX_SPEC_BYTES) {
                    return sendOpenUiError(emitter, renderId, "OPENUI_TOO_LARGE");
                }
            } catch (IOException invalidJson) {
                return sendOpenUiError(emitter, renderId, "OPENUI_FILTER_REJECTED");
            }
            safe.set(payloadField, cleaned);
            if ("ui_complete".equals(eventType)) {
                String fallback = source.path("fallback_markdown").asText("");
                if (!fallback.isEmpty()) {
                    safe.put("fallback_markdown",
                            fallback.substring(0, Math.min(fallback.length(), 200_000)));
                }
                if (uiArtifacts != null) {
                    uiArtifacts.add(safe);
                }
            }
        }
        emitter.send(SseEmitter.event()
                .name(eventType)
                .data(objectMapper.writeValueAsString(safe)));
        return true;
    }

    /** 以 ui_error 替换无效 OpenUI 事件，前端据此保留 Markdown 正文。 */
    private boolean sendOpenUiError(SseEmitter emitter, String renderId, String code)
            throws IOException {
        ObjectNode safe = objectMapper.createObjectNode();
        safe.put("type", "ui_error");
        if (renderId.matches("^[A-Za-z0-9:_-]{1,64}$")) {
            safe.put("render_id", renderId);
        }
        safe.put("code", code);
        emitter.send(SseEmitter.event()
                .name("ui_error")
                .data(objectMapper.writeValueAsString(safe)));
        return false;
    }

    /** 将收集到的 OpenUI 渲染打包为 {@code {"renders":[...]}}；无渲染时返回 null。 */
    private String serializeUiArtifacts(List<ObjectNode> artifacts) {
        if (artifacts == null || artifacts.isEmpty()) {
            return null;
        }
        try {
            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.set("renders", objectMapper.valueToTree(artifacts));
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception serializationError) {
            log.warn("序列化 OpenUI 渲染历史失败，将忽略该次渲染", serializationError);
            return null;
        }
    }

    /** OpenUI Spec 硬性超限信号：整段 Spec 判定失败并降级 Markdown。 */
    private static final class OpenUiLimitException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    /** 校验并清洗分节列表；任何硬性超限都返回 {@code null}。 */
    private JsonNode sanitizeOpenUiSections(JsonNode sections) {
        if (sections == null || !sections.isArray()) {
            return null;
        }
        AtomicInteger nodes = new AtomicInteger();
        try {
            ArrayNode cleaned = objectMapper.createArrayNode();
            for (JsonNode section : sections) {
                JsonNode node = sanitizeOpenUiNode(section, 1, nodes);
                if (node != null) {
                    cleaned.add(node);
                }
            }
            return cleaned;
        } catch (OpenUiLimitException limit) {
            return null;
        }
    }

    private JsonNode sanitizeOpenUiNode(JsonNode node, int depth, AtomicInteger nodes) {
        if (depth > OPENUI_MAX_DEPTH) {
            throw new OpenUiLimitException();
        }
        if (node == null || !node.isObject()) {
            return null;
        }
        String type = node.path("type").asText("");
        if (!OPENUI_ALLOWED_TYPES.contains(type)) {
            return null;
        }
        if (nodes.incrementAndGet() > OPENUI_MAX_NODES) {
            throw new OpenUiLimitException();
        }
        Set<String> allowed = OPENUI_FIELDS.get(type);
        ObjectNode cleaned = objectMapper.createObjectNode();
        cleaned.put("type", type);
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if ("type".equals(key) || !allowed.contains(key)) {
                continue;
            }
            Integer maxChars = OPENUI_TEXT_FIELDS.get(key);
            if (maxChars != null) {
                if (!value.isTextual()) {
                    return null;
                }
                String text = value.asText().trim();
                if (text.isEmpty()) {
                    return null;
                }
                cleaned.put(key, text.substring(0, Math.min(text.length(), maxChars)));
            } else if ("src".equals(key) || "poster".equals(key)) {
                String url = safeMediaUrl(value);
                if (url == null) {
                    return null;
                }
                cleaned.put(key, url);
            } else if ("columns".equals(key)) {
                ArrayNode columns = cleanTextArray(value, OPENUI_MAX_COLUMNS, 128);
                if (columns == null) {
                    return null;
                }
                cleaned.set(key, columns);
            } else if ("labels".equals(key)) {
                ArrayNode labels = cleanTextArray(value, OPENUI_MAX_LABELS, OPENUI_MAX_LABEL);
                if (labels == null) {
                    return null;
                }
                cleaned.set(key, labels);
            } else if ("rows".equals(key)) {
                ArrayNode rows = cleanRows(value);
                if (rows == null) {
                    return null;
                }
                cleaned.set(key, rows);
            } else if ("cards".equals(key)) {
                ArrayNode cards = cleanNodeArray(value, depth, nodes, OPENUI_MAX_CARDS);
                if (cards == null) {
                    return null;
                }
                cleaned.set(key, cards);
            } else if ("series".equals(key)) {
                ArrayNode series = cleanSeriesArray(value, depth, nodes);
                if (series == null) {
                    return null;
                }
                cleaned.set(key, series);
            }
        }
        return cleaned;
    }

    private String safeMediaUrl(JsonNode value) {
        if (!value.isTextual()) {
            return null;
        }
        String url = value.asText();
        if (url.isEmpty()) {
            return null;
        }
        if (url.length() > OPENUI_MAX_MEDIA_URL) {
            url = url.substring(0, OPENUI_MAX_MEDIA_URL);
        }
        if (url.startsWith("https://")) {
            return url;
        }
        String lowered = url.toLowerCase(Locale.ROOT);
        if (lowered.startsWith("http://localhost")
                || lowered.startsWith("http://127.0.0.1")
                || lowered.startsWith("http://[::1]")) {
            return url;
        }
        return null;
    }

    private ArrayNode cleanTextArray(JsonNode value, int maxItems, int maxChars) {
        if (!value.isArray() || value.isEmpty()) {
            return null;
        }
        ArrayNode cleaned = objectMapper.createArrayNode();
        int count = 0;
        for (JsonNode item : value) {
            if (count >= maxItems) {
                break;
            }
            if (!item.isTextual() || item.asText().trim().isEmpty()) {
                return null;
            }
            String text = item.asText().trim();
            cleaned.add(text.substring(0, Math.min(text.length(), maxChars)));
            count++;
        }
        return cleaned;
    }

    private ArrayNode cleanRows(JsonNode value) {
        if (!value.isArray() || value.isEmpty()) {
            return null;
        }
        ArrayNode cleaned = objectMapper.createArrayNode();
        int count = 0;
        for (JsonNode row : value) {
            if (count >= OPENUI_MAX_ROWS) {
                break;
            }
            if (!row.isArray()) {
                return null;
            }
            ArrayNode cells = objectMapper.createArrayNode();
            int cellCount = 0;
            for (JsonNode cell : row) {
                if (cellCount >= OPENUI_MAX_COLUMNS) {
                    break;
                }
                String text = cell.isTextual() ? cell.asText() : String.valueOf(cell);
                cells.add(text.substring(0, Math.min(text.length(), 128)));
                cellCount++;
            }
            cleaned.add(cells);
            count++;
        }
        return cleaned;
    }

    private ArrayNode cleanNodeArray(
            JsonNode value, int depth, AtomicInteger nodes, int maxItems) {
        if (!value.isArray() || value.isEmpty()) {
            return null;
        }
        ArrayNode cleaned = objectMapper.createArrayNode();
        int count = 0;
        for (JsonNode item : value) {
            if (count >= maxItems) {
                break;
            }
            JsonNode node = sanitizeOpenUiNode(item, depth + 1, nodes);
            if (node != null) {
                cleaned.add(node);
                count++;
            }
        }
        return cleaned;
    }

    private ArrayNode cleanSeriesArray(JsonNode value, int depth, AtomicInteger nodes) {
        if (!value.isArray() || value.isEmpty()) {
            return null;
        }
        ArrayNode cleaned = objectMapper.createArrayNode();
        int count = 0;
        for (JsonNode series : value) {
            if (count >= OPENUI_MAX_SERIES) {
                break;
            }
            if (!series.isObject()) {
                return null;
            }
            ObjectNode cleanedSeries = objectMapper.createObjectNode();
            String name = series.path("name").asText("");
            cleanedSeries.put("name", name.substring(0, Math.min(name.length(),
                    OPENUI_MAX_LABEL)));
            JsonNode data = series.path("data");
            if (!data.isArray() || data.isEmpty()) {
                return null;
            }
            boolean pieSlice = data.get(0).isObject();
            ArrayNode dataNodes = objectMapper.createArrayNode();
            int dataCount = 0;
            for (JsonNode item : data) {
                if (dataCount >= OPENUI_MAX_LABELS) {
                    break;
                }
                if (pieSlice) {
                    JsonNode cleanedItem = cleanPieSlice(item);
                    if (cleanedItem == null) {
                        return null;
                    }
                    dataNodes.add(cleanedItem);
                } else {
                    JsonNode cleanedNumber = cleanNumber(item);
                    if (cleanedNumber == null) {
                        return null;
                    }
                    dataNodes.add(cleanedNumber);
                }
                dataCount++;
            }
            cleanedSeries.set("data", dataNodes);
            cleaned.add(cleanedSeries);
            count++;
        }
        return cleaned;
    }

    private JsonNode cleanPieSlice(JsonNode item) {
        if (!item.isObject()) {
            return null;
        }
        String name = item.path("name").asText("");
        JsonNode value = item.path("value");
        JsonNode number = cleanNumber(value);
        if (number == null) {
            return null;
        }
        ObjectNode cleaned = objectMapper.createObjectNode();
        cleaned.put("name", name.substring(0, Math.min(name.length(), OPENUI_MAX_LABEL)));
        cleaned.set("value", number);
        return cleaned;
    }

    private JsonNode cleanNumber(JsonNode value) {
        if (!value.isNumber()) {
            return null;
        }
        if (value.isIntegralNumber()) {
            long number = value.asLong();
            if (number > OPENUI_MAX_NUMBER_ABS || number < -OPENUI_MAX_NUMBER_ABS) {
                return null;
            }
            return objectMapper.getNodeFactory().numberNode(number);
        }
        double number = value.asDouble();
        if (number > OPENUI_MAX_NUMBER_ABS || number < -OPENUI_MAX_NUMBER_ABS) {
            return null;
        }
        return objectMapper.getNodeFactory().numberNode(number);
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

    /** 删除小说作品会话对应的 Python checkpoint 创作记忆。 */
    public void deleteNovelThreadMemory(String sessionId, String userId) {
        try {
            ObjectNode request = objectMapper.createObjectNode();
            request.put("user_id", userId == null ? "" : userId.trim());
            request.put("thread_id", sessionId == null ? "" : sessionId.trim());

            JsonNode response = requestJson(
                    "DELETE",
                    config.getNovelThreadDeleteUrl(),
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

    /** 构造小说创作智能体请求体；作品上下文由服务端组装，模型与搜索密钥服务端注入。 */
    private String buildNovelRequest(
            String message, String sessionId, String userId, Object workContext) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("message", message);
            if (sessionId != null && !sessionId.trim().isEmpty()) {
                root.put("thread_id", sessionId.trim());
            }
            if (userId != null && !userId.trim().isEmpty()) {
                root.put("user_id", userId.trim());
            }
            if (workContext != null) {
                root.set("work_context", objectMapper.valueToTree(workContext));
            }
            root.put("max_iterations", config.getMaxIterations());
            putLlmConfig(root);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("构建请求失败", e);
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
        return buildRequest(
                message, sessionId, userId, mode, contextData,
                userContext, toolAccess, List.of());
    }

    /** 构造包含服务端解析附件的统一对话请求体。 */
    String buildRequest(
            String message,
            String sessionId,
            String userId,
            String mode,
            Object contextData,
            AgentUserContext userContext,
            AgentToolAccess toolAccess,
            List<AiChatAttachmentAgentDTO> attachments) {
        return buildRequest(
                message, sessionId, userId, mode, contextData,
                userContext, toolAccess, attachments, null);
    }

    /** 构造包含服务端解析附件与业务标签的统一对话请求体。 */
    String buildRequest(
            String message,
            String sessionId,
            String userId,
            String mode,
            Object contextData,
            AgentUserContext userContext,
            AgentToolAccess toolAccess,
            List<AiChatAttachmentAgentDTO> attachments,
            String businessTag) {
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
            if (businessTag != null && !businessTag.trim().isEmpty()) {
                root.put("business_tag", businessTag.trim());
            }
            if (attachments != null && !attachments.isEmpty()) {
                ArrayNode attachmentNodes = root.putArray("attachments");
                for (AiChatAttachmentAgentDTO attachment : attachments) {
                    ObjectNode node = attachmentNodes.addObject();
                    node.put("attachment_id", attachment.getAttachmentId());
                    node.put("name", attachment.getName());
                    node.put("mime_type", attachment.getMimeType());
                    node.put("size", attachment.getSize());
                    node.put("kind", attachment.getKind());
                    if (attachment.getImageUrl() != null) {
                        node.put("image_url", attachment.getImageUrl());
                    }
                    if (attachment.getExtractedText() != null) {
                        node.put("extracted_text", attachment.getExtractedText());
                    }
                    node.put("truncated", Boolean.TRUE.equals(attachment.getTruncated()));
                }
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
            if (statusCode >= 500) {
                circuitBreaker.recordFailure();
                log.warn("Agent 服务端故障，method={}, status={}，熔断状态={}",
                        method, statusCode, circuitBreaker.describe());
                return AgentResponseUtil.normalizeError(
                        objectMapper,
                        responseBody,
                        statusCode,
                        "AGENT_HTTP_ERROR",
                        "Python Agent 请求失败");
            }
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
            circuitBreaker.recordSuccess();
            return AgentResponseUtil.parseSuccess(objectMapper, responseBody);
        } catch (IOException connectionFailure) {
            circuitBreaker.recordFailure();
            log.warn("Agent 连接层故障，method={}, errorType={}，熔断状态={}",
                    method, connectionFailure.getClass().getSimpleName(),
                    circuitBreaker.describe());
            throw connectionFailure;
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
        if (securityConfigService != null) {
            String tavilyApiKey = securityConfigService.getRequiredConfig().getSearchTavilyApiKey();
            if (tavilyApiKey != null && !tavilyApiKey.trim().isEmpty()) {
                llmConfig.put("tavily_api_key", tavilyApiKey);
            }
        }
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

    /** 为 Java 到 Python 的每个请求附加服务间认证头，并透传统一 request_id。 */
    private void applyServiceAuth(HttpURLConnection conn) {
        String serviceApiKey = config.getServiceApiKey();
        if (serviceApiKey == null || serviceApiKey.trim().isEmpty()) {
            throw new IllegalStateException("agent.service-api-key is not configured");
        }
        conn.setRequestProperty("X-Agent-Service-Key", serviceApiKey);
        String requestId = com.dkd.framework.web.filter.RequestIdFilter.current();
        if (requestId != null) {
            conn.setRequestProperty("X-Request-Id", requestId);
        }
    }
}
