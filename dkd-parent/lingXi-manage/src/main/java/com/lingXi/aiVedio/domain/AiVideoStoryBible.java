package com.lingXi.aiVedio.domain;

import com.lingXi.common.core.domain.BaseEntity;
import lombok.Data;

/**
 * AI 视频故事圣经实体类，对应数据库表 ai_video_story_bible。
 * <p>故事圣经是用于维护叙事一致性的知识库，包含世界观设定、
 * 时间线、人物关系及不可变事实等核心叙事要素。</p>
 */
@Data
public class AiVideoStoryBible extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 故事圣经主键ID */
    private Long bibleId;

    /** 所属项目ID */
    private Long projectId;

    /** 所属章节ID，可为空（全局故事圣经） */
    private Long chapterId;

    /** 版本号，支持故事圣经内容迭代 */
    private Integer versionNo;

    /** 状态，如草稿、已发布等 */
    private String status;

    /** 世界观设定文本 */
    private String worldSetting;

    /** 时间线信息，JSON格式存储 */
    private String timelineJson;

    /** 人物关系网络，JSON格式存储 */
    private String relationshipJson;

    /** 不可变事实列表，JSON格式存储，用于约束叙事一致性 */
    private String immutableFactsJson;

    /** 故事圣经主体内容，JSON格式存储 */
    private String contentJson;

    /** 原文参考信息，JSON格式存储 */
    private String sourceReferenceJson;

    /** 生成该版本所使用的模型名称 */
    private String modelName;

    /** 生成该版本所使用的提示词版本 */
    private String promptVersion;
}
