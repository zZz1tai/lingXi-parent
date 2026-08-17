package com.lingXi.aiNovel.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import com.lingXi.aiNovel.domain.AiNovelChapter;

/** 前文章节摘要动态汇总测试。 */
class NovelStorySummaryBuilderTest
{
    @Test
    void buildUsesOnlyActivePreviousChapterBriefsInStoryOrder()
    {
        AiNovelChapter first = chapter(11L, 1, "雾门", "江离进入雾城并得到半张星图。");
        AiNovelChapter second = chapter(12L, 2, "井底", "江离发现姐姐留下的断手镯。");
        AiNovelChapter current = chapter(13L, 3, "来客", null);
        AiNovelChapter future = chapter(14L, 4, "追兵", "追兵抵达雾城。");

        String summary = NovelStorySummaryBuilder.build(
                List.of(future, second, current, first), current, 2_000);

        assertTrue(summary.indexOf("第1章") < summary.indexOf("第2章"));
        assertTrue(summary.contains("半张星图"));
        assertTrue(summary.contains("断手镯"));
        assertFalse(summary.contains("追兵抵达"));
    }

    @Test
    void rebuildNaturallyDropsDeletedOrMissingChapter()
    {
        AiNovelChapter first = chapter(11L, 1, "雾门", "第一章摘要。");
        AiNovelChapter deletedSecond = chapter(12L, 2, "井底", "已删除章节摘要。");
        AiNovelChapter current = chapter(13L, 3, "来客", null);

        String beforeDelete = NovelStorySummaryBuilder.build(
                List.of(first, deletedSecond, current), current, 2_000);
        String afterDelete = NovelStorySummaryBuilder.build(
                List.of(first, current), current, 2_000);

        assertTrue(beforeDelete.contains("已删除章节摘要"));
        assertFalse(afterDelete.contains("已删除章节摘要"));
        assertTrue(afterDelete.contains("第一章摘要"));
    }

    @Test
    void buildReturnsNullWhenNoPreviousBriefExists()
    {
        AiNovelChapter current = chapter(13L, 1, "开篇", null);

        assertNull(NovelStorySummaryBuilder.build(List.of(current), current, 2_000));
    }

    private static AiNovelChapter chapter(
            Long chapterId, int chapterNo, String title, String brief)
    {
        AiNovelChapter chapter = new AiNovelChapter();
        chapter.setChapterId(chapterId);
        chapter.setChapterNo(chapterNo);
        chapter.setChapterTitle(title);
        chapter.setChapterBrief(brief);
        return chapter;
    }
}
