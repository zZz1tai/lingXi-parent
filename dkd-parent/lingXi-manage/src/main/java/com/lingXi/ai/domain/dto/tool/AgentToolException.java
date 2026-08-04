package com.lingXi.ai.domain.dto.tool;

import lombok.Data;

/** 只携带稳定错误码和安全消息的 Tool Gateway 异常。 */
@Data
public class AgentToolException extends RuntimeException {
    private final String code;
    private final int httpStatus;
    private final boolean retryable;

    public AgentToolException(String code, String message, int httpStatus, boolean retryable) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

}
