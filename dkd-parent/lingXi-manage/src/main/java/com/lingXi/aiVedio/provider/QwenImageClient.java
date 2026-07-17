package com.lingXi.aiVedio.provider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingXi.ai.config.DashScopeConfig;

/** Qwen Image 同步图片生成 API 客户端。 */
@Component
public class QwenImageClient
{
    @Autowired
    private DashScopeConfig config;
    @Autowired
    private ObjectMapper objectMapper;
    @Value("${aivideo.image.request-min-interval-ms}")
    private long requestMinIntervalMs;
    @Value("${aivideo.image.sync-read-timeout-ms}")
    private int syncReadTimeoutMs;
    private long lastRequestTime;

    public String generate(String prompt, String negativePrompt, String aspectRatio) throws Exception
    {
        return generate(prompt, negativePrompt, aspectRatio, Collections.<String>emptyList());
    }

    public synchronized String generate(String prompt, String negativePrompt, String aspectRatio,
            List<String> referenceImageUrls) throws Exception
    {
        requireConfigured(config.getApiKey(), "dashscope.api-key");
        requireConfigured(config.getImageModel(), "dashscope.image-model");
        requireConfigured(config.getImageGenerationUrl(), "dashscope.image-generation-url");
        awaitRequestSlot();
        HttpURLConnection connection = (HttpURLConnection) new URL(config.getImageGenerationUrl()).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(syncReadTimeoutMs);
        connection.setDoOutput(true);

        com.fasterxml.jackson.databind.node.ObjectNode body = objectMapper.createObjectNode();
        body.put("model", config.getImageModel());
        com.fasterxml.jackson.databind.node.ObjectNode input = body.putObject("input");
        com.fasterxml.jackson.databind.node.ArrayNode messages = input.putArray("messages");
        com.fasterxml.jackson.databind.node.ObjectNode message = messages.addObject();
        message.put("role", "user");
        com.fasterxml.jackson.databind.node.ArrayNode content = message.putArray("content");
        for (String referenceImageUrl : normalizeReferenceImageUrls(referenceImageUrls))
        {
            content.addObject().put("image", referenceImageUrl);
        }
        content.addObject().put("text", prompt);
        com.fasterxml.jackson.databind.node.ObjectNode parameters = body.putObject("parameters");
        if (negativePrompt != null && !negativePrompt.trim().isEmpty())
        {
            parameters.put("negative_prompt", negativePrompt);
        }
        parameters.put("prompt_extend", true);
        parameters.put("watermark", false);
        parameters.put("size", toImageSize(aspectRatio));

        try (OutputStream output = connection.getOutputStream())
        {
            output.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        JsonNode response = readJson(connection);
        String imageUrl = response.path("output").path("choices").path(0).path("message")
                .path("content").path(0).path("image").asText();
        if (imageUrl.isEmpty())
        {
            throw new IllegalStateException("Qwen Image 未返回生成图片地址: " + response.toString());
        }
        return imageUrl;
    }

    private List<String> normalizeReferenceImageUrls(List<String> referenceImageUrls)
    {
        if (referenceImageUrls == null || referenceImageUrls.isEmpty())
        {
            return Collections.emptyList();
        }
        if (referenceImageUrls.size() > 3)
        {
            throw new IllegalArgumentException("Qwen Image 最多支持3张输入参考图");
        }
        List<String> normalized = new ArrayList<>(referenceImageUrls.size());
        for (String referenceImageUrl : referenceImageUrls)
        {
            if (referenceImageUrl == null || referenceImageUrl.trim().isEmpty())
            {
                throw new IllegalArgumentException("Qwen Image 参考图URL不能为空");
            }
            normalized.add(referenceImageUrl.trim());
        }
        return normalized;
    }

    private JsonNode readJson(HttpURLConnection connection) throws Exception
    {
        int status = connection.getResponseCode();
        InputStream responseStream = status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream();
        if (responseStream == null)
        {
            throw new DashScopeImageApiException(status, "empty response");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(responseStream, StandardCharsets.UTF_8)))
        {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null)
            {
                content.append(line);
            }
            if (status < 200 || status >= 300)
            {
                throw new DashScopeImageApiException(status, content.toString());
            }
            return objectMapper.readTree(content.toString());
        }
    }

    public static String toImageSize(String aspectRatio)
    {
        if ("9:16".equals(aspectRatio)) return "720*1280";
        if ("1:1".equals(aspectRatio)) return "1024*1024";
        return "1280*720";
    }

    private void awaitRequestSlot() throws InterruptedException
    {
        long waitMs = requestMinIntervalMs - (System.currentTimeMillis() - lastRequestTime);
        if (waitMs > 0) Thread.sleep(waitMs);
        lastRequestTime = System.currentTimeMillis();
    }

    private void requireConfigured(String value, String propertyName)
    {
        if (value == null || value.trim().isEmpty())
        {
            throw new IllegalStateException("缺少配置项 " + propertyName);
        }
    }

    public static boolean isRetryable(Throwable error)
    {
        Throwable current = error;
        while (current != null)
        {
            if (current instanceof DashScopeImageApiException)
            {
                int status = ((DashScopeImageApiException) current).getStatusCode();
                return status == 408 || status == 429 || status >= 500;
            }
            if (current instanceof SocketTimeoutException) return true;
            if (current instanceof IOException) return true;
            current = current.getCause();
        }
        return false;
    }

    public static class DashScopeImageApiException extends IllegalStateException
    {
        private static final long serialVersionUID = 1L;
        private final int statusCode;

        public DashScopeImageApiException(int statusCode, String responseBody)
        {
            super("DashScope 图片 API 请求失败(" + statusCode + "): " + responseBody);
            this.statusCode = statusCode;
        }

        public int getStatusCode()
        {
            return statusCode;
        }
    }

}
