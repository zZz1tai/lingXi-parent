package com.lingXi.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "dashscope")
public class DashScopeConfig {
    private String apiKey;
    /** 搬运给 Python Agent 的文生图模型。 */
    private String imageModel;
    /** 搬运给 Python Agent 的图生视频模型。 */
    private String videoModel;
    /** 搬运给 Python Agent 的视频输出清晰度。 */
    private String videoResolution;
}
