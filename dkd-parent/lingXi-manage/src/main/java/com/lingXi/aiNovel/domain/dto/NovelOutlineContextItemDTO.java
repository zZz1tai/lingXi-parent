package com.lingXi.aiNovel.domain.dto;

import lombok.Data;

/**
 * 注入小说创作智能体的精简大纲条目。
 * <p>仅包含全书总纲、相关卷纲以及当前章节附近的章纲，避免长篇作品的
 * 完整大纲挤占模型上下文。</p>
 */
@Data
public class NovelOutlineContextItemDTO
{
    /** 层级：BOOK-全书，VOLUME-卷，CHAPTER-章。 */
    private String level;

    /** 与当前创作位置的关系。 */
    private String relevance;

    /** 大纲标题。 */
    private String title;

    /** 大纲内容（服务端已截断）。 */
    private String content;

    /** 章级大纲的计划章节号。 */
    private Integer chapterNo;
}
