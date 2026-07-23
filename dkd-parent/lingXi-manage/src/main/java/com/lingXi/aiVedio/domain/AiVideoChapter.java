package com.lingXi.aiVedio.domain;

import com.lingXi.common.core.domain.BaseEntity;
import lombok.Data;

/**
 * AI 视频章节实体类，对应数据库表 ai_video_chapter。
 * <p>章节是对项目源文本按段落划分后形成的创作单元，
 * 每个章节包含原文内容、解析状态及对应的故事圣经版本号。</p>
 */
@Data
public class AiVideoChapter extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 章节主键ID */
    private Long chapterId;

    /** 所属项目ID */
    private Long projectId;

    /** 章节序号，从1开始 */
    private Integer chapterNo;

    /** 章节标题 */
    private String chapterTitle;

    /** 章节原始文本内容 */
    private String sourceText;

    /** 原始文本的哈希值，用于检测内容变更 */
    private String sourceHash;

    /** 字数统计 */
    private Integer wordCount;

    /** 章节摘要文本 */
    private String summaryText;

    /** 解析状态，如待解析、解析中、已完成 */
    private String parseStatus;

    /** 流水线处理状态，如待处理、处理中、已完成 */
    private String pipelineStatus;

    /** 当前使用的故事圣经版本号 */
    private Integer currentBibleVersion;

    /** 来源元数据，JSON格式存储 */
    private String sourceMetadataJson;
}
