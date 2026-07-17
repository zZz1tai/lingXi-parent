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
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VideoClient(VideoConfig config) {
        this.config = config;
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

        public ImageResult(boolean success, String imageUrl, String error, Integer statusCode) {
            this.success = success;
            this.imageUrl = imageUrl;
            this.error = error;
            this.statusCode = statusCode;
        }

        public boolean success() { return success; }
        public String imageUrl() { return imageUrl; }
        public String error() { return error; }
        public Integer statusCode() { return statusCode; }
    }

    /**
     * Result for video submission.
     */
    public static class VideoSubmitResult {
        private final boolean success;
        private final String taskId;
        private final String error;
        private final Integer statusCode;

        public VideoSubmitResult(boolean success, String taskId, String error, Integer statusCode) {
            this.success = success;
            this.taskId = taskId;
            this.error = error;
            this.statusCode = statusCode;
        }

        public boolean success() { return success; }
        public String taskId() { return taskId; }
        public String error() { return error; }
        public Integer statusCode() { return statusCode; }
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

        public VideoQueryResult(boolean success, String status, String videoUrl, String error, Integer statusCode) {
            this.success = success;
            this.status = status;
            this.videoUrl = videoUrl;
            this.error = error;
            this.statusCode = statusCode;
        }

        public boolean success() { return success; }
        public String status() { return status; }
        public String videoUrl() { return videoUrl; }
        public String error() { return error; }
        public Integer statusCode() { return statusCode; }
    }

    // ── Image Generation ────────────────────────────────────────────────────

    /**
     * Generate an image from text prompt using Python Agent.
     *
     * @param apiKey            DashScope API key
     * @param model             Image generation model name
     * @param prompt            Text prompt
     * @param negativePrompt    Negative prompt (optional)
     * @param aspectRatio       Aspect ratio (e.g., "16:9", "9:16", "1:1")
     * @param referenceImageUrls Reference image URLs (optional, max 3)
     * @param promptExtend      Whether to extend the prompt
     * @return ImageResult with success status and image URL or error
     */
    public ImageResult generateImage(
            String apiKey,
            String model,
            String prompt,
            String negativePrompt,
            String aspectRatio,
            List<String> referenceImageUrls,
            boolean promptExtend) {

        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("api_key", apiKey);
            body.put("model", model);
            body.put("prompt", prompt);
            if (negativePrompt != null && !negativePrompt.isEmpty()) {
                body.put("negative_prompt", negativePrompt);
            }
            body.put("aspect_ratio", aspectRatio);
            if (referenceImageUrls != null && !referenceImageUrls.isEmpty()) {
                body.set("reference_image_urls", objectMapper.valueToTree(referenceImageUrls));
            }
            body.put("prompt_extend", promptExtend);

            String url = config.getBaseUrl() + config.getGenerateImageUrl();
            int timeout = config.getImageReadTimeout();

            JsonNode response = doPost(url, body.toString(), timeout);
            return parseImageResponse(response);

        } catch (Exception e) {
            log.error("Failed to generate image: {}", e.getMessage(), e);
            return new ImageResult(false, null, e.getMessage(), null);
        }
    }

    private ImageResult parseImageResponse(JsonNode response) {
        boolean success = response.path("success").asBoolean(false);
        if (success) {
            String imageUrl = response.path("image_url").asText(null);
            return new ImageResult(true, imageUrl, null, 200);
        } else {
            String error = response.path("error").asText("Unknown error");
            int statusCode = response.path("status_code").asInt(0);
            return new ImageResult(false, null, error, statusCode);
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
     * @param promptExtend   Whether to extend the prompt
     * @return VideoSubmitResult with task ID or error
     */
    public VideoSubmitResult submitVideo(
            String apiKey,
            String model,
            String prompt,
            String negativePrompt,
            String imageUrl,
            String resolution,
            int durationMs,
            boolean promptExtend) {

        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("api_key", apiKey);
            body.put("model", model);
            body.put("prompt", prompt);
            if (negativePrompt != null && !negativePrompt.isEmpty()) {
                body.put("negative_prompt", negativePrompt);
            }
            body.put("image_url", imageUrl);
            body.put("resolution", resolution);
            body.put("duration_ms", durationMs);
            body.put("prompt_extend", promptExtend);

            String url = config.getBaseUrl() + config.getSubmitVideoUrl();
            int timeout = config.getVideoReadTimeout();

            JsonNode response = doPost(url, body.toString(), timeout);
            return parseVideoSubmitResponse(response);

        } catch (Exception e) {
            log.error("Failed to submit video task: {}", e.getMessage(), e);
            return new VideoSubmitResult(false, null, e.getMessage(), null);
        }
    }

    private VideoSubmitResult parseVideoSubmitResponse(JsonNode response) {
        boolean success = response.path("success").asBoolean(false);
        if (success) {
            String taskId = response.path("task_id").asText(null);
            return new VideoSubmitResult(true, taskId, null, 200);
        } else {
            String error = response.path("error").asText("Unknown error");
            int statusCode = response.path("status_code").asInt(0);
            return new VideoSubmitResult(false, null, error, statusCode);
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
            body.put("task_id", taskId);

            String url = config.getBaseUrl() + config.getQueryVideoUrl();
            int timeout = config.getVideoReadTimeout();

            JsonNode response = doPost(url, body.toString(), timeout);
            return parseVideoQueryResponse(response);

        } catch (Exception e) {
            log.error("Failed to query video task: {}", e.getMessage(), e);
            return new VideoQueryResult(false, null, null, e.getMessage(), null);
        }
    }

    private VideoQueryResult parseVideoQueryResponse(JsonNode response) {
        boolean success = response.path("success").asBoolean(false);
        if (success) {
            String status = response.path("status").asText("UNKNOWN");
            String videoUrl = response.path("video_url").asText(null);
            String error = response.path("error").asText(null);
            return new VideoQueryResult(true, status, videoUrl, error, 200);
        } else {
            String error = response.path("error").asText("Unknown error");
            int statusCode = response.path("status_code").asInt(0);
            return new VideoQueryResult(false, null, null, error, statusCode);
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
