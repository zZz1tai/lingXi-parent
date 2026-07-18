package com.lingXi.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lingXi.ai.config.AgentConfig;
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
 * HTTP client for calling Python Agent video generation endpoints.
 * Migrated from Java DashScope direct calls to centralized Python API.
 */
@Slf4j
@Component
public class VideoClient {

    private final VideoConfig config;
    private final AgentConfig agentConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VideoClient(VideoConfig config, AgentConfig agentConfig) {
        this.config = config;
        this.agentConfig = agentConfig;
    }

    // ── Inner Classes (replacing Java records for compatibility) ─────────────

    /**
     * Result for image generation.
     */
    public static class ImageResult {
        private final boolean success;
        private final String imageUrl;
        private final String error;
        private final Integer statusCode;
        private final String errorCode;
        private final boolean retryable;

        public ImageResult(boolean success, String imageUrl, String error, Integer statusCode,
                String errorCode, boolean retryable) {
            this.success = success;
            this.imageUrl = imageUrl;
            this.error = error;
            this.statusCode = statusCode;
            this.errorCode = errorCode;
            this.retryable = retryable;
        }

        public boolean success() { return success; }
        public String imageUrl() { return imageUrl; }
        public String error() { return error; }
        public Integer statusCode() { return statusCode; }
        public String errorCode() { return errorCode; }
        public boolean retryable() { return retryable; }
    }

    /**
     * Result for video submission.
     */
    public static class VideoSubmitResult {
        private final boolean success;
        private final String taskId;
        private final String error;
        private final Integer statusCode;
        private final String errorCode;
        private final boolean retryable;
        private final boolean submissionUncertain;
        private final Integer normalizedDurationMs;

        public VideoSubmitResult(boolean success, String taskId, String error, Integer statusCode,
                String errorCode, boolean retryable, boolean submissionUncertain,
                Integer normalizedDurationMs) {
            this.success = success;
            this.taskId = taskId;
            this.error = error;
            this.statusCode = statusCode;
            this.errorCode = errorCode;
            this.retryable = retryable;
            this.submissionUncertain = submissionUncertain;
            this.normalizedDurationMs = normalizedDurationMs;
        }

        public boolean success() { return success; }
        public String taskId() { return taskId; }
        public String error() { return error; }
        public Integer statusCode() { return statusCode; }
        public String errorCode() { return errorCode; }
        public boolean retryable() { return retryable; }
        public boolean submissionUncertain() { return submissionUncertain; }
        public Integer normalizedDurationMs() { return normalizedDurationMs; }
    }

    /**
     * Result for video query.
     */
    public static class VideoQueryResult {
        private final boolean success;
        private final String status;
        private final String videoUrl;
        private final String error;
        private final Integer statusCode;
        private final String errorCode;
        private final boolean retryable;

        public VideoQueryResult(boolean success, String status, String videoUrl, String error,
                Integer statusCode, String errorCode, boolean retryable) {
            this.success = success;
            this.status = status;
            this.videoUrl = videoUrl;
            this.error = error;
            this.statusCode = statusCode;
            this.errorCode = errorCode;
            this.retryable = retryable;
        }

        public boolean success() { return success; }
        public String status() { return status; }
        public String videoUrl() { return videoUrl; }
        public String error() { return error; }
        public Integer statusCode() { return statusCode; }
        public String errorCode() { return errorCode; }
        public boolean retryable() { return retryable; }
    }

    // ── Image Generation ────────────────────────────────────────────────────

