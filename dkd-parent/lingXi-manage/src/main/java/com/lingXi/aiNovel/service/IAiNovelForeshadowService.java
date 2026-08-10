package com.lingXi.aiNovel.service;

import java.util.List;
import com.lingXi.aiNovel.domain.AiNovelForeshadow;

/**
 * AI 小说伏笔服务接口，所有操作按当前用户作品归属校验。
 */
public interface IAiNovelForeshadowService
{
    /** 查询作品的伏笔列表，可按状态过滤。 */
    List<AiNovelForeshadow> selectAiNovelForeshadowList(Long workId, String status);

    /** 新增伏笔。 */
    int insertAiNovelForeshadow(Long workId, AiNovelForeshadow foreshadow);

    /** 更新伏笔。 */
    int updateAiNovelForeshadow(Long workId, AiNovelForeshadow foreshadow);

    /** 删除伏笔。 */
    int deleteAiNovelForeshadow(Long workId, Long foreshadowId);
}
