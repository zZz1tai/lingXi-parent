package com.lingXi.aiNovel.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.lingXi.aiNovel.domain.AiNovelSetting;

/**
 * AI 小说设定卡数据访问接口。
 */
public interface AiNovelSettingMapper
{
    /** 根据设定ID查询设定卡。 */
    AiNovelSetting selectAiNovelSettingBySettingId(Long settingId);

    /** 查询作品的设定卡列表，可按类型过滤。 */
    List<AiNovelSetting> selectAiNovelSettingList(
            @Param("workId") Long workId, @Param("settingType") String settingType);

    /** 查询注入智能体的作品上下文设定（限量、按更新时间倒序）。 */
    List<AiNovelSetting> selectAiNovelSettingContext(
            @Param("workId") Long workId, @Param("limit") int limit);

    /** 新增设定卡。 */
    int insertAiNovelSetting(AiNovelSetting setting);

    /** 更新设定卡。 */
    int updateAiNovelSetting(AiNovelSetting setting);

    /** 删除设定卡。 */
    int deleteAiNovelSettingBySettingId(Long settingId);

    /** 批量删除作品下的全部设定卡（删除作品时级联）。 */
    int deleteAiNovelSettingByWorkIds(@Param("workIds") Long[] workIds);
}
