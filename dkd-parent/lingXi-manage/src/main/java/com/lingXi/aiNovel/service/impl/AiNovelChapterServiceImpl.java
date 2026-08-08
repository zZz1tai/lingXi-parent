package com.lingXi.aiNovel.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.lingXi.common.exception.ServiceException;
import com.lingXi.common.utils.DateUtils;
import com.lingXi.common.utils.SecurityUtils;
import com.lingXi.common.utils.StringUtils;
import com.lingXi.aiNovel.domain.AiNovelChapter;
import com.lingXi.aiNovel.domain.AiNovelWork;
import com.lingXi.aiNovel.mapper.AiNovelChapterMapper;
import com.lingXi.aiNovel.service.IAiNovelChapterService;
import com.lingXi.aiNovel.service.IAiNovelWorkService;

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

    /** 查询作品的章节列表。 */
    @Override
    public List<AiNovelChapter> selectAiNovelChapterList(Long workId)
    {
        workService.checkWorkOwner(workId);
        return chapterMapper.selectAiNovelChapterListByWorkId(workId);
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
        if (chapter.getWordCount() == null)
        {
            chapter.setWordCount(0);
        }
        chapter.setChapterId(null);
        chapter.setWorkId(workId);
        chapter.setCreateBy(SecurityUtils.getUsername());
        chapter.setCreateTime(DateUtils.getNowDate());
        return chapterMapper.insertAiNovelChapter(chapter);
    }

    /** 更新章节。 */
    @Override
    public int updateAiNovelChapter(Long workId, AiNovelChapter chapter)
    {
        AiNovelChapter existing = selectAiNovelChapterByChapterId(workId, chapter.getChapterId());
        if (chapter.getChapterTitle() != null)
        {
            existing.setChapterTitle(chapter.getChapterTitle());
        }
        if (chapter.getChapterBrief() != null)
        {
            existing.setChapterBrief(chapter.getChapterBrief());
        }
        if (chapter.getContent() != null)
        {
            existing.setContent(chapter.getContent());
        }
        if (chapter.getWordCount() != null)
        {
            existing.setWordCount(chapter.getWordCount());
        }
        if (chapter.getStatus() != null)
        {
            existing.setStatus(chapter.getStatus());
        }
        existing.setUpdateBy(SecurityUtils.getUsername());
        existing.setUpdateTime(DateUtils.getNowDate());
        return chapterMapper.updateAiNovelChapter(existing);
    }

    /** 删除章节。 */
    @Override
    public int deleteAiNovelChapter(Long workId, Long chapterId)
    {
        selectAiNovelChapterByChapterId(workId, chapterId);
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
}
