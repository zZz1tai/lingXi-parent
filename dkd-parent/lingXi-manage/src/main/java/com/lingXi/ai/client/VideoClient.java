package com.lingXi.ai.client;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.lingXi.ai.config.AgentConfig;
import com.lingXi.ai.config.VideoConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 视频生成 HTTP 客户端
 * <p>调用 Python Agent 视频生成端点，已从 Java DashScope 直接调用迁移至集中式 Python API。</p>
 */
@Slf4j
@Component
public class VideoClient {

    /** Python 图片/视频端点和超时配置。 */
    private final VideoConfig config;
    /** Java 与 Python 服务间认证配置。 */
    private final AgentConfig agentConfig;
    /** 用于构造模型请求并解析标准响应信封。 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构造视频生成客户端
     *
     * @param config      视频配置
     * @param agentConfig Agent配置
     */
    public VideoClient(VideoConfig config, AgentConfig agentConfig) {
        this.config = config;
        this.agentConfig = agentConfig;
    }

    // ── 内部类（替代 Java record 以保持兼容性） ──────────────────────────

    /**
     * 图片生成结果
     */
    public static class ImageResult {
        /** 是否成功生成图片。 */
        private final boolean success;
        /** 成功时返回的图片地址。 */
        private final String imageUrl;
        /** 失败时面向业务层的安全错误信息。 */
        private final String error;
        /** Python Agent 或模型提供方返回的 HTTP 状态码。 */
        private final Integer statusCode;
        /** 供任务状态机判断错误类型的稳定错误码。 */
        private final String errorCode;
        /** 当前失败是否允许自动重试。 */
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
     * 视频提交结果
     */
    public static class VideoSubmitResult {
        /** 是否成功获得远端任务编号。 */
        private final boolean success;
        /** 模型提供方的视频生成任务编号。 */
        private final String taskId;
        /** 失败时面向业务层的安全错误信息。 */
        private final String error;
        /** Python Agent 或模型提供方返回的 HTTP 状态码。 */
        private final Integer statusCode;
        /** 供任务状态机判断错误类型的稳定错误码。 */
        private final String errorCode;
        /** 当前失败是否允许自动重试。 */
        private final boolean retryable;
        /** 请求结果不确定，重试前应先确认远端是否已创建任务。 */
        private final boolean submissionUncertain;
        /** Python 根据模型能力归一化后的实际视频时长。 */
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
     * 视频查询结果
     */
    public static class VideoQueryResult {
        /** 查询请求是否成功。 */
        private final boolean success;
        /** 模型提供方返回的任务状态。 */
        private final String status;
        /** 任务完成后返回的视频地址。 */
        private final String videoUrl;
        /** 查询失败时面向业务层的安全错误信息。 */
        private final String error;
        /** Python Agent 或模型提供方返回的 HTTP 状态码。 */
        private final Integer statusCode;
        /** 供任务轮询器判断错误类型的稳定错误码。 */
        private final String errorCode;
        /** 当前查询失败是否允许继续重试。 */
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

    // ── 图片生成 ──────────────────────────────────────────────────────────

    /**
     * 通过 Python Agent 根据文本提示生成图片
     *
     * @param apiKey            DashScope API 密钥
     * @param model             图片生成模型名称
     * @param providerBaseUrl   模型提供方基础地址
     * @param assetType         素材业务类型，用于 Python 端选择模型规则
     * @param prompt            文本提示词
     * @param negativePrompt    反向提示词（可选）
     * @param aspectRatio       宽高比（如 "16:9"、"9:16"、"1:1"）
     * @param referenceImageUrls 从素材关联中解析的参考图片URL列表
     * @return 包含成功状态和图片URL或错误信息的 ImageResult
     */
    public ImageResult generateImage(
            String apiKey,
            String model,
            String providerBaseUrl,
            String assetType,
            String prompt,
            String negativePrompt,
            String aspectRatio,
            List<String> referenceImageUrls) {

        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("api_key", apiKey);
            body.put("model", model);
            body.put("base_url", providerBaseUrl);
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
            log.error("Failed to generate image, errorType={}",
                    e.getClass().getSimpleName());
            return new ImageResult(false, null, "Python Agent image request failed", 503,
                    "AGENT_IMAGE_TRANSPORT_ERROR", true);
        }
    }

    /**
     * 解析图片生成响应
     *
     * @param response Python Agent 返回的 JSON 响应
     * @return 图片生成结果
     */
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

    // ── 视频提交 ──────────────────────────────────────────────────────────

