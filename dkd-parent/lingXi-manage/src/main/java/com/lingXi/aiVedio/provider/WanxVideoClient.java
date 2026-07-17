package com.lingXi.aiVedio.provider;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingXi.ai.config.DashScopeConfig;

/** DashScope Wanx 图生视频异步 API 适配器。 */
@Component
public class WanxVideoClient
{
    /** 业务硬约束：任何单条视频都不得超过 10 秒。 */
    public static final int MAX_VIDEO_DURATION_MS = 10000;

    @Autowired
    private DashScopeConfig config;
    @Autowired
    private ObjectMapper objectMapper;

    /** 生成实际发送给 DashScope 的请求体；原始未截断提示词另存于任务审计 JSON。 */
    public String buildRequestJson(String prompt, String negativePrompt, String imageUrl, Integer durationMs)
    {
        requireText(prompt, "视频正向提示词");
        requireText(imageUrl, "关键帧公网地址");
        requireConfigured(config.getVideoModel(), "dashscope.video-model");
        com.fasterxml.jackson.databind.node.ObjectNode body = objectMapper.createObjectNode();
        body.put("model", config.getVideoModel());
        com.fasterxml.jackson.databind.node.ObjectNode input = body.putObject("input");
        input.put("prompt", truncateSafely(prompt.trim(), resolvePromptLimit(config.getVideoModel())));
        input.put("img_url", imageUrl);
        if (negativePrompt != null && !negativePrompt.trim().isEmpty())
        {
            input.put("negative_prompt", truncateSafely(negativePrompt.trim(), 500));
        }
        com.fasterxml.jackson.databind.node.ObjectNode parameters = body.putObject("parameters");
        parameters.put("resolution", config.getVideoResolution());
        parameters.put("prompt_extend", config.getVideoPromptExtend().booleanValue());
        Integer providerDuration = resolveProviderDurationSeconds(config.getVideoModel(),
                normalizeDurationMs(durationMs));
        if (providerDuration != null)
        {
            parameters.put("duration", providerDuration.intValue());
        }
        return body.toString();
    }

    public void validateSubmissionConfiguration()
    {
        requireConfigured(config.getApiKey(), "dashscope.api-key");
        requireConfigured(config.getVideoModel(), "dashscope.video-model");
        requireConfigured(config.getVideoSynthesisUrl(), "dashscope.video-synthesis-url");
        requireConfigured(config.getVideoResolution(), "dashscope.video-resolution");
        if (config.getVideoPromptExtend() == null)
        {
            throw new IllegalStateException("缺少配置项 dashscope.video-prompt-extend");
        }
        if (config.getVideoDefaultDurationMs() == null || config.getVideoDefaultDurationMs() <= 0
                || config.getVideoDefaultDurationMs() > MAX_VIDEO_DURATION_MS)
        {
            throw new IllegalStateException("配置项 dashscope.video-default-duration-ms 必须在 1 到 "
                    + MAX_VIDEO_DURATION_MS + " 毫秒之间");
        }
    }

    public String submit(String requestJson) throws Exception
    {
        validateSubmissionConfiguration();
        JsonNode request = objectMapper.readTree(requestJson);
        if (request == null || !request.isObject())
        {
            throw new IllegalArgumentException("Wanx 视频请求体不是有效 JSON 对象");
        }
        boolean submissionStarted = false;
        try
        {
            HttpURLConnection connection = (HttpURLConnection) new URL(config.getVideoSynthesisUrl()).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("X-DashScope-Async", "enable");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            connection.setDoOutput(true);
            submissionStarted = true;
            try (OutputStream output = connection.getOutputStream())
            {
                output.write(request.toString().getBytes(StandardCharsets.UTF_8));
            }
            JsonNode response = readJson(connection);
            String taskId = response.path("output").path("task_id").asText();
            if (taskId.isEmpty())
            {
                throw new WanxSubmissionUncertainException("Wanx 响应未包含任务ID，提交结果需要人工核对");
            }
            return taskId;
        }
        catch (WanxSubmissionUncertainException ex)
        {
            throw ex;
        }
        catch (WanxHttpException ex)
        {
            if (ex.getStatusCode() >= 500)
            {
                throw new WanxSubmissionUncertainException("Wanx 服务端异常，提交结果需要人工核对", ex);
            }
            throw ex;
        }
        catch (Exception ex)
        {
            if (submissionStarted)
            {
                throw new WanxSubmissionUncertainException("Wanx 请求已开始但未取得明确结果，请勿重复提交", ex);
            }
            throw ex;
        }
    }

    public WanxVideoTaskStatus query(String providerTaskId) throws Exception
    {
        requireConfigured(config.getApiKey(), "dashscope.api-key");
        requireConfigured(config.getTaskQueryUrl(), "dashscope.task-query-url");
        HttpURLConnection connection = (HttpURLConnection) new URL(config.getTaskQueryUrl() + providerTaskId).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        JsonNode response = readJson(connection);
        JsonNode output = response.path("output");
        String status = output.path("task_status").asText();
        String videoUrl = output.path("video_url").asText();
        if (videoUrl.isEmpty() && output.path("results").isArray() && output.path("results").size() > 0)
        {
            videoUrl = output.path("results").get(0).path("url").asText();
        }
        return new WanxVideoTaskStatus(status, videoUrl, output.path("message").asText(""));
    }

