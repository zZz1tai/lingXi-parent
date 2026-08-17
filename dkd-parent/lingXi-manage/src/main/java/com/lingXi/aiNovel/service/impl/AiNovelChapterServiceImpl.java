package com.lingXi.aiNovel.service.impl;

import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.lingXi.common.exception.ServiceException;
import com.lingXi.common.utils.DateUtils;
import com.lingXi.common.utils.SecurityUtils;
import com.lingXi.common.utils.StringUtils;
import com.lingXi.aiNovel.domain.AiNovelChapter;
import com.lingXi.aiNovel.domain.AiNovelWork;
import com.lingXi.aiNovel.mapper.AiNovelChapterMapper;
import com.lingXi.aiNovel.mapper.AiNovelContextTaskMapper;
import com.lingXi.aiNovel.service.IAiNovelChapterService;
import com.lingXi.aiNovel.service.IAiNovelWorkService;
import com.lingXi.aiNovel.util.NovelWordCounter;

/**
 * AI 小说章节服务实现类，所有操作按当前用户作品归属校验。
 */
@Service
public class AiNovelChapterServiceImpl implements IAiNovelChapterService
{
    @Autowired
    private AiNovelChapterMapper chapterMapper;

    @Autowired
    private IAiNovelWorkService workService;

    @Autowired
    private AiNovelContextTaskMapper contextTaskMapper;

    /** 查询作品的章节列表。 */
    @Override
    public List<AiNovelChapter> selectAiNovelChapterList(Long workId)
    {
        workService.checkWorkOwner(workId);
        List<AiNovelChapter> chapters = chapterMapper.selectAiNovelChapterListByWorkId(workId);
        chapters.forEach(this::refreshWordCount);
        return chapters;
    }

    /** 查询章节详情，校验章节归属。 */
    @Override
    public AiNovelChapter selectAiNovelChapterByChapterId(Long workId, Long chapterId)
    {
        workService.checkWorkOwner(workId);
        AiNovelChapter chapter = requireChapter(chapterId);
        if (!chapter.getWorkId().equals(workId))
        {
            throw new ServiceException("章节不存在或无权访问");
        }
        refreshWordCount(chapter);
        return chapter;
    }

    /** 新增章节，自动补全序号与归属。 */
    @Override
    public int insertAiNovelChapter(Long workId, AiNovelChapter chapter)
    {
        workService.checkWorkOwner(workId);
        if (chapter.getChapterNo() == null || chapter.getChapterNo() < 1)
        {
            List<AiNovelChapter> existing = chapterMapper.selectAiNovelChapterListByWorkId(workId);
            chapter.setChapterNo(existing.stream()
                    .mapToInt(AiNovelChapter::getChapterNo)
                    .max()
                    .orElse(0) + 1);
        }
        if (StringUtils.isBlank(chapter.getStatus()))
        {
            chapter.setStatus("draft");
        }
        chapter.setWordCount(NovelWordCounter.count(chapter.getContent()));
        chapter.setChapterId(null);
        chapter.setWorkId(workId);
        chapter.setCreateBy(SecurityUtils.getUsername());
        chapter.setCreateTime(DateUtils.getNowDate());
        return chapterMapper.insertAiNovelChapter(chapter);
    }

    /** 更新章节。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateAiNovelChapter(Long workId, AiNovelChapter chapter)
    {
        AiNovelChapter existing = selectAiNovelChapterByChapterId(workId, chapter.getChapterId());
        boolean contentChanged = chapter.getContent() != null
                && !Objects.equals(existing.getContent(), chapter.getContent());
        if (chapter.getChapterTitle() != null)
        {
            existing.setChapterTitle(chapter.getChapterTitle());
        }
        if (chapter.getChapterBrief() != null)
        {
            existing.setChapterBrief(chapter.getChapterBrief());
        }
        else if (contentChanged)
        {
            // 本章摘要描述的是已发生事实；正文改变后必须先失效，避免旧剧情进入续写上下文。
            existing.setChapterBrief("");
        }
        if (chapter.getContent() != null)
        {
            existing.setContent(chapter.getContent());
            existing.setWordCount(NovelWordCounter.count(chapter.getContent()));
        }
        if (chapter.getStatus() != null)
        {
            existing.setStatus(chapter.getStatus());
        }
        existing.setUpdateBy(SecurityUtils.getUsername());
        existing.setUpdateTime(DateUtils.getNowDate());
        int affected = chapterMapper.updateAiNovelChapter(existing);
        if (affected == 1 && contentChanged)
        {
            contextTaskMapper.obsoleteActiveTasksByChapterId(
                    existing.getChapterId(), "章节正文已变化，任务已过期");
        }
        return affected;
    }

    /** 由资料分析使用的条件写入；正文变化或章节删除后更新自然失败。 */
    @Override
    public int updateChapterBriefIfContentHashMatches(
            Long workId, Long chapterId, String expectedContentHash, String chapterBrief)
    {
        workService.checkWorkOwner(workId);
        if (chapterId == null || StringUtils.isBlank(chapterBrief)
                || expectedContentHash == null || expectedContentHash.length() != 64)
        {
            throw new ServiceException("章节摘要或正文版本无效");
        }
        return chapterMapper.updateChapterBriefIfContentHashMatches(
                workId, chapterId, expectedContentHash, chapterBrief,
                SecurityUtils.getUsername());
    }

    /** 删除章节。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteAiNovelChapter(Long workId, Long chapterId)
    {
        selectAiNovelChapterByChapterId(workId, chapterId);
        contextTaskMapper.obsoleteActiveTasksByChapterId(chapterId, "章节已删除，任务已取消");
        return chapterMapper.deleteAiNovelChapterByChapterId(chapterId);
    }

    /** 按给定顺序重排章节序号。 */
    @Override
    public int sortAiNovelChapter(Long workId, List<Long> chapterIds)
    {
        workService.checkWorkOwner(workId);
        if (chapterIds == null || chapterIds.isEmpty())
        {
            throw new ServiceException("章节顺序不能为空");
        }
        int result = 0;
        int no = 1;
        for (Long chapterId : chapterIds)
        {
            AiNovelChapter chapter = requireChapter(chapterId);
            if (!chapter.getWorkId().equals(workId))
            {
                throw new ServiceException("章节不存在或无权访问");
            }
            result += chapterMapper.updateChapterNo(chapterId, no++, workId);
        }
        return result;
    }

    private AiNovelChapter requireChapter(Long chapterId)
    {
        if (chapterId == null)
        {
            throw new ServiceException("章节ID不能为空");
        }
        AiNovelChapter chapter = chapterMapper.selectAiNovelChapterByChapterId(chapterId);
        if (chapter == null)
        {
            throw new ServiceException("章节不存在或无权访问");
        }
        return chapter;
    }

    private void refreshWordCount(AiNovelChapter chapter)
    {
        chapter.setWordCount(NovelWordCounter.count(chapter.getContent()));
    }
}
