package com.lingXi.ai.client;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.lingXi.ai.config.AgentConfig;
import com.lingXi.ai.config.VideoConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 章节分析 HTTP 客户端
 * <p>调用 Python Agent 章节分析端点，已从 Java AiVideoChapterAnalysisWorker 核心逻辑迁移至 Python。</p>
 */
@Slf4j
@Component
public class ChapterAnalysisClient {

    /** 保守的单次空闲读取时限；场景调用之间收到流式进度后会重新计时。 */
    private static final long CHAPTER_STREAMING_READ_SAFETY_MULTIPLIER = 4L;
    /** 在模型处理时长之外预留给网络传输和本地校验的时间。 */
    private static final long CHAPTER_TRANSPORT_MARGIN_MS = 60_000L;
    /** 单条 NDJSON 进度或结果事件允许的最大字符数。 */
    private static final int MAX_STREAM_EVENT_CHARS = 2 * 1024 * 1024;

    /** Python 章节分析端点及超时配置。 */
    private final VideoConfig config;
    /** Java 与 Python 服务间认证配置。 */
    private final AgentConfig agentConfig;
    /** 用于构造章节请求并解析 NDJSON/JSON 响应。 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 章节分析客户端配置异常
     */
    private static class ChapterClientConfigurationException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        ChapterClientConfigurationException(String message) {
            super(message);
        }
    }

    /**
     * 构造章节分析客户端
     *
     * @param config      视频配置
     * @param agentConfig Agent配置
     */
    public ChapterAnalysisClient(VideoConfig config, AgentConfig agentConfig) {
        this.config = config;
        this.agentConfig = agentConfig;
    }

    // ── 内部类 ───────────────────────────────────────────────────────────

    /**
     * 章节分析结果
     */
    public static class AnalysisResult {
        /** 是否成功得到并校验故事圣经。 */
        private final boolean success;
        /** 结构化故事圣经，失败时为空。 */
        private final JsonNode storyBible;
        /** 模型原始响应，主要用于审计和问题排查。 */
        private final String rawLlmResponse;
        /** 对调用方安全的错误说明。 */
        private final String error;
        /** 稳定的机器可读错误码。 */
        private final String errorCode;
        /** 当前失败是否适合由任务调度器重试。 */
        private final boolean retryable;

        public AnalysisResult(boolean success, JsonNode storyBible, String rawLlmResponse,
                String error, String errorCode, boolean retryable) {
            this.success = success;
            this.storyBible = storyBible;
            this.rawLlmResponse = rawLlmResponse;
            this.error = error;
            this.errorCode = errorCode;
            this.retryable = retryable;
        }

        public boolean isSuccess() { return success; }
        public JsonNode getStoryBible() { return storyBible; }
        public String getRawLlmResponse() { return rawLlmResponse; }
        public String getError() { return error; }
        public String getErrorCode() { return errorCode; }
        public boolean isRetryable() { return retryable; }
    }

    /**
     * 章节分析进度监听器
     */
    @FunctionalInterface
    public interface ProgressListener {
        /**
         * 接收章节分析阶段进度。
         *
         * @param stage 当前阶段编码
         * @param progress 百分比进度
         * @param message 面向用户的进度说明
         */
        void onProgress(String stage, int progress, String message);
    }

    // ── API 方法 ─────────────────────────────────────────────────────────

    /**
     * 分析章节并生成结构化的故事圣经
     *
     * @param apiKey            DashScope API 密钥
     * @param model             LLM 模型名称
     * @param baseUrl           LLM 基础地址
     * @param videoModel        下游视频模型，用于标准化镜头时长
     * @param chapterTitle      章节标题
     * @param sourceText        章节原始文本
     * @param projectCharacters 已有的项目角色列表，用于身份复用
     * @return 包含验证后的故事圣经或错误信息的 AnalysisResult
     */
    public AnalysisResult analyzeChapter(
            String apiKey,
            String model,
            String baseUrl,
            String videoModel,
            String chapterTitle,
            String sourceText,
            List<ObjectNode> projectCharacters) {
        return analyzeChapter(apiKey, model, baseUrl, videoModel, chapterTitle,
                sourceText, projectCharacters, null);
    }

    /**
     * 分析章节并生成结构化的故事圣经（带进度回调）
     *
     * @param apiKey            DashScope API 密钥
     * @param model             LLM 模型名称
     * @param baseUrl           LLM 基础地址
     * @param videoModel        下游视频模型，用于标准化镜头时长
     * @param chapterTitle      章节标题
     * @param sourceText        章节原始文本
     * @param projectCharacters 已有的项目角色列表，用于身份复用
     * @param progressListener  进度监听器，用于接收分析进度回调
     * @return 包含验证后的故事圣经或错误信息的 AnalysisResult
     */
    public AnalysisResult analyzeChapter(
            String apiKey,
            String model,
            String baseUrl,
            String videoModel,
            String chapterTitle,
            String sourceText,
            List<ObjectNode> projectCharacters,
            ProgressListener progressListener) {
        return analyzeChapter(apiKey, model, baseUrl, videoModel, chapterTitle,
                sourceText, projectCharacters, 2, progressListener);
    }

    /**
     * 分析章节并生成结构化故事圣经，并限制单章场景生成并发数。
     */
    public AnalysisResult analyzeChapter(
            String apiKey,
            String model,
            String baseUrl,
            String videoModel,
            String chapterTitle,
            String sourceText,
            List<ObjectNode> projectCharacters,
            Integer sceneConcurrency,
            ProgressListener progressListener) {

        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("chapter_title", chapterTitle);
            body.put("source_text", sourceText);
            body.put("video_model", videoModel);
            body.put("scene_concurrency", requireRange(
                    sceneConcurrency, 1, 8, "chapter scene concurrency"));
            if (projectCharacters != null && !projectCharacters.isEmpty()) {
                body.set("project_characters", objectMapper.valueToTree(projectCharacters));
            }

            // 将本次章节分析使用的大模型配置一并传给 Python Agent。
            ObjectNode llmConfig = objectMapper.createObjectNode();
            llmConfig.put("api_key", apiKey);
            llmConfig.put("model", model);
            int providerReadTimeoutSeconds = requirePositive(
                    config.getChapterProviderReadTimeoutSeconds(),
                    "video.chapter-provider-read-timeout-seconds");
            llmConfig.put("timeout_seconds", providerReadTimeoutSeconds);
            if (baseUrl != null && !baseUrl.isEmpty()) {
                llmConfig.put("base_url", baseUrl);
            }
            body.set("llm_config", llmConfig);

            ProgressListener effectiveProgressListener = progressListener == null
                    ? (stage, progress, message) -> { }
                    : progressListener;
            String endpoint = config.getAnalyzeChapterStreamUrl();
            if (endpoint == null || endpoint.trim().isEmpty()) {
                throw new ChapterClientConfigurationException(
                        "video.analyze-chapter-stream-url must be configured");
            }
            String url = config.getBaseUrl() + endpoint;
            int timeout = requirePositive(config.getChapterReadTimeout(), "video.chapter-read-timeout");
            validateChapterTimeoutBudget(timeout, providerReadTimeoutSeconds);

            log.info("Calling Python chapter analysis | textLength={} | "
                            + "providerTimeoutSeconds={} | httpReadTimeoutMs={}",
                    sourceText.length(), providerReadTimeoutSeconds, timeout);

            JsonNode response = doPostStream(
                    url, body.toString(), timeout, effectiveProgressListener);
            return parseAnalysisResponse(response);

        } catch (SocketTimeoutException e) {
            log.error("Python chapter analysis request timed out");
            return new AnalysisResult(
                    false, null, null, "Python 章节分析接口等待超时", "CHAPTER_AGENT_TIMEOUT", true);
        } catch (ChapterClientConfigurationException e) {
            log.error("Invalid chapter analysis client configuration: {}", e.getMessage());
            return new AnalysisResult(
                    false, null, null, e.getMessage(), "CHAPTER_CONFIGURATION_INVALID", false);
        } catch (IOException e) {
            log.error("Python chapter analysis transport failed, errorType={}",
                    e.getClass().getSimpleName());
            return new AnalysisResult(
                    false, null, null, "Python 章节分析接口通信失败",
                    "CHAPTER_AGENT_TRANSPORT_ERROR", true);
        } catch (Exception e) {
            log.error("Failed to analyze chapter, errorType={}",
                    e.getClass().getSimpleName());
            return new AnalysisResult(false, null, null,
                    "Python 章节分析失败", "CHAPTER_ANALYSIS_FAILED", false);
        }
    }

    /**
     * 解析章节分析响应
     *
     * @param response Python Agent 返回的 JSON 响应
     * @return 章节分析结果
     */
    private AnalysisResult parseAnalysisResponse(JsonNode response) {
        boolean success = response.path("success").asBoolean(false);
        if (success) {
            JsonNode storyBible = response.path("story_bible");
            String rawResponse = response.path("raw_llm_response").asText(null);
            return new AnalysisResult(true, storyBible, rawResponse, null, null, false);
        } else {
            String error = response.path("error").asText("Unknown error");
            String errorCode = response.path("error_code").asText(null);
            boolean retryable = response.path("retryable").asBoolean(false);
            return new AnalysisResult(false, null, null, error, errorCode, retryable);
        }
    }

    /**
     * 读取必须为正数的超时配置，避免零值导致立即超时或无限等待。
     */
    private int requirePositive(Integer value, String propertyName) {
        if (value == null || value <= 0) {
            throw new ChapterClientConfigurationException(
                    propertyName + " must be configured as a positive integer");
        }
        return value;
    }

    /** 读取必须落在给定闭区间内的整数配置。 */
    private int requireRange(Integer value, int minimum, int maximum, String propertyName) {
        if (value == null || value < minimum || value > maximum) {
            throw new ChapterClientConfigurationException(
                    propertyName + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    /**
     * 校验 Java 传输层超时足以覆盖 Python 内部的多阶段模型调用。
     * <p>预算包含规划、场景生成、可能的本地修复以及固定网络余量。</p>
     */
    private void validateChapterTimeoutBudget(int chapterReadTimeoutMs, int providerReadTimeoutSeconds) {
        long minimumBudgetMs = providerReadTimeoutSeconds * 1_000L
                * CHAPTER_STREAMING_READ_SAFETY_MULTIPLIER
                + CHAPTER_TRANSPORT_MARGIN_MS;
        if (chapterReadTimeoutMs <= minimumBudgetMs) {
            throw new ChapterClientConfigurationException(
                    "video.chapter-read-timeout must be greater than " + minimumBudgetMs
                            + " ms so streamed planning, scene generation, local repair, and transport margin can finish");
        }
    }

    // ── HTTP 辅助方法 ─────────────────────────────────────────────────────

    /** 执行兼容性的同步章节分析请求，并归一化成功或失败响应。 */
    private JsonNode doPost(String urlStr, String jsonBody, int readTimeout) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            applyServiceAuth(conn);
            conn.setConnectTimeout(requirePositive(config.getConnectTimeout(), "video.connect-timeout"));
            conn.setReadTimeout(readTimeout);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int statusCode = conn.getResponseCode();

            String responseBody = AgentResponseUtil.readResponseBody(conn, statusCode);

            if (statusCode < 200 || statusCode >= 300) {
                log.error("Python Agent chapter HTTP error | status={}", statusCode);
                return AgentResponseUtil.normalizeError(
                        objectMapper,
                        responseBody,
                        statusCode,
                        "CHAPTER_AGENT_HTTP_ERROR",
                        "Python 章节分析接口请求失败");
            }

            JsonNode response = AgentResponseUtil.parseSuccess(objectMapper, responseBody);
            if (!response.path("success").asBoolean(false)) {
                return AgentResponseUtil.normalizeError(
                        objectMapper,
                        responseBody,
                        statusCode,
                        "CHAPTER_ANALYSIS_FAILED",
                        "Python 章节分析接口请求失败");
            }
            return response;

        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 消费 Python 返回的 NDJSON 流：中间 progress 事件触发回调，最终 result 事件返回结果。
     */
    private JsonNode doPostStream(String urlStr, String jsonBody, int readTimeout,
            ProgressListener progressListener) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/x-ndjson");
            applyServiceAuth(conn);
            conn.setConnectTimeout(requirePositive(config.getConnectTimeout(), "video.connect-timeout"));
            conn.setReadTimeout(readTimeout);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            int statusCode = conn.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                String responseBody = AgentResponseUtil.readResponseBody(conn, statusCode);
                return AgentResponseUtil.normalizeError(
                        objectMapper, responseBody, statusCode,
                        "CHAPTER_AGENT_HTTP_ERROR", "Python 章节分析流式接口请求失败");
            }

            JsonNode terminalResponse = null;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                    if (line.length() > MAX_STREAM_EVENT_CHARS) {
                        throw new IOException("Chapter progress event exceeds size limit");
                    }
                    JsonNode event = objectMapper.readTree(line);
                    String type = event.path("type").asText("");
                    if ("progress".equals(type)) {
                        progressListener.onProgress(
                                event.path("stage").asText("RUNNING"),
                                event.path("progress").asInt(10),
                                event.path("message").asText("章节分析进行中"));
                    } else if ("result".equals(type)) {
                        terminalResponse = event.path("response");
                    }
                }
            }
            if (terminalResponse == null || !terminalResponse.isObject()) {
                throw new IOException("Python chapter analysis stream ended without a result");
            }
            return terminalResponse;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** 为章节分析请求附加服务认证头，未配置密钥时拒绝发送。 */
    private void applyServiceAuth(HttpURLConnection conn) {
        String serviceApiKey = agentConfig.getServiceApiKey();
        if (serviceApiKey == null || serviceApiKey.trim().isEmpty()) {
            throw new ChapterClientConfigurationException(
                    "agent.service-api-key is not configured");
        }
        conn.setRequestProperty("X-Agent-Service-Key", serviceApiKey);
    }
}
