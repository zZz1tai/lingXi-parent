package com.lingXi.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

/** Normalizes Python Agent success/error envelopes without reflecting raw bodies. */
final class AgentResponseUtil {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;
    static final int MAX_RESPONSE_BODY_BYTES = 2 * 1024 * 1024;

    private AgentResponseUtil() {
    }

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

    static JsonNode parseSuccess(ObjectMapper mapper, String responseBody) throws IOException {
        JsonNode response;
        try {
            response = mapper.readTree(responseBody);
        } catch (IOException ignored) {
            throw new IOException("Python Agent returned an invalid JSON response");
        }
        if (response == null || !response.isObject()) {
            throw new IOException("Python Agent returned an invalid JSON response");
        }
        return response;
    }

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
            } catch (IOException ignored) {
                // Fail closed with a stable transport error; never reflect raw content.
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

    static String errorCode(JsonNode response, String fallbackCode) {
        JsonNode nested = response.path("error");
        if (nested.isObject()) {
            return safeText(nested.path("code").asText(fallbackCode), fallbackCode);
        }
        return safeText(response.path("error_code").asText(fallbackCode), fallbackCode);
    }

    static String errorMessage(JsonNode response, String fallbackMessage) {
        JsonNode nested = response.path("error");
        if (nested.isObject()) {
            return safeText(nested.path("message").asText(fallbackMessage), fallbackMessage);
        }
        return safeText(response.path("error").asText(fallbackMessage), fallbackMessage);
    }

    static String safeText(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            normalized = fallback;
        }
        return normalized.length() > MAX_ERROR_MESSAGE_LENGTH
                ? normalized.substring(0, MAX_ERROR_MESSAGE_LENGTH) : normalized;
    }
}
