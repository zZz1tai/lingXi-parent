package com.lingXi.aiVedio.domain;

import com.lingXi.common.core.domain.BaseEntity;
import lombok.Data;

/**
 * AI 视频章节对象 ai_video_chapter
 */
@Data
public class AiVideoChapter extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long chapterId;
    private Long projectId;
    private Integer chapterNo;
    private String chapterTitle;
    private String sourceText;
    private String sourceHash;
    private Integer wordCount;
    private String summaryText;
    private String parseStatus;
    private String pipelineStatus;
    private Integer currentBibleVersion;
    private String sourceMetadataJson;
}