    /**
     * Generate an image from text prompt using Python Agent.
     *
     * @param apiKey            DashScope API key
     * @param model             Image generation model name
     * @param assetType         Asset business type used by Python to select model rules
     * @param prompt            Text prompt
     * @param negativePrompt    Negative prompt (optional)
     * @param aspectRatio       Aspect ratio (e.g., "16:9", "9:16", "1:1")
     * @param referenceImageUrls Reference image URLs resolved from asset relations
     * @return ImageResult with success status and image URL or error
     */
    public ImageResult generateImage(
            String apiKey,
            String model,
            String assetType,
            String prompt,
            String negativePrompt,
            String aspectRatio,
            List<String> referenceImageUrls) {

        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("api_key", apiKey);
            body.put("model", model);
            putProviderBaseUrl(body);
            if (assetType != null && !assetType.trim().isEmpty()) {
                body.put("asset_type", assetType.trim());
            }
            body.put("prompt", prompt);
            if (negativePrompt != null && !negativePrompt.isEmpty()) {
                body.put("negative_prompt", negativePrompt);
            }
            if (aspectRatio != null && !aspectRatio.trim().isEmpty()) {
                body.put("aspect_ratio", aspectRatio.trim());
            }
            if (referenceImageUrls != null && !referenceImageUrls.isEmpty()) {
                body.set("reference_image_urls", objectMapper.valueToTree(referenceImageUrls));
            }

            String url = config.getBaseUrl() + config.getGenerateImageUrl();
            int timeout = config.getImageReadTimeout();

            JsonNode response = doPost(url, body.toString(), timeout);
            return parseImageResponse(response);

        } catch (Exception e) {
            log.error("Failed to generate image: {}", e.getMessage(), e);
            return new ImageResult(false, null, e.getMessage(), 503,
                    "AGENT_IMAGE_TRANSPORT_ERROR", true);
        }
    }

    private ImageResult parseImageResponse(JsonNode response) {
        boolean success = response.path("success").asBoolean(false);
        if (success) {
            String imageUrl = response.path("image_url").asText(null);
            return new ImageResult(true, imageUrl, null, 200, null, false);
        } else {
            String error = response.path("error").asText("Unknown error");
            int statusCode = response.path("status_code").asInt(0);
            String errorCode = response.path("error_code").asText(null);
            boolean retryable = response.path("retryable").asBoolean(false);
            return new ImageResult(false, null, error, statusCode, errorCode, retryable);
        }
    }

    // ── Video Submission ────────────────────────────────────────────────────

    /**
     * Submit a video generation task using Python Agent.
     *
     * @param apiKey         DashScope API key
     * @param model          Video generation model name
     * @param prompt         Text prompt
     * @param negativePrompt Negative prompt (optional)
     * @param imageUrl       Public URL of the keyframe image
     * @param resolution     Video resolution (e.g., "720P")
     * @param durationMs     Duration in milliseconds
     * @return VideoSubmitResult with task ID or error
     */
    public VideoSubmitResult submitVideo(
            String apiKey,
            String model,
            String prompt,
            String negativePrompt,
            String imageUrl,
            String resolution,
            int durationMs) {

        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("api_key", apiKey);
            body.put("model", model);
            putProviderBaseUrl(body);
            body.put("prompt", prompt);
            if (negativePrompt != null && !negativePrompt.isEmpty()) {
                body.put("negative_prompt", negativePrompt);
            }
            body.put("image_url", imageUrl);
            body.put("resolution", resolution);
            body.put("duration_ms", durationMs);

            String url = config.getBaseUrl() + config.getSubmitVideoUrl();
            int timeout = config.getVideoReadTimeout();

            JsonNode response = doPost(url, body.toString(), timeout);
            return parseVideoSubmitResponse(response);

        } catch (Exception e) {
            log.error("Failed to submit video task: {}", e.getMessage(), e);
            return new VideoSubmitResult(false, null,
                    "WANX_SUBMISSION_UNCERTAIN: Agent transport error: " + e.getMessage(),
                    503, "WANX_SUBMISSION_UNCERTAIN", false, true, null);
        }
    }

    private VideoSubmitResult parseVideoSubmitResponse(JsonNode response) {
        boolean success = response.path("success").asBoolean(false);
        Integer normalizedDurationMs = response.path("normalized_duration_ms").canConvertToInt()
                ? Integer.valueOf(response.path("normalized_duration_ms").asInt()) : null;
        if (success) {
            String taskId = response.path("task_id").asText(null);
            return new VideoSubmitResult(true, taskId, null, 200, null, false, false,
                    normalizedDurationMs);
        } else {
            String error = response.path("error").asText("Unknown error");
            int statusCode = response.path("status_code").asInt(0);
            String errorCode = response.path("error_code").asText(null);
            boolean retryable = response.path("retryable").asBoolean(false);
            boolean submissionUncertain = response.path("submission_uncertain").asBoolean(false);
            return new VideoSubmitResult(false, null, error, statusCode,
                    errorCode, retryable, submissionUncertain, normalizedDurationMs);
        }
    }

    // ── Video Query ─────────────────────────────────────────────────────────

    /**
     * Query the status of a video generation task.
     *
     * @param apiKey DashScope API key
     * @param taskId DashScope task ID
     * @return VideoQueryResult with status and video URL if completed
     */
    public VideoQueryResult queryVideo(String apiKey, String taskId) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("api_key", apiKey);
            putProviderBaseUrl(body);
            body.put("task_id", taskId);

            String url = config.getBaseUrl() + config.getQueryVideoUrl();
            int timeout = config.getVideoReadTimeout();

            JsonNode response = doPost(url, body.toString(), timeout);
            return parseVideoQueryResponse(response);

        } catch (Exception e) {
            log.error("Failed to query video task: {}", e.getMessage(), e);
            return new VideoQueryResult(false, null, null, e.getMessage(), 503,
                    "AGENT_VIDEO_QUERY_TRANSPORT_ERROR", true);
        }
    }

    private VideoQueryResult parseVideoQueryResponse(JsonNode response) {
        boolean success = response.path("success").asBoolean(false);
        if (success) {
            String status = response.path("status").asText("UNKNOWN");
            String videoUrl = response.path("video_url").asText(null);
            String error = response.path("error").asText(null);
            return new VideoQueryResult(true, status, videoUrl, error, 200, null, false);
        } else {
            String error = response.path("error").asText("Unknown error");
            int statusCode = response.path("status_code").asInt(0);
            String errorCode = response.path("error_code").asText(null);
            boolean retryable = response.path("retryable").asBoolean(false);
            return new VideoQueryResult(false, null, null, error, statusCode,
                    errorCode, retryable);
        }
    }

    // ── HTTP Helpers ────────────────────────────────────────────────────────

    /** Provider URL remains configuration-driven; Python converts compatible-mode/v1 to native api/v1. */
    private void putProviderBaseUrl(ObjectNode body) {
        String baseUrl = agentConfig.getLlmBaseUrl();
        if (baseUrl != null && !baseUrl.trim().isEmpty()) {
            body.put("base_url", baseUrl.trim());
        }
    }

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

            // Read response
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
                errorResponse.put("status_code", statusCode);
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
