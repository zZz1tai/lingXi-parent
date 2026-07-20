package com.lingXi.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for video generation API calls to Python Agent.
 * Binds to video.* properties in application.yml.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "video")
public class VideoConfig {

    /**
     * Python Agent base URL for video endpoints.
     */
    private String baseUrl;

    /**
     * Image generation endpoint path.
     */
    private String generateImageUrl;

    /**
     * Video submission endpoint path.
     */
    private String submitVideoUrl;

    /**
     * Video query endpoint path.
     */
    private String queryVideoUrl;

    /**
     * Chapter story-bible analysis endpoint path.
     */
    private String analyzeChapterUrl;

    /** Streaming chapter-analysis endpoint that emits NDJSON progress events. */
    private String analyzeChapterStreamUrl = "/api/v1/video/analyze-chapter/stream";

    /**
     * Connection timeout in milliseconds.
     */
    private Integer connectTimeout;

    /**
     * Read timeout for image generation in milliseconds (3 minutes).
     */
    private Integer imageReadTimeout;

    /**
     * Idle read timeout for streamed chapter analysis, including scene-local
     * generation and repair stages in the Python LangChain workflow.
     */
    private Integer chapterReadTimeout;

    /**
     * Read timeout in seconds for each chapter-analysis LLM provider call made
     * by Python. This value is transported as llm_config.timeout_seconds.
     */
    private Integer chapterProviderReadTimeoutSeconds;

    /**
     * Read timeout for video operations in milliseconds.
     */
    private Integer videoReadTimeout;
}
