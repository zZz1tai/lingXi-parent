package com.lingXi.aiNovel.domain.dto;

import java.util.List;

/**
 * 大纲生成结果节点（全书/卷/章）。
 * <p>对应 Python 大纲生成接口返回的三层大纲树节点，
 * 由 Service 持久化到 ai_novel_outline 表。</p>
 */
public class NovelOutlineNodeDTO
{
    /** 层级：BOOK-全书, VOLUME-卷, CHAPTER-章 */
    private String level;

    /** 标题（卷名/章名/书名） */
    private String title;

    /** 概述/梗概内容 */
    private String content;

    /** 章级大纲关联的章节号（CHAPTER 层） */
    private Integer chapterNo;

    /** 子节点（VOLUME 的父为 BOOK，CHAPTER 的父为 VOLUME） */
    private List<NovelOutlineNodeDTO> children;

    public String getLevel() { return level; }

    public void setLevel(String level) { this.level = level; }

    public String getTitle() { return title; }

    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }

    public void setContent(String content) { this.content = content; }

    public Integer getChapterNo() { return chapterNo; }

    public void setChapterNo(Integer chapterNo) { this.chapterNo = chapterNo; }

    public List<NovelOutlineNodeDTO> getChildren() { return children; }

    public void setChildren(List<NovelOutlineNodeDTO> children) { this.children = children; }
}
