package com.lingXi.ai.client;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

/**
 * Agent 响应处理工具类
 * <p>标准化 Python Agent 的成功/错误响应封装，不暴露原始响应体。</p>
 */
final class AgentResponseUtil {

    /** 对外错误文本最大长度，避免远端异常内容无限扩张。 */
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;
    /** 允许读取的最大响应体大小，超过上限时按无效响应处理。 */
    static final int MAX_RESPONSE_BODY_BYTES = 2 * 1024 * 1024;

    /** 工具类不允许实例化。 */
    private AgentResponseUtil() {
    }

    /**
     * 读取 HTTP 响应体内容
     *
     * @param conn     HTTP 连接对象
     * @param statusCode HTTP 状态码
     * @return 响应体字符串
     * @throws IOException 读取失败时抛出
     */
    static String readResponseBody(HttpURLConnection conn, int statusCode) throws IOException {
        InputStream stream = statusCode >= 200 && statusCode < 300
                ? conn.getInputStream() : conn.getErrorStream();
        if (stream == null) {
            return "";
        }
        try (InputStream input = stream;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(
                    buffer,
                    0,
                    Math.min(buffer.length, MAX_RESPONSE_BODY_BYTES - total + 1))) != -1) {
                if (total + read > MAX_RESPONSE_BODY_BYTES) {
                    return "";
                }
                output.write(buffer, 0, read);
                total += read;
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    /**
     * 解析成功的 JSON 响应
     *
     * @param mapper       Jackson ObjectMapper 实例
     * @param responseBody 响应体字符串
     * @return 解析后的 JsonNode 对象
     * @throws IOException JSON 解析失败或响应格式无效时抛出
     */
    static JsonNode parseSuccess(ObjectMapper mapper, String responseBody) throws IOException {
        JsonNode response;
        try {
            response = mapper.readTree(responseBody);
        } catch (JacksonException ignored) {
            throw new IOException("Python Agent returned an invalid JSON response");
        }
        if (response == null || !response.isObject()) {
            throw new IOException("Python Agent returned an invalid JSON response");
        }
        return response;
    }

    /**
     * 标准化错误响应，确保返回统一格式的错误信息
     *
     * @param mapper          Jackson ObjectMapper 实例
     * @param responseBody    响应体字符串
     * @param httpStatus      HTTP 状态码
     * @param fallbackCode    兜底错误码
     * @param fallbackMessage 兜底错误信息
     * @return 标准化后的错误 JsonNode 对象
     */
    static ObjectNode normalizeError(
            ObjectMapper mapper,
            String responseBody,
            int httpStatus,
            String fallbackCode,
            String fallbackMessage) {
        ObjectNode normalized = mapper.createObjectNode();
        if (responseBody != null && !responseBody.trim().isEmpty()) {
            try {
                JsonNode parsed = mapper.readTree(responseBody);
                if (parsed != null && parsed.isObject()) {
                    normalized = ((ObjectNode) parsed).deepCopy();
                }
            } catch (JacksonException ignored) {
                // 解析失败时使用稳定的传输错误并拒绝继续处理，绝不向调用方回显原始响应内容。
            }
        }

        normalized.put("success", false);
        if (!normalized.has("status_code")) {
            normalized.put("status_code", httpStatus);
        }

        JsonNode errorNode = normalized.path("error");
        if (errorNode.isObject()) {
            if (!normalized.hasNonNull("error_code")) {
                normalized.put("error_code", safeText(
                        errorNode.path("code").asText(fallbackCode), fallbackCode));
            }
            normalized.put("error", safeText(
                    errorNode.path("message").asText(fallbackMessage), fallbackMessage));
        } else if (errorNode.isTextual()) {
            normalized.put("error", safeText(errorNode.asText(), fallbackMessage));
        } else {
            normalized.put("error", fallbackMessage);
        }

        if (!normalized.hasNonNull("error_code")) {
            normalized.put("error_code", fallbackCode);
        }
        return normalized;
    }

    /**
     * 从响应中提取错误码
     *
     * @param response     响应 JsonNode 对象
     * @param fallbackCode 兜底错误码
     * @return 错误码字符串
     */
    static String errorCode(JsonNode response, String fallbackCode) {
        JsonNode nested = response.path("error");
        if (nested.isObject()) {
            return safeText(nested.path("code").asText(fallbackCode), fallbackCode);
        }
        return safeText(response.path("error_code").asText(fallbackCode), fallbackCode);
    }

    /**
     * 从响应中提取错误信息
     *
     * @param response        响应 JsonNode 对象
     * @param fallbackMessage 兜底错误信息
     * @return 错误信息字符串
     */
    static String errorMessage(JsonNode response, String fallbackMessage) {
        JsonNode nested = response.path("error");
        if (nested.isObject()) {
            return safeText(nested.path("message").asText(fallbackMessage), fallbackMessage);
        }
        return safeText(response.path("error").asText(fallbackMessage), fallbackMessage);
    }

    /**
     * 安全处理文本值，去除首尾空格并截断过长内容
     *
     * @param value    原始文本值
     * @param fallback 兜底文本值
     * @return 处理后的文本字符串
     */
    static String safeText(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            normalized = fallback;
        }
        return normalized.length() > MAX_ERROR_MESSAGE_LENGTH
                ? normalized.substring(0, MAX_ERROR_MESSAGE_LENGTH) : normalized;
    }
}
