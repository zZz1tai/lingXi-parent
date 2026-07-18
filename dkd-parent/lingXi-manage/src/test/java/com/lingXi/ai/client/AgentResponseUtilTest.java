package com.lingXi.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentResponseUtilTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void nestedErrorEnvelopeIsFlattenedForLegacyJavaCallers() {
        ObjectNode normalized = AgentResponseUtil.normalizeError(
                objectMapper,
                "{\"success\":false,\"error\":{\"code\":\"VALIDATION_ERROR\","
                        + "\"message\":\"invalid request\"}}",
                422,
                "AGENT_HTTP_ERROR",
                "request failed");

        assertFalse(normalized.path("success").asBoolean());
        assertEquals("VALIDATION_ERROR", normalized.path("error_code").asText());
        assertEquals("invalid request", normalized.path("error").asText());
        assertEquals(422, normalized.path("status_code").asInt());
    }

    @Test
    void acceptedUncertainSubmissionPreservesBusinessStatusAndFlags() {
        ObjectNode normalized = AgentResponseUtil.normalizeError(
                objectMapper,
                "{\"success\":false,\"error\":\"do not resubmit\","
                        + "\"error_code\":\"WANX_SUBMISSION_UNCERTAIN\","
                        + "\"status_code\":504,\"retryable\":false,"
                        + "\"submission_uncertain\":true}",
                202,
                "AGENT_HTTP_ERROR",
                "request failed");

        assertEquals(504, normalized.path("status_code").asInt());
        assertEquals("WANX_SUBMISSION_UNCERTAIN",
                normalized.path("error_code").asText());
        assertFalse(normalized.path("retryable").asBoolean());
        assertTrue(normalized.path("submission_uncertain").asBoolean());
    }

    @Test
    void invalidBodyIsNotReflectedIntoFallbackError() {
        String sentinel = "SENTINEL_PROVIDER_BODY_MUST_NOT_ESCAPE";
        ObjectNode normalized = AgentResponseUtil.normalizeError(
                objectMapper,
                "not-json-" + sentinel,
                502,
                "AGENT_HTTP_ERROR",
                "request failed");

        assertEquals("request failed", normalized.path("error").asText());
        assertFalse(normalized.toString().contains(sentinel));
    }

    @Test
    void oversizedProviderResponseBodyIsDiscarded() throws Exception {
        byte[] oversized = new byte[AgentResponseUtil.MAX_RESPONSE_BODY_BYTES + 1];
        HttpURLConnection connection = new HttpURLConnection(
                new URL("http://127.0.0.1/oversized")) {
            @Override
            public void disconnect() {
            }

            @Override
            public boolean usingProxy() {
                return false;
            }

            @Override
            public void connect() {
            }

            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(oversized);
            }
        };

        assertEquals("", AgentResponseUtil.readResponseBody(connection, 200));
    }
}
