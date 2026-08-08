package com.lingXi.aiNovel.service.impl;

import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.lingXi.common.exception.ServiceException;
import com.lingXi.common.utils.DateUtils;
import com.lingXi.common.utils.SecurityUtils;
import com.lingXi.common.utils.StringUtils;
import com.lingXi.aiNovel.domain.AiNovelSetting;
import com.lingXi.aiNovel.mapper.AiNovelSettingMapper;
import com.lingXi.aiNovel.service.IAiNovelSettingService;
import com.lingXi.aiNovel.service.IAiNovelWorkService;

/**
 * AI 小说设定卡服务实现类，所有操作按当前用户作品归属校验。
 */
@Service
public class AiNovelSettingServiceImpl implements IAiNovelSettingService
{
    /** 允许的设定类型白名单（与前端及 Python 契约一致）。 */
    private static final Set<String> SETTING_TYPES = Set.of(
            "character", "world", "outline", "item", "organization", "event", "style", "other");

    @Autowired
    private AiNovelSettingMapper settingMapper;

    @Autowired
    private IAiNovelWorkService workService;

    /** 查询作品的设定卡列表，可按类型过滤。 */
    @Override
    public List<AiNovelSetting> selectAiNovelSettingList(Long workId, String settingType)
    {
        workService.checkWorkOwner(workId);
        return settingMapper.selectAiNovelSettingList(workId, settingType);
    }

    /** 新增设定卡。 */
    @Override
    public int insertAiNovelSetting(Long workId, AiNovelSetting setting)
    {
        workService.checkWorkOwner(workId);
        validateSetting(setting);
        setting.setSettingId(null);
        setting.setWorkId(workId);
        setting.setCreateBy(SecurityUtils.getUsername());
        setting.setCreateTime(DateUtils.getNowDate());
        return settingMapper.insertAiNovelSetting(setting);
    }

    /** 更新设定卡。 */
    @Override
    public int updateAiNovelSetting(Long workId, AiNovelSetting setting)
    {
        if (setting.getSettingId() == null)
        {
            throw new ServiceException("设定ID不能为空");
        }
        AiNovelSetting existing = requireSetting(setting.getSettingId());
        if (!existing.getWorkId().equals(workId))
        {
            throw new ServiceException("设定不存在或无权访问");
        }
        validateSetting(setting);
        existing.setSettingType(setting.getSettingType());
        existing.setTitle(setting.getTitle());
        existing.setContent(setting.getContent());
        existing.setUpdateBy(SecurityUtils.getUsername());
        existing.setUpdateTime(DateUtils.getNowDate());
        return settingMapper.updateAiNovelSetting(existing);
    }

    /** 删除设定卡。 */
    @Override
    public int deleteAiNovelSetting(Long workId, Long settingId)
    {
        AiNovelSetting existing = requireSetting(settingId);
        if (!existing.getWorkId().equals(workId))
        {
            throw new ServiceException("设定不存在或无权访问");
        }
        return settingMapper.deleteAiNovelSettingBySettingId(settingId);
    }

    private AiNovelSetting requireSetting(Long settingId)
    {
        if (settingId == null)
        {
            throw new ServiceException("设定ID不能为空");
        }
        AiNovelSetting setting = settingMapper.selectAiNovelSettingBySettingId(settingId);
        if (setting == null)
        {
            throw new ServiceException("设定不存在或无权访问");
        }
        return setting;
    }

    private static void validateSetting(AiNovelSetting setting)
    {
        if (setting.getSettingType() == null
                || !SETTING_TYPES.contains(setting.getSettingType()))
        {
            throw new ServiceException("设定类型无效");
        }
        if (StringUtils.isBlank(setting.getTitle()))
        {
            throw new ServiceException("设定标题不能为空");
        }
        if (setting.getTitle().length() > 128)
        {
            throw new ServiceException("设定标题不能超过128个字符");
        }
        if (setting.getContent() != null && setting.getContent().length() > 4_000)
        {
            throw new ServiceException("设定内容不能超过4000个字符");
        }
    }
}
