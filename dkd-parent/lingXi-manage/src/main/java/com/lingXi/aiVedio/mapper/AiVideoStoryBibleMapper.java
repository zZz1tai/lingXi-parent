package com.lingXi.aiVedio.mapper;

import com.lingXi.aiVedio.domain.AiVideoStoryBible;

/**
 * AI视频故事圣经数据访问接口
 * <p>
 * 提供AI视频故事圣经数据的数据库操作方法，包括新增和查询最新版本等操作。
 * 故事圣经是章节解析后生成的结构化剧情数据。
 * </p>
 *
 * @author lingXi
 * @since 2026-07-23
 */
public interface AiVideoStoryBibleMapper
{
    /**
     * 新增AI视频故事圣经
     *
     * @param bible 故事圣经信息对象
     * @return 影响的行数
     */
    int insertAiVideoStoryBible(AiVideoStoryBible bible);

    /**
     * 根据章节ID查询最新的故事圣经版本
     *
     * @param chapterId 章节ID
     * @return 最新的故事圣经信息，不存在时返回null
     */
    AiVideoStoryBible selectLatestAiVideoStoryBibleByChapterId(Long chapterId);
}
