package com.lingXi.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lingXi.ai.config.VideoConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * HTTP client for calling Python Agent chapter analysis endpoint.
 * Migrated from Java AiVideoChapterAnalysisWorker core logic to Python.
 */
@Slf4j
@Component
public class ChapterAnalysisClient {

    private static final long CHAPTER_PROVIDER_CALL_BUDGET = 2L;
    private static final long CHAPTER_TRANSPORT_MARGIN_MS = 60_000L;

    private final VideoConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static class ChapterClientConfigurationException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        ChapterClientConfigurationException(String message) {
            super(message);
        }
    }

    public ChapterAnalysisClient(VideoConfig config) {
        this.config = config;
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

    // ── API Methods ─────────────────────────────────────────────────────────

    /**
     * Analyze a chapter and produce a structured story bible.
     *
     * @param apiKey            DashScope API key
     * @param model             LLM model name
     * @param baseUrl           LLM base URL
     * @param videoModel        Downstream Wanx model used to normalize shot durations
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

            String url = config.getBaseUrl() + config.getAnalyzeChapterUrl();
            int timeout = requirePositive(config.getChapterReadTimeout(), "video.chapter-read-timeout");
            validateChapterTimeoutBudget(timeout, providerReadTimeoutSeconds);

            log.info("Calling Python chapter analysis | url={} | textLength={} | "
                            + "providerTimeoutSeconds={} | httpReadTimeoutMs={}",
                    url, sourceText.length(), providerReadTimeoutSeconds, timeout);

            JsonNode response = doPost(url, body.toString(), timeout);
            return parseAnalysisResponse(response);

        } catch (SocketTimeoutException e) {
            log.error("Python chapter analysis request timed out: {}", e.getMessage(), e);
            return new AnalysisResult(
                    false, null, null, "Python 章节分析接口等待超时", "CHAPTER_AGENT_TIMEOUT", true);
        } catch (ChapterClientConfigurationException e) {
            log.error("Invalid chapter analysis client configuration: {}", e.getMessage());
            return new AnalysisResult(
                    false, null, null, e.getMessage(), "CHAPTER_CONFIGURATION_INVALID", false);
        } catch (IOException e) {
            log.error("Python chapter analysis transport failed: {}", e.getMessage(), e);
            String message = e.getMessage() == null
                    ? "Python 章节分析接口通信失败"
                    : "Python 章节分析接口通信失败：" + e.getMessage();
            return new AnalysisResult(
                    false, null, null, message, "CHAPTER_AGENT_TRANSPORT_ERROR", true);
        } catch (Exception e) {
            log.error("Failed to analyze chapter: {}", e.getMessage(), e);
            return new AnalysisResult(false, null, null, e.getMessage(), null, false);
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
        long minimumBudgetMs = providerReadTimeoutSeconds * 1_000L * CHAPTER_PROVIDER_CALL_BUDGET
                + CHAPTER_TRANSPORT_MARGIN_MS;
        if (chapterReadTimeoutMs <= minimumBudgetMs) {
            throw new ChapterClientConfigurationException(
                    "video.chapter-read-timeout must be greater than " + minimumBudgetMs
                            + " ms so the primary provider call, one contract-repair call, and transport margin can finish");
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
            conn.setConnectTimeout(requirePositive(config.getConnectTimeout(), "video.connect-timeout"));
            conn.setReadTimeout(readTimeout);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int statusCode = conn.getResponseCode();

            String responseBody;
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(
                            statusCode >= 200 && statusCode < 300
                                    ? conn.getInputStream()
                                    : conn.getErrorStream(),
                            StandardCharsets.UTF_8))) {
                responseBody = br.lines().collect(Collectors.joining());
            }

            if (statusCode < 200 || statusCode >= 300) {
                log.error("HTTP error | status={} | url={} | body={}", statusCode, urlStr, responseBody);
                ObjectNode errorResponse = structuredHttpError(responseBody);
                errorResponse.put("success", false);
                if (!errorResponse.hasNonNull("error")) {
                    errorResponse.put("error", "HTTP " + statusCode + ": " + responseBody);
                }
                return errorResponse;
            }

            JsonNode response = objectMapper.readTree(responseBody);
            if (response == null) {
                throw new IOException("Python chapter analysis returned an empty response");
            }
            return response;

        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private ObjectNode structuredHttpError(String responseBody) {
        if (responseBody != null && !responseBody.trim().isEmpty()) {
            try {
                JsonNode parsed = objectMapper.readTree(responseBody);
                if (parsed != null && parsed.isObject()) {
                    return ((ObjectNode) parsed).deepCopy();
                }
            } catch (IOException ignored) {
                // The caller retains the raw HTTP response when it is not JSON.
            }
        }
        return objectMapper.createObjectNode();
    }
}
