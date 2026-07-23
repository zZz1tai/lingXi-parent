package com.lingXi.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 视频生成配置类
 * <p>绑定 application.yml 中 video.* 前缀的配置属性。</p>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "video")
public class VideoConfig {

    /**
     * Python Agent 视频端点基础地址
     */
    private String baseUrl;

    /**
     * 图片生成端点路径
     */
    private String generateImageUrl;

    /**
     * 视频提交端点路径
     */
    private String submitVideoUrl;

    /**
     * 视频查询端点路径
     */
    private String queryVideoUrl;

    /**
     * 章节故事圣经分析端点路径
     */
    private String analyzeChapterUrl;

    /**
     * 流式章节分析端点，发送 NDJSON 格式的进度事件
     */
    private String analyzeChapterStreamUrl = "/api/v1/video/analyze-chapter/stream";

    /**
     * 连接超时时间（毫秒）
     */
    private Integer connectTimeout;

    /**
     * 图片生成读取超时时间（毫秒，3分钟）
     */
    private Integer imageReadTimeout;

    /**
     * 流式章节分析空闲读取超时时间（毫秒），
     * 包含 Python LangChain 工作流中的场景本地生成和修复阶段
     */
    private Integer chapterReadTimeout;

    /**
     * 章节分析 LLM 提供方调用读取超时时间（秒），
     * 作为 llm_config.timeout_seconds 传输给 Python
     */
    private Integer chapterProviderReadTimeoutSeconds;

    /**
     * 视频操作读取超时时间（毫秒）
     */
    private Integer videoReadTimeout;
}
