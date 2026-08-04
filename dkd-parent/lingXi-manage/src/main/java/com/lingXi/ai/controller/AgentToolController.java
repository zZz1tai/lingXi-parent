package com.lingXi.ai.controller;

import tools.jackson.databind.JsonNode;
import com.lingXi.ai.domain.dto.tool.AgentToolException;
import com.lingXi.ai.domain.dto.tool.AgentToolResponse;
import com.lingXi.ai.service.AgentToolService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.converter.HttpMessageNotReadableException;

/** Python Agent 专用的内部只读 Tool Gateway。 */
@Slf4j
@RestController
@RequestMapping("/internal/ai/tools")
public class AgentToolController {

    private static final String BEARER_PREFIX = "Bearer ";
    private final AgentToolService toolService;

    public AgentToolController(AgentToolService toolService) {
        this.toolService = toolService;
    }

    @PostMapping("/invoke")
    public ResponseEntity<AgentToolResponse> invoke(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Agent-Request-Id", required = false) String requestId,
            @RequestBody JsonNode request) {
        long started = System.nanoTime();
        String tool = request == null ? "" : request.path("tool").asText("");
        try {
            String token = requireBearerToken(authorization);
            return ResponseEntity.ok(toolService.invoke(token, requestId, request));
        } catch (AgentToolException exception) {
            AgentToolResponse response = AgentToolResponse.failure(
                    toolService.failureMetadata(
                            requestId,
                            tool,
                            Math.max(0L, (System.nanoTime() - started) / 1_000_000L)),
                    exception.getCode(),
                    exception.getMessage(),
                    exception.isRetryable());
            return ResponseEntity.status(exception.getHttpStatus()).body(response);
        } catch (Exception exception) {
            log.error("Agent Tool Gateway failed | tool={} | errorType={}",
                    safeTool(tool), exception.getClass().getSimpleName());
            AgentToolResponse response = AgentToolResponse.failure(
                    toolService.failureMetadata(
                            requestId,
                            tool,
                            Math.max(0L, (System.nanoTime() - started) / 1_000_000L)),
                    "TOOL_INTERNAL_ERROR",
                    "工具服务暂时不可用",
                    true);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /** JSON 无法解析时仍返回稳定 ToolResponse，而不是框架默认错误页。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<AgentToolResponse> invalidJson() {
        AgentToolResponse response = AgentToolResponse.failure(
                toolService.failureMetadata("", "", 0L),
                "TOOL_INVALID_ARGUMENT",
                "工具请求JSON无效",
                false);
        return ResponseEntity.badRequest().body(response);
    }

    private static String requireBearerToken(String authorization) {
        if (authorization == null
                || !authorization.startsWith(BEARER_PREFIX)
                || authorization.length() <= BEARER_PREFIX.length()) {
            throw new AgentToolException(
                    "TOOL_UNAUTHORIZED", "缺少有效的工具访问凭据", 401, false);
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty() || token.length() > 256
                || token.indexOf('\r') >= 0 || token.indexOf('\n') >= 0) {
            throw new AgentToolException(
                    "TOOL_UNAUTHORIZED", "工具访问凭据无效", 401, false);
        }
        return token;
    }

    private static String safeTool(String value) {
        if (value == null || !value.matches("^[a-z_]{1,64}$")) {
            return "unknown";
        }
        return value;
    }
}
