package com.lingXi.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lingXi.ai.config.AgentConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
public class AgentClient {

    private final AgentConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public AgentClient(AgentConfig config) {
        this.config = config;
    }

    /**
     * 同步调用 Agent 对话接口
     */
    public String chat(String message, String userId) {
        try {
            URL url = new URL(config.getBaseUrl() + config.getChatInvokeUrl());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setConnectTimeout(config.getConnectTimeout());
            conn.setReadTimeout(config.getReadTimeout());
            conn.setDoOutput(true);

            String requestBody = buildRequest(message, userId);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBody.getBytes(StandardCharsets.UTF_8));
            }

            if (conn.getResponseCode() == 200) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    JsonNode root = objectMapper.readTree(response.toString());
                    return extractResponse(root);
                }
            } else {
                throw new RuntimeException("Agent API error: " + conn.getResponseCode());
            }
        } catch (Exception e) {
            log.error("调用 Agent 服务失败", e);
            throw new RuntimeException("调用 Agent 服务失败", e);
        }
    }

    /**
     * 流式调用 Agent 对话接口
     */
    public SseEmitter streamChat(String message, String userId) {
        SseEmitter emitter = new SseEmitter(0L);

        executorService.execute(() -> {
            try {
                URL url = new URL(config.getBaseUrl() + config.getChatStreamUrl());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "text/event-stream");
                conn.setConnectTimeout(config.getConnectTimeout());
                conn.setReadTimeout(config.getReadTimeout());
                conn.setDoOutput(true);

                String requestBody = buildRequest(message, userId);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(requestBody.getBytes(StandardCharsets.UTF_8));
                }

                if (conn.getResponseCode() == 200) {
                    StringBuilder fullReply = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            line = line.trim();
                            if (line.startsWith("data:")) {
                                String data = line.substring(5).trim();
                                if ("[DONE]".equals(data)) {
                                    break;
                                }
                                try {
                                    JsonNode node = objectMapper.readTree(data);
                                    String eventType = node.path("type").asText("");
                                    String content = node.path("content").asText("");

                                    if ("token".equals(eventType) && !content.isEmpty()) {
                                        fullReply.append(content);
                                        emitter.send(SseEmitter.event().data(content));
                                    } else if ("done".equals(eventType) && !content.isEmpty()) {
                                        if (fullReply.length() == 0) {
                                            emitter.send(SseEmitter.event().data(content));
                                        }
                                    } else if ("error".equals(eventType)) {
                                        emitter.send(SseEmitter.event().name("error").data(content));
                                    }
                                } catch (Exception e) {
                                    log.warn("解析 Agent 流式数据失败: {}", data, e);
                                }
                            }
                        }
                    }
                    emitter.complete();
                } else {
                    throw new RuntimeException("Agent API error: " + conn.getResponseCode());
                }
            } catch (Exception e) {
                log.error("Agent 流式调用失败", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                    emitter.complete();
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            }
        });

        return emitter;
    }

    private String buildRequest(String message, String userId) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("message", message);
            root.put("style", config.getStyle());
            root.put("user_id", userId);
            root.put("max_iterations", config.getMaxIterations());

            // 添加 LLM 配置
            if (config.getLlmApiKey() != null && !config.getLlmApiKey().isEmpty()) {
                ObjectNode llmConfig = root.putObject("llm_config");
                llmConfig.put("api_key", config.getLlmApiKey());
                llmConfig.put("model", config.getLlmModel());
                if (config.getLlmBaseUrl() != null && !config.getLlmBaseUrl().isEmpty()) {
                    llmConfig.put("base_url", config.getLlmBaseUrl());
                }
            }

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("构建请求失败", e);
        }
    }

    private String extractResponse(JsonNode root) {
        JsonNode data = root.path("data");
        if (!data.isMissingNode()) {
            return data.path("response").asText("");
        }
        return root.path("response").asText("");
    }
}
