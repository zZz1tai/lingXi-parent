package com.lingXi.aiVedio.domain;

import com.lingXi.common.core.domain.BaseEntity;
import lombok.Data;

/** AI 视频场景对象 ai_video_scene */
@Data
public class AiVideoScene extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long sceneId;
    private Long projectId;
    private Long chapterId;
    private Integer sceneNo;
    private String sceneTitle;
    private Integer sourceParagraphFrom;
    private Integer sourceParagraphTo;
    private String timeDescription;
    private String locationDescription;
    private String atmosphere;
    private String dramaticGoal;
    private String characterIds;
    private String scenePackageJson;
    private String status;
    private Integer versionNo;
}
