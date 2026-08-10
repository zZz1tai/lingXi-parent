package com.lingXi.aiNovel.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.lingXi.aiNovel.domain.AiNovelForeshadow;

/**
 * AI 小说伏笔数据访问接口。
 */
public interface AiNovelForeshadowMapper
{
    /** 根据伏笔ID查询伏笔。 */
    AiNovelForeshadow selectAiNovelForeshadowByForeshadowId(Long foreshadowId);

    /** 查询作品的伏笔列表，可按状态过滤。 */
    List<AiNovelForeshadow> selectAiNovelForeshadowList(
            @Param("workId") Long workId, @Param("status") String status);

    /** 查询注入智能体的未解伏笔（限量、按更新时间倒序）。 */
    List<AiNovelForeshadow> selectAiNovelForeshadowContext(
            @Param("workId") Long workId, @Param("limit") int limit);

    /** 新增伏笔。 */
    int insertAiNovelForeshadow(AiNovelForeshadow foreshadow);

    /** 更新伏笔。 */
    int updateAiNovelForeshadow(AiNovelForeshadow foreshadow);

    /** 删除伏笔。 */
    int deleteAiNovelForeshadowByForeshadowId(Long foreshadowId);

    /** 批量删除作品下的全部伏笔（删除作品时级联）。 */
    int deleteAiNovelForeshadowByWorkIds(@Param("workIds") Long[] workIds);
}
