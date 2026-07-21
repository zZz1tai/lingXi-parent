package com.lingXi.aiVedio.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 当前已安装的视频生成适配器；模型和请求参数由配置页面维护。 */
@Data
@Configuration
@ConfigurationProperties(prefix = "aivideo.video")
public class AiVideoProviderProperties
{
    private String provider = "happyhorse";
}
