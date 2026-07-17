package com.lingXi.ai.service.impl;

import com.lingXi.ai.client.AgentClient;
import com.lingXi.ai.config.DashScopeConfig;
import com.lingXi.manage.domain.ModelHistory;
import com.lingXi.manage.service.IDashBoardService;
import com.lingXi.manage.service.IModelHistoryService;
import com.lingXi.ai.service.IQwenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class QwenServiceImpl implements IQwenService {

    private final DashScopeConfig config;
    private final AgentClient agentClient;
    private final IModelHistoryService modelHistoryService;
    private final IDashBoardService dashBoardService;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public QwenServiceImpl(DashScopeConfig config, AgentClient agentClient,
                           IModelHistoryService modelHistoryService, IDashBoardService dashBoardService) {
        this.config = config;
        this.agentClient = agentClient;
        this.modelHistoryService = modelHistoryService;
        this.dashBoardService = dashBoardService;
    }

    @Override
    public String chat(String sessionId, String userMessage) {
        return agentClient.chat(userMessage, sessionId);
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
        String prompt = buildPromptWithContext(userMessage, contextData);
        return agentClient.chat(prompt, sessionId);
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
        saveUserMessage(sessionId, userId, userName, userMessage);

        SseEmitter emitter = agentClient.streamChat(userMessage, sessionId);

        // 异步保存助手回复
        executorService.execute(() -> {
            try {
                // 等待流完成，然后获取完整回复保存
                // AgentClient 的 streamChat 已经处理了 SSE 事件
                // 这里我们需要在流完成后保存回复
                // 由于 SseEmitter 是异步的，我们通过监听器来保存
                emitter.onCompletion(() -> {
                    log.info("流式聊天完成，会话ID: {}", sessionId);
                });
            } catch (Exception e) {
                log.error("设置流式聊天完成回调失败", e);
            }
        });

        return emitter;
    }

    @Override
    public SseEmitter streamChatWithContext(String sessionId, String userId, String userName, String userMessage, String contextData) {
        saveUserMessage(sessionId, userId, userName, userMessage);

        String prompt = buildPromptWithContext(userMessage, contextData);
        SseEmitter emitter = agentClient.streamChat(prompt, sessionId);

        emitter.onCompletion(() -> {
            log.info("流式上下文聊天完成，会话ID: {}", sessionId);
        });

        return emitter;
    }

    private String buildPromptWithContext(String userMessage, String contextData) {
        return "[重要] 你必须基于以下数据回答问题，不要回复问候语，直接分析数据并回答。\n\n" +
                "以下是系统提供的数据：\n" + contextData + "\n\n" +
                "用户的问题是：" + userMessage + "\n\n" +
                "请根据以上数据，直接给出分析结果。回答格式：\n" +
                "1. 先总结关键数据\n" +
                "2. 针对用户问题给出具体回答\n" +
                "3. 如果有异常情况，指出并给出建议";
    }

    private void saveUserMessage(String sessionId, String userId, String userName, String content) {
        ModelHistory userHistory = new ModelHistory();
        userHistory.setSessionId(sessionId);
        userHistory.setUserId(userId);
        userHistory.setUserName(userName);
        userHistory.setContent(content);
        userHistory.setMessageType("user");
        userHistory.setModelName("agent");
        modelHistoryService.insertModelHistory(userHistory);
    }

    private void saveAssistantReply(String sessionId, String userId, String userName, String content) {
        ModelHistory assistantHistory = new ModelHistory();
        assistantHistory.setSessionId(sessionId);
        assistantHistory.setUserId(userId);
        assistantHistory.setUserName(userName);
        assistantHistory.setContent(content);
        assistantHistory.setMessageType("assistant");
        assistantHistory.setModelName("agent");
        modelHistoryService.insertModelHistory(assistantHistory);
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

            String response = agentClient.chat(prompt, sessionId);

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
                questions.add("能不能具体的说一说？");
                questions.add("还有其他相关问题吗？");
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
