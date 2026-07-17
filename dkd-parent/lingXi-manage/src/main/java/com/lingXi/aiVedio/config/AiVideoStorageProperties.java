package com.lingXi.aiVedio.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 供外部模型拉取参考图的公网资源地址，例如 https://media.example.com。 */
@Data
@Configuration
@ConfigurationProperties(prefix = "aivideo")
public class AiVideoStorageProperties
{
    private String publicAssetBaseUrl;
}
