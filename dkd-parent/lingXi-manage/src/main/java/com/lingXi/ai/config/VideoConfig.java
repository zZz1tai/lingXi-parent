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
    private String baseUrl = "http://localhost:5000";

    /**
     * Image generation endpoint path.
     */
    private String generateImageUrl = "/api/v1/video/generate-image";

    /**
     * Video submission endpoint path.
     */
    private String submitVideoUrl = "/api/v1/video/submit-video";

    /**
     * Video query endpoint path.
     */
    private String queryVideoUrl = "/api/v1/video/query-video";

    /**
     * Connection timeout in milliseconds.
     */
    private Integer connectTimeout = 5000;

    /**
     * Read timeout for image generation in milliseconds (3 minutes).
     */
    private Integer imageReadTimeout = 180000;

    /**
     * Read timeout for video operations in milliseconds.
     */
    private Integer videoReadTimeout = 60000;
}
