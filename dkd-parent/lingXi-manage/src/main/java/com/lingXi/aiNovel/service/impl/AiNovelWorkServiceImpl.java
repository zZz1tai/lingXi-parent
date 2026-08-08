package com.lingXi.aiNovel.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.lingXi.common.exception.ServiceException;
import com.lingXi.common.utils.DateUtils;
import com.lingXi.common.utils.SecurityUtils;
import com.lingXi.common.utils.StringUtils;
import com.lingXi.aiNovel.domain.AiNovelChapter;
import com.lingXi.aiNovel.domain.AiNovelSetting;
import com.lingXi.aiNovel.domain.AiNovelWork;
import com.lingXi.aiNovel.domain.dto.NovelSettingItemDTO;
import com.lingXi.aiNovel.domain.dto.NovelWorkContextDTO;
import com.lingXi.aiNovel.mapper.AiNovelChapterMapper;
import com.lingXi.aiNovel.mapper.AiNovelSettingMapper;
import com.lingXi.aiNovel.mapper.AiNovelWorkMapper;
import com.lingXi.aiNovel.service.IAiNovelWorkService;

/**
 * AI 小说作品服务实现类。
 * <p>负责作品增删改查、归属校验，以及组装提交给创作智能体的作品上下文。</p>
 */
@Service
public class AiNovelWorkServiceImpl implements IAiNovelWorkService
{
    /** 注入智能体的设定卡数量上限（与 Python NovelWorkContext 契约一致）。 */
    private static final int CONTEXT_SETTING_LIMIT = 40;
    /** 注入智能体的正文末尾片段长度上限（字符）。 */
    private static final int CONTEXT_MANUSCRIPT_TAIL_CHARS = 3_000;
    /** 单条设定卡内容长度上限（与 Python 契约一致）。 */
    private static final int SETTING_CONTENT_MAX_CHARS = 4_000;

    @Autowired
    private AiNovelWorkMapper workMapper;

    @Autowired
    private AiNovelChapterMapper chapterMapper;

    @Autowired
    private AiNovelSettingMapper settingMapper;

    /** 根据作品ID查询作品，并校验当前用户为作品所有者。 */
    @Override
    public AiNovelWork selectAiNovelWorkByWorkId(Long workId)
    {
        return checkWorkOwner(workId);
    }

    /** 查询当前用户拥有的作品列表。 */
    @Override
    public List<AiNovelWork> selectAiNovelWorkList(AiNovelWork work)
    {
        work.setOwnerUserId(SecurityUtils.getUserId());
        List<AiNovelWork> list = workMapper.selectAiNovelWorkList(work);
        for (AiNovelWork item : list)
        {
            item.setWordCount(calculateWordCount(item));
        }
        return list;
    }

    /** 统计作品总字数：短篇取正文长度，长篇累加各章节正文字符数。 */
    private long calculateWordCount(AiNovelWork work)
    {
        if ("novel".equals(work.getWorkType()))
        {
            long total = 0L;
            for (AiNovelChapter chapter : chapterMapper.selectAiNovelChapterListByWorkId(work.getWorkId()))
            {
                if (chapter.getContent() != null)
                {
                    total += chapter.getContent().length();
                }
            }
            return total;
        }
        return work.getManuscript() == null ? 0L : work.getManuscript().length();
    }

    /** 新增作品，设置默认值并校验参数。 */
    @Override
    public int insertAiNovelWork(AiNovelWork work)
    {
        validateWork(work);
        work.setWorkId(null);
        work.setOwnerUserId(SecurityUtils.getUserId());
        if (StringUtils.isBlank(work.getStatus()))
        {
            work.setStatus("draft");
        }
        if (StringUtils.isBlank(work.getWorkType()))
        {
            work.setWorkType("short");
        }
        work.setCreateBy(SecurityUtils.getUsername());
        work.setCreateTime(DateUtils.getNowDate());
        return workMapper.insertAiNovelWork(work);
    }

    /** 更新作品信息，校验作品所有权。 */
    @Override
    public int updateAiNovelWork(AiNovelWork work)
    {
        if (work.getWorkId() == null)
        {
            throw new ServiceException("作品ID不能为空");
        }
        AiNovelWork existing = checkWorkOwner(work.getWorkId());
        existing.setWorkName(work.getWorkName());
        existing.setWorkType(work.getWorkType());
        existing.setGenre(work.getGenre());
        existing.setSynopsis(work.getSynopsis());
        existing.setStatus(work.getStatus());
        existing.setUpdateBy(SecurityUtils.getUsername());
        existing.setUpdateTime(DateUtils.getNowDate());
        return workMapper.updateAiNovelWork(existing);
    }

