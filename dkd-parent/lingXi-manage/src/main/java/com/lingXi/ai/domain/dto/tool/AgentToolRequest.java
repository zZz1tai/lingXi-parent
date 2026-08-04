package com.lingXi.ai.domain.dto.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import tools.jackson.databind.JsonNode;

/** Python Agent 调用 Java Tool Gateway 的严格请求 DTO。 */
@Data
public class AgentToolRequest {

    private String tool;
    private JsonNode arguments;
    @JsonProperty("request_context")
    private RequestContext requestContext;

    /** 不包含用户身份，只包含与令牌绑定的链路标识。 */
    @Data
    public static class RequestContext {
        @JsonProperty("agent_request_id")
        private String agentRequestId;
        @JsonProperty("thread_id")
        private String threadId;
    }
}
