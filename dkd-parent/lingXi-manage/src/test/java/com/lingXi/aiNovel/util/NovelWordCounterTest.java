package com.lingXi.aiNovel.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.lingXi.aiNovel.domain.AiNovelChapter;
import com.lingXi.aiNovel.domain.AiNovelOutline;
import com.lingXi.aiNovel.domain.dto.NovelOutlineContextItemDTO;

class NovelWordCounterTest
{
    @Test
    void countsManuscriptCharactersWithoutWhitespace()
    {
        assertEquals(9, NovelWordCounter.count("无常，\n 欠我\t一炷香。"));
    }

    @Test
    void ignoresUnicodeSpacesUsedByBrowserWhitespaceMatching()
    {
        assertEquals(4, NovelWordCounter.count("甲\u00A0乙\u3000丙\uFEFF丁"));
    }

    @Test
    void matchesBrowserUtf16LengthSemanticsForSupplementaryCharacters()
    {
        assertEquals(4, NovelWordCounter.count("甲😀乙"));
        assertEquals(0, NovelWordCounter.count(null));
    }

    @Test
    void selectsBookCurrentVolumeAndNearbyOutlineChapters()
    {
        List<AiNovelOutline> outlines = sampleOutline(7);
        AiNovelChapter current = new AiNovelChapter();
        current.setChapterId(103L);
        current.setChapterNo(3);

        List<NovelOutlineContextItemDTO> selected =
                NovelOutlineContextSelector.selectRelevant(outlines, current);

        assertEquals(List.of(
                "global",
                "current_volume",
                "previous_chapter",
                "previous_chapter",
                "current_chapter",
                "next_chapter",
                "next_chapter"),
                selected.stream().map(NovelOutlineContextItemDTO::getRelevance).toList());
        assertEquals(List.of(1, 2, 3, 4, 5), selected.stream()
                .filter(item -> "CHAPTER".equals(item.getLevel()))
                .map(NovelOutlineContextItemDTO::getChapterNo)
                .toList());
    }

    @Test
    void matchesFutureOutlineByPersistedChapterNumber()
    {
        List<AiNovelOutline> outlines = sampleOutline(8);
        AiNovelChapter current = new AiNovelChapter();
        current.setChapterId(999L);
        current.setChapterNo(7);

        NovelOutlineContextItemDTO currentOutline =
                NovelOutlineContextSelector.selectRelevant(outlines, current).stream()
                        .filter(item -> "current_chapter".equals(item.getRelevance()))
                        .findFirst()
                        .orElseThrow();

        assertEquals(7, currentOutline.getChapterNo());
        assertEquals("第7章", currentOutline.getTitle());
    }

    @Test
    void chapterDeletesUseNullToAvoidUniqueKeyCollisions() throws IOException
    {
        String resource = "mapper/aiNovel/AiNovelChapterMapper.xml";
        try (InputStream stream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(resource))
        {
            assertTrue(stream != null, "找不到章节 Mapper XML");
            String xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(xml.contains("set del_flag = null"));
            assertFalse(xml.contains("set del_flag = '2'"));
        }
    }

    private static List<AiNovelOutline> sampleOutline(int chapterCount)
    {
        List<AiNovelOutline> outlines = new ArrayList<>();
        outlines.add(outline(1L, "BOOK", 0L, 1, "全书", null, null));
        outlines.add(outline(10L, "VOLUME", 1L, 1, "第一卷", null, null));
        for (int chapterNo = 1; chapterNo <= chapterCount; chapterNo++)
        {
            Long chapterId = chapterNo <= 5 ? 100L + chapterNo : null;
            outlines.add(outline(
                    10L + chapterNo,
                    "CHAPTER",
                    10L,
                    chapterNo,
                    "第" + chapterNo + "章",
                    chapterId,
                    chapterNo));
        }
        return outlines;
    }

    private static AiNovelOutline outline(
            Long id, String level, Long parentId, Integer seqNo,
            String title, Long chapterId, Integer chapterNo)
    {
        AiNovelOutline outline = new AiNovelOutline();
        outline.setOutlineId(id);
        outline.setOutlineLevel(level);
        outline.setParentId(parentId);
        outline.setSeqNo(seqNo);
        outline.setOutlineTitle(title);
        outline.setOutlineContent(title + "内容");
        outline.setChapterId(chapterId);
        outline.setChapterNo(chapterNo);
        return outline;
    }
}
