package com.lingXi.ai.domain.dto.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/** Python Agent 调用 Java Tool Gateway 的严格请求 DTO。 */
public class AgentToolRequest {

    private String tool;
    private JsonNode arguments;
    @JsonProperty("request_context")
    private RequestContext requestContext;

    public String getTool() {
        return tool;
    }

    public void setTool(String tool) {
        this.tool = tool;
    }

    public JsonNode getArguments() {
        return arguments;
    }

    public void setArguments(JsonNode arguments) {
        this.arguments = arguments;
    }

    public RequestContext getRequestContext() {
        return requestContext;
    }

    public void setRequestContext(RequestContext requestContext) {
        this.requestContext = requestContext;
    }

    /** 不包含用户身份，只包含与令牌绑定的链路标识。 */
    public static class RequestContext {
        @JsonProperty("agent_request_id")
        private String agentRequestId;
        @JsonProperty("thread_id")
        private String threadId;

        public String getAgentRequestId() {
            return agentRequestId;
        }

        public void setAgentRequestId(String agentRequestId) {
            this.agentRequestId = agentRequestId;
        }

        public String getThreadId() {
            return threadId;
        }

        public void setThreadId(String threadId) {
            this.threadId = threadId;
        }
    }
}
