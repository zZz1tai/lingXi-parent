package com.lingXi.aiNovel.domain;

import com.lingXi.common.core.domain.BaseEntity;
import lombok.Data;

/**
 * AI 小说三层大纲实体类，对应数据库表 ai_novel_outline。
 * <p>大纲按 全书(BOOK) → 卷(VOLUME) → 章(CHAPTER) 三层组织，
 * 通过 parent_id 构成树形结构，章级大纲可关联具体章节用于断链修复。</p>
 */
@Data
public class AiNovelOutline extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 大纲ID */
    private Long outlineId;

    /** 所属作品ID */
    private Long workId;

    /** 层级：BOOK-全书, VOLUME-卷, CHAPTER-章 */
    private String outlineLevel;

    /** 父级大纲ID，BOOK 层为 0 */
    private Long parentId;

    /** 同级排序序号 */
    private Integer seqNo;

    /** 大纲标题 */
    private String outlineTitle;

    /** 概述/梗概内容 */
    private String outlineContent;

    /** 关联章节ID（章级大纲） */
    private Long chapterId;

    /** 关联章节号（展示用，章级大纲冗余查询字段） */
    private Integer chapterNo;
}
