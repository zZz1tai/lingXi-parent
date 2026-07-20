package com.lingXi.aiVedio.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 当前视频生成供应商配置；模型适配器从这里读取，不复用 LLM Endpoint。 */
@Data
@Configuration
@ConfigurationProperties(prefix = "aivideo.video")
public class AiVideoProviderProperties
{
    private String provider = "happyhorse";
    private String model = "happyhorse-1.1-r2v";
    private String baseUrl = "https://llm-pjxe58t9bydrpkqj.cn-beijing.maas.aliyuncs.com/compatible-mode/v1";
    private String resolution = "720P";
    private String ratio = "16:9";
    private Boolean watermark = Boolean.FALSE;
}
