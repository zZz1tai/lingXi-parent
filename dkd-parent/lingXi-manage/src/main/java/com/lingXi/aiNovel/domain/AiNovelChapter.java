package com.lingXi.aiNovel.domain;

import com.lingXi.common.core.domain.BaseEntity;
import lombok.Data;

/**
 * AI 小说章节实体类，对应数据库表 ai_novel_chapter。
 * <p>章节归属于作品，用于长篇小说按章节组织正文、梗概与发布状态。</p>
 */
@Data
public class AiNovelChapter extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 章节主键ID */
    private Long chapterId;

    /** 所属作品ID */
    private Long workId;

    /** 章节序号 */
    private Integer chapterNo;

    /** 章节标题 */
    private String chapterTitle;

    /** 本章梗概 */
    private String chapterBrief;

    /** 章节正文 */
    private String content;

    /** 正文字数 */
    private Integer wordCount;

    /** 章节状态：draft-草稿，published-已发布 */
    private String status;
}
