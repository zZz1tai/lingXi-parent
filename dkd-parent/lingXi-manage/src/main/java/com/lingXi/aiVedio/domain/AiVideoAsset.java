package com.lingXi.aiVedio.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.lingXi.common.core.domain.BaseEntity;
import lombok.Data;

/**
 * AI 视频统一资产对象 ai_video_asset
 */
@Data
public class AiVideoAsset extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long assetId;
    private Long projectId;
    private Long chapterId;
    private Long sceneId;
    private Long shotId;
    private Long characterId;
    private String assetCode;
    private String assetName;
    private String assetType;
    private String assetScope;
    private Integer canonicalFlag;
    private String status;
    private Integer versionNo;
    private Long sourceAssetId;
    private String storageProvider;
    private String objectKey;
    private String previewObjectKey;
    private String mimeType;
    private Long fileSize;
    private String contentHash;
    private Integer width;
    private Integer height;
    private Integer durationMs;
    private String promptText;
    private String negativePromptText;
    private String generationParamsJson;
    private String metadataJson;
    private String approvedBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date approvedTime;
}
