package com.lingXi.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "dashscope")
public class DashScopeConfig {
    private String apiKey;
    /** 文本模型，必须通过 dashscope.model 配置。 */
    private String model;
    /** 文生图模型，必须通过 dashscope.image-model 配置。 */
    private String imageModel;
    /** 图片同步生成地址，必须通过 dashscope.image-generation-url 配置。 */
    private String imageGenerationUrl;
    /** 异步任务查询地址前缀，任务 ID 会追加在末尾。 */
    private String taskQueryUrl;
    /** Wanx 图生视频模型，必须通过 dashscope.video-model 配置。 */
    private String videoModel;
    /** Wanx 图生视频异步提交地址，必须通过 dashscope.video-synthesis-url 配置。 */
    private String videoSynthesisUrl;
    /** Wanx 输出清晰度，必须通过 dashscope.video-resolution 配置。 */
    private String videoResolution;
    /** 是否由 Wanx 扩写视频提示词，必须通过 dashscope.video-prompt-extend 配置。 */
    private Boolean videoPromptExtend;
    /** 分镜未给出时长时采用的目标时长，必须通过 dashscope.video-default-duration-ms 配置。 */
    private Integer videoDefaultDurationMs;
}
