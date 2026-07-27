package com.lingXi.ai.domain.dto.tool;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Tool Gateway 的稳定响应信封。 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public final class AgentToolResponse {
    private final boolean success;
    private final Object data;
    private final Metadata metadata;
    private final ErrorBody error;

    private AgentToolResponse(boolean success, Object data, Metadata metadata, ErrorBody error) {
        this.success = success;
        this.data = data;
        this.metadata = metadata;
        this.error = error;
    }

    public static AgentToolResponse success(Object data, Metadata metadata) {
        return new AgentToolResponse(true, data, metadata, null);
    }

    public static AgentToolResponse failure(Metadata metadata, String code,
            String message, boolean retryable) {
        return new AgentToolResponse(false, null, metadata,
                new ErrorBody(code, message, retryable));
    }

    public boolean isSuccess() { return success; }
    public Object getData() { return data; }
    public Metadata getMetadata() { return metadata; }
    public ErrorBody getError() { return error; }

    public static final class Metadata {
        @JsonProperty("request_id")
        private final String requestId;
        private final String tool;
        @JsonProperty("elapsed_ms")
        private final long elapsedMs;
        @JsonProperty("generated_at")
        private final String generatedAt;
        @JsonProperty("permission_filtered")
        private final boolean permissionFiltered;
        private final boolean truncated;

        public Metadata(String requestId, String tool, long elapsedMs,
                String generatedAt, boolean permissionFiltered, boolean truncated) {
            this.requestId = requestId;
            this.tool = tool;
            this.elapsedMs = elapsedMs;
            this.generatedAt = generatedAt;
            this.permissionFiltered = permissionFiltered;
            this.truncated = truncated;
        }

        public String getRequestId() { return requestId; }
        public String getTool() { return tool; }
        public long getElapsedMs() { return elapsedMs; }
        public String getGeneratedAt() { return generatedAt; }
        public boolean isPermissionFiltered() { return permissionFiltered; }
        public boolean isTruncated() { return truncated; }
    }

    public static final class ErrorBody {
        private final String code;
        private final String message;
        private final boolean retryable;

        public ErrorBody(String code, String message, boolean retryable) {
            this.code = code;
            this.message = message;
            this.retryable = retryable;
        }

        public String getCode() { return code; }
        public String getMessage() { return message; }
        public boolean isRetryable() { return retryable; }
    }
}
