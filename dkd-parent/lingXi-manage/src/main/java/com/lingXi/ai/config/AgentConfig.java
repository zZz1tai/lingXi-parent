package com.lingXi.ai.config;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * Agent 服务配置类
 * <p>绑定 application.yml 中 agent.* 前缀的配置属性。</p>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "agent")
public class AgentConfig implements EnvironmentAware {
    /** Spring 运行时环境，用于在属性未配置时读取服务认证环境变量。 */
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
    /** 结构化流式对话接口路径；Python 当前与旧流共用同一上游端点。 */
    private String chatStreamV2Url = "/api/v1/chat/stream";
    /** 人工确认后恢复同一 LangGraph checkpoint 的流式接口路径。 */
    private String chatResumeUrl = "/api/v1/chat/resume";
    /** 智能问题生成接口路径 */
    private String smartQuestionsUrl;
    /** 私有会话图片 OCR 接口路径。 */
    private String imageOcrUrl = "/api/v1/chat/ocr";
    /** 删除 Python checkpoint 会话记忆接口路径 */
    private String threadDeleteUrl = "/api/v1/chat/thread";
    /** 小说创作智能体流式创作接口路径 */
    private String novelStreamUrl = "/api/v1/novel/write/stream";
    /** 删除小说作品会话记忆接口路径 */
    private String novelThreadDeleteUrl = "/api/v1/novel/thread";
    /** 根据书名自动生成故事梗概接口路径 */
    private String novelSynopsisUrl = "/api/v1/novel/synopsis/generate";
    /** 根据书名流式生成故事梗概接口路径 */
    private String novelSynopsisStreamUrl = "/api/v1/novel/synopsis/stream";
    /** 生成小说三层大纲（全书-卷-章）并执行断链检查接口路径 */
    private String novelOutlineUrl = "/api/v1/novel/outline/generate";
    /** 分析章节节奏（评分/档位/问题与建议）接口路径 */
    private String novelPacingUrl = "/api/v1/novel/pacing/analyze";
    /** 查看当前用户长期回答偏好接口路径。 */
    private String memoryListUrl = "/api/v1/chat/memory/list";
    /** 修改当前用户单项长期回答偏好接口路径。 */
    private String memoryPreferenceUrl = "/api/v1/chat/memory/preference";
    /** 清空当前用户长期回答偏好接口路径。 */
    private String memoryClearUrl = "/api/v1/chat/memory";
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
    /** 短期工具令牌有效期（秒），服务端强制不超过15分钟。 */
    private Integer toolTokenTtlSeconds = 300;
    /** 单轮对话最多允许调用的 Java 业务工具次数。 */
    private Integer toolMaxCallsPerRun = 5;
    /** 受控写操作总开关；默认关闭，避免迁移或 checkpoint 未就绪时开放写入。 */
    private boolean writeActionsEnabled = false;
    /** 待审批提案的最长有效时间（分钟），服务端强制不超过60分钟。 */
    private Integer writeActionProposalTtlMinutes = 15;
    /** 熔断器连续失败阈值；Agent 连接层连续失败达到该次数后进入打开状态。 */
    private Integer circuitFailureThreshold = 5;
    /** 熔断器打开后尝试恢复前等待的时间（毫秒）。 */
    private Long circuitOpenTimeoutMs = 30_000L;

    /** 保存 Spring 运行时环境，供认证密钥的配置回退逻辑使用。 */
    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    /**
     * 获取服务认证密钥
     * <p>优先使用 Spring 属性配置，若未配置则回退到环境变量 AGENT_SERVICE_API_KEY。</p>
     *
     * @return 服务认证密钥
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
