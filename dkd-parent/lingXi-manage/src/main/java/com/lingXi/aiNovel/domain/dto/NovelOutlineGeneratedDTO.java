package com.lingXi.aiNovel.domain.dto;

import java.util.List;

/**
 * 大纲生成接口响应：三层大纲树 + 断链检查报告。
 */
public class NovelOutlineGeneratedDTO
{
    /** 三层大纲树（BOOK → VOLUME → CHAPTER） */
    private List<NovelOutlineNodeDTO> tree;

    /** 断链检查报告（游离章节/大纲缺章/标题不一致） */
    private List<NovelOutlineGapDTO> gaps;

    public List<NovelOutlineNodeDTO> getTree() { return tree; }

    public void setTree(List<NovelOutlineNodeDTO> tree) { this.tree = tree; }

    public List<NovelOutlineGapDTO> getGaps() { return gaps; }

    public void setGaps(List<NovelOutlineGapDTO> gaps) { this.gaps = gaps; }
}