    /** 更新作品正文（短篇）。 */
    @Override
    public int updateAiNovelWorkManuscript(Long workId, String content)
    {
        checkWorkOwner(workId);
        AiNovelWork update = new AiNovelWork();
        update.setWorkId(workId);
        update.setManuscript(content == null ? "" : content);
        update.setUpdateBy(SecurityUtils.getUsername());
        update.setUpdateTime(DateUtils.getNowDate());
        return workMapper.updateAiNovelWork(update);
    }

    /** 批量删除作品及其章节、设定卡。 */
    @Override
    public int deleteAiNovelWorkByWorkIds(Long[] workIds)
    {
        if (workIds == null || workIds.length == 0)
        {
            throw new ServiceException("请选择需要删除的作品");
        }
        Long[] owned = Arrays.stream(workIds)
                .filter(workId -> workMapper.selectAiNovelWorkByWorkId(workId) != null
                        && SecurityUtils.getUserId().equals(
                                workMapper.selectAiNovelWorkByWorkId(workId).getOwnerUserId()))
                .toArray(Long[]::new);
        if (owned.length == 0)
        {
            return 0;
        }
        chapterMapper.deleteAiNovelChapterByWorkIds(owned);
        settingMapper.deleteAiNovelSettingByWorkIds(owned);
        return workMapper.deleteAiNovelWorkByWorkIds(owned, SecurityUtils.getUserId());
    }

    /** 校验当前用户是否为作品所有者，不存在或无权时抛出异常。 */
    @Override
    public AiNovelWork checkWorkOwner(Long workId)
    {
        if (workId == null)
        {
            throw new ServiceException("作品ID不能为空");
        }
        AiNovelWork work = workMapper.selectAiNovelWorkByWorkId(workId);
        if (work == null || work.getOwnerUserId() == null
                || !work.getOwnerUserId().equals(SecurityUtils.getUserId()))
        {
            // 统一返回无权访问，避免泄露其他用户的作品是否存在。
            throw new ServiceException("作品不存在或无权访问");
        }
        return work;
    }

    /** 组装提交给创作智能体的作品上下文。 */
    @Override
    public NovelWorkContextDTO buildNovelWorkContext(Long workId, Long chapterId)
    {
        AiNovelWork work = checkWorkOwner(workId);
        NovelWorkContextDTO context = new NovelWorkContextDTO();
        context.setWorkId(work.getWorkId());
        context.setWorkName(work.getWorkName());
        context.setWorkType(work.getWorkType());
        context.setGenre(work.getGenre());
        context.setSynopsis(work.getSynopsis());

        AiNovelChapter chapter = null;
        if (chapterId != null)
        {
            chapter = chapterMapper.selectAiNovelChapterByChapterId(chapterId);
            if (chapter != null && !chapter.getWorkId().equals(workId))
            {
                chapter = null;
            }
        }
        if (chapter != null)
        {
            context.setChapterTitle(chapter.getChapterTitle());
            context.setChapterSynopsis(chapter.getChapterBrief());
            context.setManuscriptTail(manuscriptTail(chapter.getContent()));
        }
        else
        {
            context.setManuscriptTail(manuscriptTail(work.getManuscript()));
        }

        List<NovelSettingItemDTO> settings = new ArrayList<>();
        for (AiNovelSetting setting : settingMapper.selectAiNovelSettingContext(
                workId, CONTEXT_SETTING_LIMIT))
        {
            NovelSettingItemDTO item = new NovelSettingItemDTO();
            item.setSettingType(setting.getSettingType());
            item.setTitle(setting.getTitle());
            item.setContent(truncate(setting.getContent(), SETTING_CONTENT_MAX_CHARS));
            settings.add(item);
        }
        context.setSettings(settings);
        return context;
    }

    /** 截取正文末尾片段，用于无缝续写。 */
    private static String manuscriptTail(String content)
    {
        if (StringUtils.isBlank(content))
        {
            return null;
        }
        String normalized = content.trim();
        if (normalized.length() <= CONTEXT_MANUSCRIPT_TAIL_CHARS)
        {
            return normalized;
        }
        return normalized.substring(normalized.length() - CONTEXT_MANUSCRIPT_TAIL_CHARS);
    }

    private static String truncate(String value, int maxChars)
    {
        if (value == null)
        {
            return null;
        }
        return value.length() <= maxChars ? value : value.substring(0, maxChars);
    }

    private static void validateWork(AiNovelWork work)
    {
        if (StringUtils.isBlank(work.getWorkName()))
        {
            throw new ServiceException("作品名称不能为空");
        }
        if (work.getWorkName().length() > 128)
        {
            throw new ServiceException("作品名称不能超过128个字符");
        }
        if (!"short".equals(work.getWorkType()) && !"novel".equals(work.getWorkType()))
        {
            throw new ServiceException("作品类型无效");
        }
    }
}
