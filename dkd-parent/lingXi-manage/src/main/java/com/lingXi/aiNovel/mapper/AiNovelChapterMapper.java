package com.lingXi.aiNovel.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.lingXi.aiNovel.domain.AiNovelChapter;

/**
 * AI 小说章节数据访问接口。
 */
public interface AiNovelChapterMapper
{
    /** 根据章节ID查询章节。 */
    AiNovelChapter selectAiNovelChapterByChapterId(Long chapterId);

    /** 查询作品的章节列表（按章节序号排序）。 */
    List<AiNovelChapter> selectAiNovelChapterListByWorkId(Long workId);

    /** 新增章节。 */
    int insertAiNovelChapter(AiNovelChapter chapter);

    /** 更新章节。 */
    int updateAiNovelChapter(AiNovelChapter chapter);

    /** 正文哈希匹配时原子更新本章事实摘要。 */
    int updateChapterBriefIfContentHashMatches(
            @Param("workId") Long workId,
            @Param("chapterId") Long chapterId,
            @Param("expectedContentHash") String expectedContentHash,
            @Param("chapterBrief") String chapterBrief,
            @Param("updateBy") String updateBy);

    /** 删除章节。 */
    int deleteAiNovelChapterByChapterId(Long chapterId);

    /** 批量删除作品下的全部章节（删除作品时级联）。 */
    int deleteAiNovelChapterByWorkIds(@Param("workIds") Long[] workIds);

    /** 按给定顺序批量更新章节序号。 */
    int updateChapterNo(
            @Param("chapterId") Long chapterId, @Param("chapterNo") int chapterNo,
            @Param("workId") Long workId);
}
