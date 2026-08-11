package com.lingXi.aiNovel.service;

import java.util.List;
import com.lingXi.aiNovel.domain.AiNovelOutline;
import com.lingXi.aiNovel.domain.dto.NovelOutlineGeneratedDTO;

/**
 * AI 小说三层大纲服务接口，所有操作按当前用户作品归属校验。
 */
public interface IAiNovelOutlineService
{
    /** 查询作品的大纲节点平铺列表（按层级与排序，可用于前端组树）。 */
    List<AiNovelOutline> selectAiNovelOutlineList(Long workId);

    /** 新增大纲节点。 */
    int insertAiNovelOutline(Long workId, AiNovelOutline outline);

    /** 更新大纲节点。 */
    int updateAiNovelOutline(Long workId, AiNovelOutline outline);

    /** 删除大纲节点，级联删除其子节点（卷删除时章一并删除）。 */
    int deleteAiNovelOutline(Long workId, Long outlineId);

    /** 按给定顺序重排同一父级下的大纲节点。 */
    int sortAiNovelOutline(Long workId, Long parentId, List<Long> outlineIds);

    /** 调用 AI 生成三层大纲并全量保存，返回新大纲与断链报告。 */
    NovelOutlineGeneratedDTO generateOutline(Long workId);
}
