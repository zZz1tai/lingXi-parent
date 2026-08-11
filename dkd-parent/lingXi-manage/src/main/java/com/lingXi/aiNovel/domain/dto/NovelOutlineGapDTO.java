package com.lingXi.aiNovel.domain.dto;

/**
 * 大纲-章节断链检查结果项。
 * <p>生成大纲时模型对比现有章节列表与大纲树，输出不一致项
 * 及其修复建议，由人工确认后应用。</p>
 */
public class NovelOutlineGapDTO
{
    /** 章节号 */
    private Integer chapterNo;

    /** 章节标题 */
    private String chapterTitle;

    /** 问题类型：ORPHAN_CHAPTER-游离章节, MISSING_CHAPTER-大纲缺章, MISMATCH-标题不一致 */
    private String issue;

    /** 修复建议 */
    private String suggestion;

    public Integer getChapterNo() { return chapterNo; }

    public void setChapterNo(Integer chapterNo) { this.chapterNo = chapterNo; }

    public String getChapterTitle() { return chapterTitle; }

    public void setChapterTitle(String chapterTitle) { this.chapterTitle = chapterTitle; }

    public String getIssue() { return issue; }

    public void setIssue(String issue) { this.issue = issue; }

    public String getSuggestion() { return suggestion; }

    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
}
