package com.lingXi.aiNovel.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.lingXi.aiNovel.domain.AiNovelWork;

/**
 * AI 小说作品数据访问接口。
 */
public interface AiNovelWorkMapper
{
    /** 根据作品ID查询作品。 */
    AiNovelWork selectAiNovelWorkByWorkId(Long workId);

    /** 查询作品列表（按当前用户过滤）。 */
    List<AiNovelWork> selectAiNovelWorkList(AiNovelWork work);

    /** 新增作品。 */
    int insertAiNovelWork(AiNovelWork work);

    /** 更新作品。 */
    int updateAiNovelWork(AiNovelWork work);

    /** 批量删除当前用户的作品。 */
    int deleteAiNovelWorkByWorkIds(
            @Param("workIds") Long[] workIds, @Param("ownerUserId") Long ownerUserId);
}
