package com.lingXi.aiVedio.domain;

import com.lingXi.common.core.domain.BaseEntity;
import lombok.Data;

/** AI 视频分镜对象 ai_video_shot */
@Data
public class AiVideoShot extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long shotId;
    private Long projectId;
    private Long chapterId;
    private Long sceneId;
    private Integer shotNo;
    private Integer durationMs;
    private String shotSize;
    private String cameraMovement;
    private String compositionText;
    private String actionText;
    private String emotionText;
    private String dialogueJson;
    private String promptContextJson;
    private String status;
    private Integer versionNo;
}
