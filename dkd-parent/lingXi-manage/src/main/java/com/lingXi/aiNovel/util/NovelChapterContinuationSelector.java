package com.lingXi.aiNovel.util;

import java.util.List;
import com.lingXi.aiNovel.domain.AiNovelChapter;
import com.lingXi.common.utils.StringUtils;

/**
 * 为新建的空章节选择最近一个已有正文的前置章节，供 AI 无缝续写。
 */
public final class NovelChapterContinuationSelector
{
    private NovelChapterContinuationSelector()
    {
    }

    /** 返回当前章节之前、章节序号最大的非空正文；不会读取当前章之后的内容。 */
    public static String selectPreviousContent(
            AiNovelChapter currentChapter, List<AiNovelChapter> chapters)
    {
        if (currentChapter == null || chapters == null || chapters.isEmpty())
        {
            return null;
        }
        Integer currentNo = currentChapter.getChapterNo();
        AiNovelChapter previous = null;
        for (AiNovelChapter candidate : chapters)
        {
            if (candidate == null || StringUtils.isBlank(candidate.getContent()))
            {
                continue;
            }
            Integer candidateNo = candidate.getChapterNo();
            if (currentNo == null || candidateNo == null || candidateNo >= currentNo)
            {
                continue;
            }
            if (previous == null || previous.getChapterNo() == null
                    || candidateNo > previous.getChapterNo())
            {
                previous = candidate;
            }
        }
        return previous == null ? null : previous.getContent();
    }
}
