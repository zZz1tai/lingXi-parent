package com.lingXi.aiNovel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import com.lingXi.aiNovel.domain.AiNovelChapter;
import com.lingXi.aiNovel.mapper.AiNovelChapterMapper;
import com.lingXi.aiNovel.mapper.AiNovelContextTaskMapper;
import com.lingXi.aiNovel.service.impl.AiNovelChapterServiceImpl;
import com.lingXi.common.utils.SecurityUtils;

/** 章节正文变化后的事实摘要失效测试。 */
@ExtendWith(MockitoExtension.class)
class AiNovelChapterServiceImplTest
{
    @Mock private AiNovelChapterMapper chapterMapper;
    @Mock private IAiNovelWorkService workService;
    @Mock private AiNovelContextTaskMapper contextTaskMapper;
    @InjectMocks private AiNovelChapterServiceImpl service;

    @Test
    void updateInvalidatesOldBriefWhenContentChanges()
    {
        AiNovelChapter existing = chapter("旧正文", "旧剧情摘要");
        when(chapterMapper.selectAiNovelChapterByChapterId(31L)).thenReturn(existing);
        when(chapterMapper.updateAiNovelChapter(any())).thenReturn(1);
        AiNovelChapter update = new AiNovelChapter();
        update.setChapterId(31L);
        update.setContent("删改后的新正文");

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class))
        {
            security.when(SecurityUtils::getUsername).thenReturn("tester");
            assertEquals(1, service.updateAiNovelChapter(7L, update));
        }

        ArgumentCaptor<AiNovelChapter> captor = ArgumentCaptor.forClass(AiNovelChapter.class);
        verify(chapterMapper).updateAiNovelChapter(captor.capture());
        assertEquals("", captor.getValue().getChapterBrief());
        assertEquals("删改后的新正文", captor.getValue().getContent());
        verify(contextTaskMapper).obsoleteActiveTasksByChapterId(
                eq(31L), eq("章节正文已变化，任务已过期"));
    }

    @Test
    void updateKeepsBriefWhenContentDidNotChange()
    {
        AiNovelChapter existing = chapter("相同正文", "仍然有效的摘要");
        when(chapterMapper.selectAiNovelChapterByChapterId(31L)).thenReturn(existing);
        when(chapterMapper.updateAiNovelChapter(any())).thenReturn(1);
        AiNovelChapter update = new AiNovelChapter();
        update.setChapterId(31L);
        update.setContent("相同正文");

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class))
        {
            security.when(SecurityUtils::getUsername).thenReturn("tester");
            service.updateAiNovelChapter(7L, update);
        }

        ArgumentCaptor<AiNovelChapter> captor = ArgumentCaptor.forClass(AiNovelChapter.class);
        verify(chapterMapper).updateAiNovelChapter(captor.capture());
        assertEquals("仍然有效的摘要", captor.getValue().getChapterBrief());
    }

    @Test
    void briefUpdateDelegatesToAtomicContentHashCondition()
    {
        String hash = "a".repeat(64);
        when(chapterMapper.updateChapterBriefIfContentHashMatches(
                eq(7L), eq(31L), eq(hash), eq("新的事实摘要"), eq("tester")))
                .thenReturn(1);

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class))
        {
            security.when(SecurityUtils::getUsername).thenReturn("tester");
            assertEquals(1, service.updateChapterBriefIfContentHashMatches(
                    7L, 31L, hash, "新的事实摘要"));
        }

        verify(chapterMapper).updateChapterBriefIfContentHashMatches(
                7L, 31L, hash, "新的事实摘要", "tester");
    }

    private static AiNovelChapter chapter(String content, String brief)
    {
        AiNovelChapter chapter = new AiNovelChapter();
        chapter.setChapterId(31L);
        chapter.setWorkId(7L);
        chapter.setChapterNo(3);
        chapter.setContent(content);
        chapter.setChapterBrief(brief);
        return chapter;
    }
}