    /**
     * 通过 Python Agent 提交视频生成任务
     *
     * @param apiKey                    DashScope API 密钥
     * @param provider                  视频生成提供方标识
     * @param providerBaseUrl           模型提供方基础地址
     * @param model                     视频生成模型名称
     * @param prompt                    文本提示词
     * @param negativePrompt            反向提示词（可选）
     * @param imageUrl                  关键帧图片的公开URL
     * @param characterReferenceImageUrls 角色参考图片URL列表
     * @param sceneReferenceImageUrl    场景参考图片URL
     * @param resolution                视频分辨率（如 "720P"）
     * @param ratio                     视频宽高比
     * @param watermark                 是否添加水印
     * @param durationMs                视频时长（毫秒）
     * @param idempotencyKey            幂等键，用于转发给提供方的稳定本地任务标识
     * @return 包含任务ID或错误信息的 VideoSubmitResult
     */
    public VideoSubmitResult submitVideo(
            String apiKey,
            String provider,
            String providerBaseUrl,
            String model,
            String prompt,
            String negativePrompt,
            String imageUrl,
            List<String> characterReferenceImageUrls,
            String sceneReferenceImageUrl,
            String resolution,
            String ratio,
            boolean watermark,
            int durationMs,
            String idempotencyKey) {

        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("api_key", apiKey);
            body.put("provider", provider);
            body.put("model", model);
            body.put("base_url", providerBaseUrl);
            body.put("prompt", prompt);
            if (negativePrompt != null && !negativePrompt.isEmpty()) {
                body.put("negative_prompt", negativePrompt);
            }
            if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                body.put("image_url", imageUrl.trim());
            }
            if (characterReferenceImageUrls != null && !characterReferenceImageUrls.isEmpty()) {
                tools.jackson.databind.node.ArrayNode characters =
                        body.putArray("character_reference_image_urls");
                for (String referenceUrl : characterReferenceImageUrls) {
                    if (referenceUrl != null && !referenceUrl.trim().isEmpty()) {
                        characters.add(referenceUrl.trim());
                    }
                }
            }
            if (sceneReferenceImageUrl != null && !sceneReferenceImageUrl.trim().isEmpty()) {
                body.put("scene_reference_image_url", sceneReferenceImageUrl.trim());
            }
            body.put("resolution", resolution);
            body.put("ratio", ratio);
            body.put("watermark", watermark);
            body.put("duration_ms", durationMs);
            body.put("idempotency_key", idempotencyKey);

            String url = config.getBaseUrl() + config.getSubmitVideoUrl();
            int timeout = config.getVideoReadTimeout();

            JsonNode response = doPost(url, body.toString(), timeout);
            return parseVideoSubmitResponse(response);

        } catch (Exception e) {
            log.error("Failed to submit video task, errorType={}",
                    e.getClass().getSimpleName());
            return new VideoSubmitResult(false, null,
                    "VIDEO_PROVIDER_SUBMISSION_UNCERTAIN: Python Agent transport failed",
                    503, "VIDEO_PROVIDER_SUBMISSION_UNCERTAIN", false, true, null);
        }
    }

    /**
     * 解析视频提交响应
     *
     * @param response Python Agent 返回的 JSON 响应
     * @return 视频提交结果
     */
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

    // ── 视频查询 ──────────────────────────────────────────────────────────

    /**
     * 查询视频生成任务状态
     *
     * @param apiKey          DashScope API 密钥
     * @param providerBaseUrl 模型提供方基础地址
     * @param taskId          DashScope 任务ID
     * @return 包含状态和视频URL（如已完成）的 VideoQueryResult
     */
    public VideoQueryResult queryVideo(String apiKey, String providerBaseUrl, String taskId) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("api_key", apiKey);
            body.put("base_url", providerBaseUrl);
            body.put("task_id", taskId);

            String url = config.getBaseUrl() + config.getQueryVideoUrl();
            int timeout = config.getVideoReadTimeout();

            JsonNode response = doPost(url, body.toString(), timeout);
            return parseVideoQueryResponse(response);

        } catch (Exception e) {
            log.error("Failed to query video task, errorType={}",
                    e.getClass().getSimpleName());
            return new VideoQueryResult(false, null, null,
                    "Python Agent video query failed", 503,
                    "AGENT_VIDEO_QUERY_TRANSPORT_ERROR", true);
        }
    }

    /**
     * 解析视频查询响应
     *
     * @param response Python Agent 返回的 JSON 响应
     * @return 视频查询结果
     */
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

    // ── HTTP 辅助方法 ─────────────────────────────────────────────────────

    /**
     * 执行 HTTP POST 请求并返回 JSON 响应
     *
     * @param urlStr      请求URL
     * @param jsonBody    JSON 请求体
     * @param readTimeout 读取超时时间（毫秒）
     * @return 解析后的 JSON 响应
     * @throws IOException 请求失败时抛出
     */
    private JsonNode doPost(String urlStr, String jsonBody, int readTimeout) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            applyServiceAuth(conn);
            conn.setConnectTimeout(config.getConnectTimeout());
            conn.setReadTimeout(readTimeout);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int statusCode = conn.getResponseCode();

            String responseBody = AgentResponseUtil.readResponseBody(conn, statusCode);

            if (statusCode < 200 || statusCode >= 300) {
                log.error("Python Agent media HTTP error | status={}", statusCode);
                return AgentResponseUtil.normalizeError(
                        objectMapper,
                        responseBody,
                        statusCode,
                        "AGENT_HTTP_ERROR",
                        "Python Agent request failed");
            }

            JsonNode response = AgentResponseUtil.parseSuccess(objectMapper, responseBody);
            if (!response.path("success").asBoolean(false)) {
                return AgentResponseUtil.normalizeError(
                        objectMapper,
                        responseBody,
                        statusCode,
                        "AGENT_REQUEST_FAILED",
                        "Python Agent request failed");
            }
            return response;

        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 为 HTTP 连接添加服务认证头
     *
     * @param conn HTTP 连接对象
     */
    private void applyServiceAuth(HttpURLConnection conn) {
        String serviceApiKey = agentConfig.getServiceApiKey();
        if (serviceApiKey == null || serviceApiKey.trim().isEmpty()) {
            throw new IllegalStateException("agent.service-api-key is not configured");
        }
        conn.setRequestProperty("X-Agent-Service-Key", serviceApiKey);
    }
}
