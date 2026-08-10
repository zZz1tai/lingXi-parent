package com.lingXi.aiNovel.service.impl;

import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.lingXi.common.exception.ServiceException;
import com.lingXi.common.utils.DateUtils;
import com.lingXi.common.utils.SecurityUtils;
import com.lingXi.common.utils.StringUtils;
import com.lingXi.aiNovel.domain.AiNovelForeshadow;
import com.lingXi.aiNovel.mapper.AiNovelForeshadowMapper;
import com.lingXi.aiNovel.service.IAiNovelForeshadowService;
import com.lingXi.aiNovel.service.IAiNovelWorkService;

/**
 * AI 小说伏笔服务实现类，所有操作按当前用户作品归属校验。
 */
@Service
public class AiNovelForeshadowServiceImpl implements IAiNovelForeshadowService
{
    /** 允许的伏笔状态白名单（与前端及 Python 契约一致）。 */
    private static final Set<String> STATUSES = Set.of("buried", "pending", "resolved");

    /** 允许的重要等级白名单（与前端及 Python 契约一致）。 */
    private static final Set<String> PRIORITIES = Set.of("high", "medium", "low");

    @Autowired
    private AiNovelForeshadowMapper foreshadowMapper;

    @Autowired
    private IAiNovelWorkService workService;

    /** 查询作品的伏笔列表，可按状态过滤。 */
    @Override
    public List<AiNovelForeshadow> selectAiNovelForeshadowList(Long workId, String status)
    {
        workService.checkWorkOwner(workId);
        return foreshadowMapper.selectAiNovelForeshadowList(workId, status);
    }

    /** 新增伏笔。 */
    @Override
    public int insertAiNovelForeshadow(Long workId, AiNovelForeshadow foreshadow)
    {
        workService.checkWorkOwner(workId);
        validateForeshadow(foreshadow);
        foreshadow.setForeshadowId(null);
        foreshadow.setWorkId(workId);
        foreshadow.setCreateBy(SecurityUtils.getUsername());
        foreshadow.setCreateTime(DateUtils.getNowDate());
        return foreshadowMapper.insertAiNovelForeshadow(foreshadow);
    }

    /** 更新伏笔。 */
    @Override
    public int updateAiNovelForeshadow(Long workId, AiNovelForeshadow foreshadow)
    {
        if (foreshadow.getForeshadowId() == null)
        {
            throw new ServiceException("伏笔ID不能为空");
        }
        AiNovelForeshadow existing = requireForeshadow(foreshadow.getForeshadowId());
        if (!existing.getWorkId().equals(workId))
        {
            throw new ServiceException("伏笔不存在或无权访问");
        }
        validateForeshadow(foreshadow);
        existing.setTitle(foreshadow.getTitle());
        existing.setDescription(foreshadow.getDescription());
        existing.setStatus(foreshadow.getStatus());
        existing.setPriority(foreshadow.getPriority());
        existing.setKeyword(foreshadow.getKeyword());
        existing.setResolveChapterNo(foreshadow.getResolveChapterNo());
        existing.setUpdateBy(SecurityUtils.getUsername());
        existing.setUpdateTime(DateUtils.getNowDate());
        return foreshadowMapper.updateAiNovelForeshadow(existing);
    }

    /** 删除伏笔。 */
    @Override
    public int deleteAiNovelForeshadow(Long workId, Long foreshadowId)
    {
        AiNovelForeshadow existing = requireForeshadow(foreshadowId);
        if (!existing.getWorkId().equals(workId))
        {
            throw new ServiceException("伏笔不存在或无权访问");
        }
        return foreshadowMapper.deleteAiNovelForeshadowByForeshadowId(foreshadowId);
    }

    private AiNovelForeshadow requireForeshadow(Long foreshadowId)
    {
        if (foreshadowId == null)
        {
            throw new ServiceException("伏笔ID不能为空");
        }
        AiNovelForeshadow foreshadow =
                foreshadowMapper.selectAiNovelForeshadowByForeshadowId(foreshadowId);
        if (foreshadow == null)
        {
            throw new ServiceException("伏笔不存在或无权访问");
        }
        return foreshadow;
    }

    private static void validateForeshadow(AiNovelForeshadow foreshadow)
    {
        if (StringUtils.isBlank(foreshadow.getTitle()))
        {
            throw new ServiceException("伏笔名称不能为空");
        }
        if (foreshadow.getTitle().length() > 128)
        {
            throw new ServiceException("伏笔名称不能超过128个字符");
        }
        if (foreshadow.getStatus() != null && !STATUSES.contains(foreshadow.getStatus()))
        {
            throw new ServiceException("伏笔状态无效");
        }
        if (foreshadow.getPriority() != null && !PRIORITIES.contains(foreshadow.getPriority()))
        {
            throw new ServiceException("伏笔等级无效");
        }
        if (foreshadow.getKeyword() != null && foreshadow.getKeyword().length() > 128)
        {
            throw new ServiceException("伏笔关键词不能超过128个字符");
        }
        if (foreshadow.getDescription() != null && foreshadow.getDescription().length() > 4_000)
        {
            throw new ServiceException("伏笔详情不能超过4000个字符");
        }
        if (foreshadow.getResolveChapterNo() != null && foreshadow.getResolveChapterNo() < 1)
        {
            throw new ServiceException("计划回收章节号必须为正整数");
        }
    }
}
