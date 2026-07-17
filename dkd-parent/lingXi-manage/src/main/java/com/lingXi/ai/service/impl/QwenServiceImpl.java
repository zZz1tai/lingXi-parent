package com.lingXi.ai.service.impl;

import com.lingXi.ai.config.DashScopeConfig;
import com.lingXi.manage.domain.ModelHistory;
import com.lingXi.manage.service.IDashBoardService;
import com.lingXi.manage.service.IModelHistoryService;
import com.lingXi.ai.service.IQwenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class QwenServiceImpl implements IQwenService {

    private final DashScopeConfig config;
    private final IModelHistoryService modelHistoryService;
    private final IDashBoardService dashBoardService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @Value("${dashscope.ai-chat-url}")
    private String CHATURL;

    @Value("${dashscope.history-round}")
    private Integer HISTORYROUND;

    public QwenServiceImpl(DashScopeConfig config, IModelHistoryService modelHistoryService, IDashBoardService dashBoardService) {
        this.config = config;
        this.modelHistoryService = modelHistoryService;
        this.dashBoardService = dashBoardService;
    }

    @Override
    public String chat(String sessionId, String userMessage) {
        return callModel(sessionId, userMessage, null);
    }

    @Override
    public String chat(String sessionId, String userId, String userName, String userMessage) {
        saveUserMessage(sessionId, userId, userName, userMessage);
        String reply = chat(sessionId, userMessage);
        saveAssistantReply(sessionId, userId, userName, reply);
        return reply;
    }

    @Override
    public String chatWithContext(String sessionId, String userMessage, String contextData) {
        String systemPrompt = buildSystemPrompt(contextData);
        return callModel(sessionId, userMessage, systemPrompt);
    }

    @Override
    public String chatWithContext(String sessionId, String userId, String userName, String userMessage, String contextData) {
        saveUserMessage(sessionId, userId, userName, userMessage);
        String reply = chatWithContext(sessionId, userMessage, contextData);
        saveAssistantReply(sessionId, userId, userName, reply);
        return reply;
    }

    @Override
    public SseEmitter streamChat(String sessionId, String userId, String userName, String userMessage) {
        SseEmitter emitter = new SseEmitter(0L);
        saveUserMessage(sessionId, userId, userName, userMessage);

        executorService.execute(() -> {
            try {
                String fullReply = callModelStream(sessionId, userMessage, null, emitter);
                if (fullReply != null && !fullReply.isEmpty()) {
                    saveAssistantReply(sessionId, userId, userName, fullReply);
                }
            } catch (Exception e) {
                log.error("流式聊天失败", e);
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

    @Override
    public SseEmitter streamChatWithContext(String sessionId, String userId, String userName, String userMessage, String contextData) {
        SseEmitter emitter = new SseEmitter(0L);
        saveUserMessage(sessionId, userId, userName, userMessage);

        String systemPrompt = buildSystemPrompt(contextData);

        executorService.execute(() -> {
            try {
                String fullReply = callModelStream(sessionId, userMessage, systemPrompt, emitter);
                if (fullReply != null && !fullReply.isEmpty()) {
                    saveAssistantReply(sessionId, userId, userName, fullReply);
                }
            } catch (Exception e) {
                log.error("流式上下文聊天失败", e);
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

    private String buildSystemPrompt(String contextData) {
        return "你是一个专业的设备管理和数据分析助手。请基于以下数据看板信息回答用户的问题。\n\n" +
                "数据看板信息：\n" + contextData + "\n\n" +
                "分析要求：\n" +
                "1. 仔细分析「异常设备列表」，该列表中的所有设备都是状态异常的设备，需要维修\n" +
                "2. 同时参考「工单统计」中的维修工单数据，特别是进行中的维修工单\n" +
                "3. 综合以上信息，准确识别和列出所有需要维修的设备\n" +
                "4. 请用简洁、专业的方式分析数据并回答用户问题";
    }

    private void saveUserMessage(String sessionId, String userId, String userName, String content) {
        ModelHistory userHistory = new ModelHistory();
        userHistory.setSessionId(sessionId);
        userHistory.setUserId(userId);
        userHistory.setUserName(userName);
        userHistory.setContent(content);
        userHistory.setMessageType("user");
        userHistory.setModelName(config.getModel());
        modelHistoryService.insertModelHistory(userHistory);
    }

    private void saveAssistantReply(String sessionId, String userId, String userName, String content) {
        ModelHistory assistantHistory = new ModelHistory();
        assistantHistory.setSessionId(sessionId);
        assistantHistory.setUserId(userId);
        assistantHistory.setUserName(userName);
        assistantHistory.setContent(content);
        assistantHistory.setMessageType("assistant");
        assistantHistory.setModelName(config.getModel());
        modelHistoryService.insertModelHistory(assistantHistory);
    }

    private List<ModelHistory> getRecentHistory(String sessionId) {
        return modelHistoryService.selectRecentModelHistoryBySessionId(sessionId, HISTORYROUND);
    }

    private String callModel(String sessionId, String userMessage, String systemPrompt) {
        try {
            URL url = new URL(CHATURL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);

            String requestBody = buildRequestBody(sessionId, userMessage, systemPrompt, false);

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
                    return extractReplyText(root);
                }
            } else {
                throw new RuntimeException("API error: " + conn.getResponseCode());
            }
        } catch (Exception e) {
            log.error("调用大模型失败", e);
            throw new RuntimeException("调用大模型失败", e);
        }
    }

    private String callModelStream(String sessionId, String userMessage, String systemPrompt, SseEmitter emitter) {
        StringBuilder fullReply = new StringBuilder();
        String lastSentContent = ""; // 记录上次发送的内容，用于计算增量

        try {
            URL url = new URL(CHATURL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "text/event-stream");
            conn.setDoOutput(true);

            String requestBody = buildRequestBody(sessionId, userMessage, systemPrompt, true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBody.getBytes(StandardCharsets.UTF_8));
            }

            if (conn.getResponseCode() == 200) {
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
                                String currentContent = extractStreamContent(node);

                                if (currentContent != null && !currentContent.isEmpty()) {
                                    // 计算增量内容（当前内容减去上次发送的内容）
                                    String delta = "";
                                    if (currentContent.startsWith(lastSentContent)) {
                                        delta = currentContent.substring(lastSentContent.length());
                                    } else {
                                        // 如果不以之前内容开头，可能是格式变化，发送完整内容
                                        delta = currentContent;
                                    }

                                    if (!delta.isEmpty()) {
                                        fullReply.append(delta);
                                        emitter.send(SseEmitter.event().data(delta));
                                        lastSentContent = currentContent;
                                    }
                                }
                            } catch (Exception e) {
                                log.warn("解析流式数据失败: {}", data, e);
                            }
                        }
                    }
                }
                emitter.complete();
            } else {
                throw new RuntimeException("API error: " + conn.getResponseCode());
            }
        } catch (Exception e) {
            log.error("流式调用失败", e);
            try {
                emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                emitter.complete();
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        }

        return fullReply.toString();
    }

    private String buildRequestBody(String sessionId, String userMessage, String systemPrompt, boolean stream) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", config.getModel());

        ObjectNode input = root.putObject("input");
        ArrayNode messages = input.putArray("messages");

        // 系统提示
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            ObjectNode systemMsg = messages.addObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
        }

        // 历史记录
        if (sessionId != null && !sessionId.isEmpty()) {
            List<ModelHistory> history = getRecentHistory(sessionId);
            for (int i = history.size() - 1; i >= 0 && i >= history.size() - HISTORYROUND; i--) {
                ModelHistory h = history.get(i);
                ObjectNode msg = messages.addObject();
                msg.put("role", "user".equals(h.getMessageType()) ? "user" : "assistant");
                msg.put("content", h.getContent());
            }
        }

        // 当前用户消息
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);

        // 参数
        ObjectNode parameters = root.putObject("parameters");
        parameters.put("result_format", "message");
        if (stream) {
            parameters.put("stream", true);
        }

        return root.toString();
    }

    private String extractReplyText(JsonNode root) {
        JsonNode output = root.path("output");

        // 尝试 output.text
        String text = output.path("text").asText("");
        if (!text.isEmpty()) {
            return text;
        }

        // 尝试 output.choices[0].message.content
        JsonNode choices = output.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            JsonNode choice = choices.get(0);
            JsonNode message = choice.path("message");
            String content = message.path("content").asText("");
            if (!content.isEmpty()) {
                return content;
            }
        }

        return "";
    }

    private String extractStreamContent(JsonNode node) {
        JsonNode output = node.path("output");

        // 尝试 choices[0].message.content
        JsonNode choices = output.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            JsonNode choice = choices.get(0);

            // message.content
            JsonNode message = choice.path("message");
            if (!message.isMissingNode()) {
                String content = message.path("content").asText("");
                if (!content.isEmpty()) {
                    return content;
                }
            }

            // delta.content (流式增量)
            JsonNode delta = choice.path("delta");
            if (!delta.isMissingNode()) {
                String deltaContent = delta.path("content").asText("");
                if (!deltaContent.isEmpty()) {
                    return deltaContent;
                }
            }
        }

        // 尝试 output.text
        String text = output.path("text").asText("");
        if (!text.isEmpty()) {
            return text;
        }

        return null;
    }

    @Override
    public String escapeJson(String str) {
        return str.replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    @Override
    public String formatDashboardData(String start, String end) {
        StringBuilder sb = new StringBuilder();

        try {
            // 工单统计
            List<Map<String, Object>> taskStats = dashBoardService.getTaskStats(start, end);
            sb.append("=== 工单统计 ===\n");
            for (Map<String, Object> stat : taskStats) {
                boolean isRepair = Boolean.TRUE.equals(stat.get("repair"));
                sb.append(isRepair ? "维修工单: " : "非维修工单: ");
                sb.append("总数=").append(stat.get("total"))
                        .append(", 已完成=").append(stat.get("completedTotal"))
                        .append(", 已取消=").append(stat.get("cancelTotal"))
                        .append(", 进行中=").append(stat.get("progressTotal"))
                        .append(", 参与人员数=").append(stat.get("workerCount"))
                        .append("\n");
            }

            // 销售统计
            Map<String, Object> saleStats = dashBoardService.getSaleStats(start, end);
            sb.append("\n=== 销售统计 ===\n");
            if (saleStats != null) {
                sb.append("订单数量: ").append(saleStats.get("orderCount")).append("\n");
                Object orderAmountObj = saleStats.get("orderAmount");
                if (orderAmountObj != null) {
                    try {
                        long orderAmount = Long.parseLong(orderAmountObj.toString());
                        sb.append("订单总额: ").append(orderAmount / 100.0).append(" 元\n");
                    } catch (NumberFormatException e) {
                        sb.append("订单总额: ").append(orderAmountObj).append("\n");
                    }
                }
            }

            // SKU销售排名
            List<Map<String, Object>> skuSaleRank = dashBoardService.getSkuSaleRank(start, end);
            sb.append("\n=== 商品热榜（Top 10）===\n");
            if (skuSaleRank != null && !skuSaleRank.isEmpty()) {
                for (int i = 0; i < skuSaleRank.size(); i++) {
                    Map<String, Object> sku = skuSaleRank.get(i);
                    sb.append(i + 1).append(". ")
                            .append(sku.get("skuName"))
                            .append(" - 销售数量: ").append(sku.get("count")).append(" 个\n");
                }
            }

            // 异常设备列表
            List<Map<String, Object>> abnormalEquipment = dashBoardService.getAbnormalEquipment();
            sb.append("\n=== 异常设备列表 ===\n");
            if (abnormalEquipment != null && !abnormalEquipment.isEmpty()) {
                for (Map<String, Object> equipment : abnormalEquipment) {
                    String status = "异常";
                    Object runningStatusObj = equipment.get("runningStatus");
                    if (runningStatusObj != null) {
                        String runningStatusStr = runningStatusObj.toString();
                        if (runningStatusStr.contains("\"value\":\"")) {
                            int startIndex = runningStatusStr.indexOf("\"value\":\"") + 9;
                            int endIndex = runningStatusStr.indexOf("\"", startIndex);
                            if (startIndex < endIndex) {
                                status = runningStatusStr.substring(startIndex, endIndex);
                            }
                        }
                    }
                    sb.append("设备ID: ").append(equipment.get("id"))
                            .append(", 设备内部编码: ").append(equipment.get("innerCode"))
                            .append(", 设备类型: ").append(equipment.get("vmTypeName"))
                            .append(", 地址: ").append(equipment.get("addr"))
                            .append(", 状态: ").append(status)
                            .append("\n");
                }
            } else {
                sb.append("暂无异常设备\n");
            }

        } catch (Exception e) {
            log.error("格式化看板数据失败", e);
            sb.append("\n数据获取过程中出现错误: ").append(e.getMessage());
        }

        return sb.toString();
    }

    @Override
    public List<String> generateSmartQuestions(String sessionId, String userId, String userName, List<Map<String, Object>> chatHistory) {
        try {
            StringBuilder historyText = new StringBuilder();
            int count = 0;
            for (Map<String, Object> item : chatHistory) {
                if (count >= 2) break;
                String content = item.get("content") != null ? item.get("content").toString() : "";
                boolean isUser = false;
                if (item.get("isUser") != null) {
                    isUser = Boolean.TRUE.equals(item.get("isUser"));
                } else if (item.get("messageType") != null) {
                    isUser = "user".equals(item.get("messageType"));
                }
                if (!content.isEmpty()) {
                    historyText.append(isUser ? "用户: " : "助手: ").append(content).append("\n");
                    count++;
                }
            }

            String prompt = "请基于以下对话历史的前两条记录，从用户自身视角出发，生成3条用户可能想问大模型的智能快捷提问。\n\n" +
                    "对话历史:\n" + historyText.toString() + "\n\n" +
                    "要求：\n" +
                    "1. 生成的问题必须从用户自身视角出发\n" +
                    "2. 问题必须直接关联用户之前的对话内容\n" +
                    "3. 问题应与大模型之前对话的最后一句有关（）\n\n" +
                    "请严格按照以下格式输出3条问题，每条问题占一行：\n1. 问题1\n2. 问题2\n3. 问题3";

            String response = callModel(sessionId, prompt, null);

            List<String> questions = new ArrayList<>();
            String[] lines = response.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("1.") || line.startsWith("2.") || line.startsWith("3.")) {
                    String question = line.substring(line.indexOf(".") + 1).trim();
                    if (!question.isEmpty()) {
                        questions.add(question);
                    }
                }
            }

            if (questions.size() < 3) {
                questions.clear();
                questions.add("我想了解更多细节？");
                questions.add("我有什么具体的期望或目标？");
                questions.add("我还有其他相关问题吗？");
            }

            return questions;
        } catch (Exception e) {
            log.error("生成智能快捷提问失败", e);
            List<String> defaultQuestions = new ArrayList<>();
            defaultQuestions.add("我想了解更多细节？");
            defaultQuestions.add("我有什么具体的期望或目标？");
            defaultQuestions.add("我还有其他相关问题吗？");
            return defaultQuestions;
        }
    }
}
