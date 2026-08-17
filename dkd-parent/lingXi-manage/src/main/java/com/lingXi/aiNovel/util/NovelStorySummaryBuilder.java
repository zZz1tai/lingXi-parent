package com.lingXi.aiNovel.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import com.lingXi.aiNovel.domain.AiNovelChapter;

/**
 * 把当前章节之前的本章梗概压缩成按时间顺序排列的前情摘要。
 * <p>只使用已经保存的梗概，不把历史整章正文再次塞入模型上下文。</p>
 */
public final class NovelStorySummaryBuilder
{
    private static final int MAX_SINGLE_BRIEF_CHARS = 500;

    private NovelStorySummaryBuilder()
    {
    }

    public static String build(
            List<AiNovelChapter> chapters, AiNovelChapter currentChapter, int maxChars)
    {
        if (chapters == null || chapters.isEmpty() || currentChapter == null || maxChars < 1)
        {
            return null;
        }

        List<AiNovelChapter> ordered = new ArrayList<>(chapters);
        ordered.sort(Comparator
                .comparing(AiNovelChapter::getChapterNo,
                        Comparator.nullsLast(Integer::compareTo))
                .thenComparing(AiNovelChapter::getChapterId,
                        Comparator.nullsLast(Long::compareTo)));

        List<String> entries = new ArrayList<>();
        for (AiNovelChapter chapter : ordered)
        {
            if (!isBefore(chapter, currentChapter))
            {
                continue;
            }
            String brief = normalize(chapter.getChapterBrief());
            if (brief == null)
            {
                continue;
            }
            if (brief.length() > MAX_SINGLE_BRIEF_CHARS)
            {
                brief = brief.substring(0, MAX_SINGLE_BRIEF_CHARS);
            }
            entries.add(label(chapter) + "：" + brief);
        }

        List<String> selected = new ArrayList<>();
        int used = 0;
        for (int index = entries.size() - 1; index >= 0; index--)
        {
            String entry = entries.get(index);
            int separatorChars = selected.isEmpty() ? 0 : 1;
            if (used + separatorChars + entry.length() > maxChars)
            {
                break;
            }
            selected.add(0, entry);
            used += separatorChars + entry.length();
        }
        return selected.isEmpty() ? null : String.join("\n", selected);
    }

    private static boolean isBefore(AiNovelChapter candidate, AiNovelChapter current)
    {
        if (candidate == null || candidate.getChapterId() != null
                && candidate.getChapterId().equals(current.getChapterId()))
        {
            return false;
        }
        if (candidate.getChapterNo() != null && current.getChapterNo() != null)
        {
            return candidate.getChapterNo() < current.getChapterNo();
        }
        return false;
    }

    private static String label(AiNovelChapter chapter)
    {
        String number = chapter.getChapterNo() == null
                ? "前章" : "第" + chapter.getChapterNo() + "章";
        String title = normalize(chapter.getChapterTitle());
        return title == null ? number : number + "《" + title + "》";
    }

    private static String normalize(String value)
    {
        if (value == null)
        {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }
}
