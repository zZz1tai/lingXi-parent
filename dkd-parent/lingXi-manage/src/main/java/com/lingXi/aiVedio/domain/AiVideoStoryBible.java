package com.lingXi.aiVedio.domain;

import com.lingXi.common.core.domain.BaseEntity;
import lombok.Data;

/** AI 视频故事圣经对象 ai_video_story_bible */
@Data
public class AiVideoStoryBible extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long bibleId;
    private Long projectId;
    private Long chapterId;
    private Integer versionNo;
    private String status;
    private String worldSetting;
    private String timelineJson;
    private String relationshipJson;
    private String immutableFactsJson;
    private String contentJson;
    private String sourceReferenceJson;
    private String modelName;
    private String promptVersion;
}
