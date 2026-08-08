package com.lingXi.aiNovel.service;

import java.util.List;
import com.lingXi.aiNovel.domain.AiNovelSetting;

/**
 * AI 小说设定卡服务接口，所有操作按当前用户作品归属校验。
 */
public interface IAiNovelSettingService
{
    /** 查询作品的设定卡列表，可按类型过滤。 */
    List<AiNovelSetting> selectAiNovelSettingList(Long workId, String settingType);

    /** 新增设定卡。 */
    int insertAiNovelSetting(Long workId, AiNovelSetting setting);

    /** 更新设定卡。 */
    int updateAiNovelSetting(Long workId, AiNovelSetting setting);

    /** 删除设定卡。 */
    int deleteAiNovelSetting(Long workId, Long settingId);
}
