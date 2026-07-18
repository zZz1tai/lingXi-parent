package com.lingXi.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "agent")
public class AgentConfig {
    /** Agent 服务基础地址 */
    private String baseUrl;
    /** 同步对话接口路径 */
    private String chatInvokeUrl;
    /** 流式对话接口路径 */
    private String chatStreamUrl;
    /** 智能问题生成接口路径 */
    private String smartQuestionsUrl;
    /** 回答风格: professional 或 casual */
    private String style;
    /** 最大迭代次数 */
    private Integer maxIterations;
    /** 连接超时时间(ms) */
    private Integer connectTimeout;
    /** 读取超时时间(ms) */
    private Integer readTimeout;

    // LLM 配置 (传递给 Python Agent)
    /** LLM API Key */
    private String llmApiKey;
    /** LLM 模型名称 */
    private String llmModel;
    /** LLM API Base URL (DashScope 等) */
    private String llmBaseUrl;
}
