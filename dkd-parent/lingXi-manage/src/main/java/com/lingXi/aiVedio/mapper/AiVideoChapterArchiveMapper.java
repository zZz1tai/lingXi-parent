package com.lingXi.aiVedio.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.lingXi.aiVedio.domain.AiVideoAsset;
import com.lingXi.aiVedio.domain.AiVideoChapter;
import com.lingXi.aiVedio.domain.AiVideoGenerationTask;

/**
 * 章节删除归档所需的跨表锁定和批量状态迁移。
 *
 * 归档操作由章节服务统一包在一个事务内，避免留下仍可生成的章节孤儿数据。
 */
public interface AiVideoChapterArchiveMapper
{
    AiVideoChapter selectAiVideoChapterForUpdate(@Param("projectId") Long projectId,
            @Param("chapterId") Long chapterId);

    List<AiVideoChapter> selectAiVideoChaptersForUpdate(@Param("projectId") Long projectId,
            @Param("chapterIds") Long[] chapterIds);

    List<AiVideoGenerationTask> selectAiVideoChapterTasksForUpdate(@Param("projectId") Long projectId,
            @Param("chapterIds") Long[] chapterIds);

    List<AiVideoAsset> selectAiVideoChapterAssetsForUpdate(@Param("projectId") Long projectId,
            @Param("chapterIds") Long[] chapterIds);

    int archiveAiVideoStoryBiblesByChapterIds(@Param("projectId") Long projectId,
            @Param("chapterIds") Long[] chapterIds, @Param("updateBy") String updateBy);

    int archiveAiVideoShotsByChapterIds(@Param("projectId") Long projectId,
            @Param("chapterIds") Long[] chapterIds, @Param("updateBy") String updateBy);

    int archiveAiVideoScenesByChapterIds(@Param("projectId") Long projectId,
            @Param("chapterIds") Long[] chapterIds, @Param("updateBy") String updateBy);

    int archiveAiVideoAssetsByChapterIds(@Param("projectId") Long projectId,
            @Param("chapterIds") Long[] chapterIds, @Param("updateBy") String updateBy);

    int archiveAiVideoTasksByChapterIds(@Param("projectId") Long projectId,
            @Param("chapterIds") Long[] chapterIds, @Param("updateBy") String updateBy);
}
