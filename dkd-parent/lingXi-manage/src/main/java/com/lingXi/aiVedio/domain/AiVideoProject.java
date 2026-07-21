package com.lingXi.aiVedio.domain;

import java.math.BigDecimal;
import com.lingXi.common.core.domain.BaseEntity;
import lombok.Data;

/**
 * AI 视频项目对象 ai_video_project
 */
@Data
public class AiVideoProject extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long projectId;
    private String projectName;
    private String sourceType;
    private String adaptationMode;
    private String status;
    private Long ownerUserId;
    private String coverUrl;
    private String visualStyle;
    private String styleGuideJson;
    private String defaultAspectRatio;
    private String defaultLanguage;
    private Long storageBytes;
    private BigDecimal estimatedCost;
    private BigDecimal actualCost;
}
