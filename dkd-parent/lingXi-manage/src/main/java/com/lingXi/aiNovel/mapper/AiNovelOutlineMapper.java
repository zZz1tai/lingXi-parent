package com.lingXi.aiNovel.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.lingXi.aiNovel.domain.AiNovelOutline;

/**
 * AI 小说三层大纲数据访问接口。
 */
public interface AiNovelOutlineMapper
{
    /** 根据大纲ID查询。 */
    AiNovelOutline selectAiNovelOutlineByOutlineId(Long outlineId);

    /** 查询作品的全部大纲节点（按层级与排序）。 */
    List<AiNovelOutline> selectAiNovelOutlineListByWorkId(Long workId);

    /** 查询作品指定层级的全部大纲节点（按排序）。 */
    List<AiNovelOutline> selectAiNovelOutlineListByWorkAndLevel(
            @Param("workId") Long workId, @Param("outlineLevel") String outlineLevel);

    /** 查询父级下的全部大纲节点。 */
    List<AiNovelOutline> selectAiNovelOutlineListByParentId(
            @Param("workId") Long workId, @Param("parentId") Long parentId);

    /** 新增大纲节点。 */
    int insertAiNovelOutline(AiNovelOutline outline);

    /** 更新大纲节点（按层级加锁校验）。 */
    int updateAiNovelOutline(AiNovelOutline outline);

    /** 按给定顺序批量更新同级排序。 */
    int updateOutlineSeqNo(
            @Param("outlineId") Long outlineId, @Param("seqNo") int seqNo,
            @Param("workId") Long workId);

    /** 删除大纲节点（软删）。 */
    int deleteAiNovelOutlineByOutlineId(
            @Param("outlineId") Long outlineId, @Param("workId") Long workId);

    /** 批量删除作品下的全部大纲（删除作品时级联）。 */
    int deleteAiNovelOutlineByWorkIds(@Param("workIds") Long[] workIds);
}
