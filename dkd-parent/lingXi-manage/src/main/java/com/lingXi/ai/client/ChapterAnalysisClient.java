package com.lingXi.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
 * HTTP client for calling Python Agent chapter analysis endpoint.
 * Migrated from Java AiVideoChapterAnalysisWorker core logic to Python.
 */
@Slf4j
@Component
public class ChapterAnalysisClient {

    /** Conservative idle-read budget; streamed progress resets it between scene calls. */
    private static final long CHAPTER_STREAMING_READ_SAFETY_MULTIPLIER = 4L;
    private static final long CHAPTER_TRANSPORT_MARGIN_MS = 60_000L;
    private static final int MAX_STREAM_EVENT_CHARS = 2 * 1024 * 1024;

    private final VideoConfig config;
    private final AgentConfig agentConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static class ChapterClientConfigurationException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        ChapterClientConfigurationException(String message) {
            super(message);
        }
    }

    public ChapterAnalysisClient(VideoConfig config, AgentConfig agentConfig) {
        this.config = config;
        this.agentConfig = agentConfig;
    }

    // ── Inner Classes ───────────────────────────────────────────────────────

    /**
     * Result of chapter analysis.
     */
    public static class AnalysisResult {
        private final boolean success;
        private final JsonNode storyBible;
        private final String rawLlmResponse;
        private final String error;
        private final String errorCode;
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

    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(String stage, int progress, String message);
    }

    // ── API Methods ─────────────────────────────────────────────────────────

    /**
     * Analyze a chapter and produce a structured story bible.
     *
     * @param apiKey            DashScope API key
     * @param model             LLM model name
     * @param baseUrl           LLM base URL
     * @param videoModel        Downstream video model used to normalize shot durations
     * @param chapterTitle      Chapter title
     * @param sourceText        Raw chapter source text
     * @param projectCharacters Existing project characters for identity reuse
     * @return AnalysisResult with validated story bible or error
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

    public AnalysisResult analyzeChapter(
            String apiKey,
            String model,
            String baseUrl,
            String videoModel,
            String chapterTitle,
            String sourceText,
            List<ObjectNode> projectCharacters,
            ProgressListener progressListener) {

        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("chapter_title", chapterTitle);
            body.put("source_text", sourceText);
            body.put("video_model", videoModel);
            if (projectCharacters != null && !projectCharacters.isEmpty()) {
                body.set("project_characters", objectMapper.valueToTree(projectCharacters));
            }

            // Add LLM config
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

    private int requirePositive(Integer value, String propertyName) {
        if (value == null || value <= 0) {
            throw new ChapterClientConfigurationException(
                    propertyName + " must be configured as a positive integer");
        }
        return value;
    }

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

    // ── HTTP Helpers ────────────────────────────────────────────────────────

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

    private void applyServiceAuth(HttpURLConnection conn) {
        String serviceApiKey = agentConfig.getServiceApiKey();
        if (serviceApiKey == null || serviceApiKey.trim().isEmpty()) {
            throw new ChapterClientConfigurationException(
                    "agent.service-api-key is not configured");
        }
        conn.setRequestProperty("X-Agent-Service-Key", serviceApiKey);
    }
}
