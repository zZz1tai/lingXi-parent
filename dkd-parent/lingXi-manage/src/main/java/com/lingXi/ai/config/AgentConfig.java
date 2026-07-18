package com.lingXi.ai.config;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

@Data
@Configuration
@ConfigurationProperties(prefix = "agent")
public class AgentConfig implements EnvironmentAware {
    /** Exact environment-variable alias used when agent.service-api-key is absent. */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private transient Environment environment;
    /** Agent 服务基础地址 */
    private String baseUrl;
    /** Java 到 Python Agent 的服务间认证密钥 */
    @ToString.Exclude
    private String serviceApiKey;
    /** 同步对话接口路径 */
    private String chatInvokeUrl;
    /** 流式对话接口路径 */
    private String chatStreamUrl;
    /** 智能问题生成接口路径 */
    private String smartQuestionsUrl;
    /** 删除 Python checkpoint 会话记忆接口路径 */
    private String threadDeleteUrl = "/api/v1/chat/thread";
    /** 回答风格: professional 或 casual */
    private String style;
    /** 最大迭代次数 */
    private Integer maxIterations;
    /** 连接超时时间(ms) */
    private Integer connectTimeout;
    /** 读取超时时间(ms) */
    private Integer readTimeout;
    /** SSE 连接最长存活时间(ms) */
    private Long streamTimeout = 310_000L;
    /** Agent 流式转发线程池核心线程数 */
    private Integer streamCorePoolSize = 4;
    /** Agent 流式转发线程池最大线程数 */
    private Integer streamMaxPoolSize = 16;
    /** Agent 流式转发等待队列长度 */
    private Integer streamQueueCapacity = 100;

    // LLM 配置 (传递给 Python Agent)
    /** LLM API Key */
    @ToString.Exclude
    private String llmApiKey;
    /** LLM 模型名称 */
    private String llmModel;
    /** LLM API Base URL (DashScope 等) */
    private String llmBaseUrl;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    /**
     * Resolve the normal Spring property first, then the documented exact
     * environment-variable alias. This avoids relying on relaxed binding to
     * reinterpret the extra underscore-separated path segments.
     */
    public String getServiceApiKey() {
        if (serviceApiKey != null && !serviceApiKey.trim().isEmpty()) {
            return serviceApiKey;
        }
        return environment == null
                ? serviceApiKey
                : environment.getProperty("AGENT_SERVICE_API_KEY", serviceApiKey);
    }
}
