package com.lingXi.aiNovel.service;

import java.util.List;
import com.lingXi.aiNovel.domain.AiNovelChapter;

/**
 * AI 小说章节服务接口，所有操作按当前用户作品归属校验。
 */
public interface IAiNovelChapterService
{
    /** 查询作品的章节列表。 */
    List<AiNovelChapter> selectAiNovelChapterList(Long workId);

    /** 查询章节详情，校验章节归属。 */
    AiNovelChapter selectAiNovelChapterByChapterId(Long workId, Long chapterId);

    /** 新增章节，自动补全序号与归属。 */
    int insertAiNovelChapter(Long workId, AiNovelChapter chapter);

    /** 更新章节。 */
    int updateAiNovelChapter(Long workId, AiNovelChapter chapter);

    /** 仅在正文哈希仍匹配时更新本章事实摘要，防止过期分析覆盖新正文。 */
    int updateChapterBriefIfContentHashMatches(
            Long workId, Long chapterId, String expectedContentHash, String chapterBrief);

    /** 删除章节。 */
    int deleteAiNovelChapter(Long workId, Long chapterId);

    /** 按给定顺序重排章节序号。 */
    int sortAiNovelChapter(Long workId, List<Long> chapterIds);
}