    private JsonNode readJson(HttpURLConnection connection) throws Exception
    {
        int status = connection.getResponseCode();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream(), StandardCharsets.UTF_8)))
        {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) content.append(line);
            if (status < 200 || status >= 300)
            {
                throw new WanxHttpException(status, "Wanx 视频请求失败(" + status + "): " + content);
            }
            return objectMapper.readTree(content.toString());
        }
    }

    private void requireConfigured(String value, String propertyName)
    {
        if (value == null || value.trim().isEmpty())
        {
            throw new IllegalStateException("缺少配置项 " + propertyName);
        }
    }

    private void requireText(String value, String fieldName)
    {
        if (value == null || value.trim().isEmpty())
        {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
    }

    /** 将目标时长归一化为当前模型真正支持且会回显、落库的时长。 */
    public Integer normalizeDurationMs(Integer requestedDurationMs)
    {
        requireConfigured(config.getVideoModel(), "dashscope.video-model");
        int requested = requestedDurationMs == null || requestedDurationMs <= 0
                ? requireDefaultDurationMs() : requestedDurationMs.intValue();
        int boundedRequested = Math.min(requested, MAX_VIDEO_DURATION_MS);
        int requestedSeconds = Math.max(1, (int) Math.round(boundedRequested / 1000.0d));
        String model = config.getVideoModel().toLowerCase(java.util.Locale.ROOT);
        if (model.contains("2.1") && model.contains("turbo"))
        {
            return Integer.valueOf(clamp(requestedSeconds, 3, 5) * 1000);
        }
        if (model.contains("2.1") || model.contains("2.2"))
        {
            return Integer.valueOf(5000);
        }
        if (model.contains("2.5"))
        {
            return Integer.valueOf(requestedSeconds <= 7 ? 5000 : 10000);
        }
        if (model.contains("2.6"))
        {
            return Integer.valueOf(clamp(requestedSeconds, 2, MAX_VIDEO_DURATION_MS / 1000) * 1000);
        }
        return Integer.valueOf(boundedRequested);
    }

    /** 当前已配置模型真正会接收的正向提示词字符上限。 */
    public int getPromptLimit()
    {
        requireConfigured(config.getVideoModel(), "dashscope.video-model");
        return resolvePromptLimit(config.getVideoModel());
    }

    /** Wanx 2.1-2.6 的反向提示词统一上限。 */
    public int getNegativePromptLimit()
    {
        return 500;
    }

    private Integer resolveProviderDurationSeconds(String model, Integer normalizedDurationMs)
    {
        if (model == null) return null;
        String normalized = model.toLowerCase(java.util.Locale.ROOT);
        if ((normalized.contains("2.1") && normalized.contains("turbo"))
                || normalized.contains("2.5") || normalized.contains("2.6"))
        {
            return Integer.valueOf(normalizedDurationMs.intValue() / 1000);
        }
        return null;
    }

    private int resolvePromptLimit(String model)
    {
        if (model == null) return 800;
        String normalized = model.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("2.5") || normalized.contains("2.6") ? 1500 : 800;
    }

    private int requireDefaultDurationMs()
    {
        if (config.getVideoDefaultDurationMs() == null || config.getVideoDefaultDurationMs() <= 0
                || config.getVideoDefaultDurationMs() > MAX_VIDEO_DURATION_MS)
        {
            throw new IllegalStateException("配置项 dashscope.video-default-duration-ms 必须在 1 到 "
                    + MAX_VIDEO_DURATION_MS + " 毫秒之间");
        }
        return config.getVideoDefaultDurationMs().intValue();
    }

    private int clamp(int value, int minimum, int maximum)
    {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private String truncateSafely(String value, int maxLength)
    {
        if (value.length() <= maxLength) return value;
        int end = maxLength;
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end);
    }

    public static class WanxVideoTaskStatus
    {
        private final String status;
        private final String videoUrl;
        private final String message;

        public WanxVideoTaskStatus(String status, String videoUrl, String message)
        {
            this.status = status;
            this.videoUrl = videoUrl;
            this.message = message;
        }

        public String getStatus() { return status; }
        public String getVideoUrl() { return videoUrl; }
        public String getMessage() { return message; }
    }

    /** 请求可能已被供应商受理，此类错误绝不能自动重提。 */
    public static class WanxSubmissionUncertainException extends Exception
    {
        private static final long serialVersionUID = 1L;

        public WanxSubmissionUncertainException(String message)
        {
            super(message);
        }

        public WanxSubmissionUncertainException(String message, Throwable cause)
        {
            super(message, cause);
        }
    }

    private static class WanxHttpException extends IllegalStateException
    {
        private static final long serialVersionUID = 1L;
        private final int statusCode;

        private WanxHttpException(int statusCode, String message)
        {
            super(message);
            this.statusCode = statusCode;
        }

        private int getStatusCode()
        {
            return statusCode;
        }
    }
}
