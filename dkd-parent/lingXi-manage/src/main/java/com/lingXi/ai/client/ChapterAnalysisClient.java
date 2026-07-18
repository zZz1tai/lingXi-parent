package com.lingXi.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lingXi.ai.config.VideoConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
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

    private final VideoConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

        public AnalysisResult(boolean success, JsonNode storyBible, String rawLlmResponse, String error) {
            this.success = success;
            this.storyBible = storyBible;
            this.rawLlmResponse = rawLlmResponse;
            this.error = error;
        }

        public boolean isSuccess() { return success; }
        public JsonNode getStoryBible() { return storyBible; }
        public String getRawLlmResponse() { return rawLlmResponse; }
        public String getError() { return error; }
    }

    // ── API Methods ─────────────────────────────────────────────────────────

    /**
     * Analyze a chapter and produce a structured story bible.
     *
     * @param apiKey            DashScope API key
     * @param model             LLM model name
     * @param baseUrl           LLM base URL
     * @param chapterTitle      Chapter title
     * @param sourceText        Raw chapter source text
     * @param projectCharacters Existing project characters for identity reuse
     * @return AnalysisResult with validated story bible or error
     */
    public AnalysisResult analyzeChapter(
            String apiKey,
            String model,
            String baseUrl,
            String chapterTitle,
            String sourceText,
            List<ObjectNode> projectCharacters) {

        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("chapter_title", chapterTitle);
            body.put("source_text", sourceText);
            if (projectCharacters != null && !projectCharacters.isEmpty()) {
                body.set("project_characters", objectMapper.valueToTree(projectCharacters));
            }

            // Add LLM config
            ObjectNode llmConfig = objectMapper.createObjectNode();
            llmConfig.put("api_key", apiKey);
            llmConfig.put("model", model);
            if (baseUrl != null && !baseUrl.isEmpty()) {
                llmConfig.put("base_url", baseUrl);
            }
            body.set("llm_config", llmConfig);

            String url = config.getBaseUrl() + "/api/v1/video/analyze-chapter";
            int timeout = 300000; // 5 minutes for LLM call

            log.info("Calling Python chapter analysis | url={} | textLength={}", url, sourceText.length());

            JsonNode response = doPost(url, body.toString(), timeout);
            return parseAnalysisResponse(response);

        } catch (Exception e) {
            log.error("Failed to analyze chapter: {}", e.getMessage(), e);
            return new AnalysisResult(false, null, null, e.getMessage());
        }
    }

    private AnalysisResult parseAnalysisResponse(JsonNode response) {
        boolean success = response.path("success").asBoolean(false);
        if (success) {
            JsonNode storyBible = response.path("story_bible");
            String rawResponse = response.path("raw_llm_response").asText(null);
            return new AnalysisResult(true, storyBible, rawResponse, null);
        } else {
            String error = response.path("error").asText("Unknown error");
            return new AnalysisResult(false, null, null, error);
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
            conn.setConnectTimeout(config.getConnectTimeout());
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
                ObjectNode errorResponse = objectMapper.createObjectNode();
                errorResponse.put("success", false);
                errorResponse.put("error", "HTTP " + statusCode + ": " + responseBody);
                return errorResponse;
            }

            return objectMapper.readTree(responseBody);

        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
