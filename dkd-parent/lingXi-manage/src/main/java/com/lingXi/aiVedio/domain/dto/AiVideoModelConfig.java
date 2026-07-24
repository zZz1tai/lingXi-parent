package com.lingXi.aiVedio.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * AI 生产链路运行时配置数据传输对象。
 * <p>用于前端展示和修改AI生成链路的配置信息，包括API密钥管理、
 * 各类模型选择、视频输出参数等。</p>
 * <p>注意：API Key 只允许写入，绝不通过 JSON 或日志返回明文。</p>
 */
@Data
public class AiVideoModelConfig
{
    /** 工作空间基础URL */
    private String workspaceBaseUrl;

    /** API密钥，仅允许写入，读取时返回空 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private String apiKey;

    /** API密钥脱敏展示值 */
    private String apiKeyMasked;

    /** 是否已配置API密钥 */
    private Boolean apiKeyConfigured;

    /** 文本生成模型名称 */
    private String textModel;

    /** 单个章节内同时生成的场景数量 */
    private Integer chapterSceneConcurrency;

    /** 图片生成模型名称 */
    private String imageModel;

    /** 视频生成供应商编码 */
    private String videoProvider;

    /** 视频生成模型名称 */
    private String videoModel;

    /** 视频分辨率，如1080p、720p等 */
    private String videoResolution;

    /** 视频画面宽高比，如16:9、9:16等 */
    private String videoRatio;

    /** 是否启用水印 */
    private Boolean videoWatermark;
}
