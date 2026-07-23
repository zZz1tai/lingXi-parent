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
    /**
     * 锁定单个章节记录（悲观锁）用于归档
     *
     * @param projectId 项目ID
     * @param chapterId 章节ID
     * @return 锁定的章节信息
     */
    AiVideoChapter selectAiVideoChapterForUpdate(@Param("projectId") Long projectId,
            @Param("chapterId") Long chapterId);

    /**
     * 批量锁定章节记录（悲观锁）用于归档
     *
     * @param projectId  项目ID
     * @param chapterIds 章节ID数组
     * @return 锁定的章节列表
     */
    List<AiVideoChapter> selectAiVideoChaptersForUpdate(@Param("projectId") Long projectId,
            @Param("chapterIds") Long[] chapterIds);

    /**
     * 查询章节关联的生成任务（悲观锁）用于归档
     *
     * @param projectId  项目ID
     * @param chapterIds 章节ID数组
     * @return 关联的生成任务列表
     */
    List<AiVideoGenerationTask> selectAiVideoChapterTasksForUpdate(@Param("projectId") Long projectId,
            @Param("chapterIds") Long[] chapterIds);

    /**
     * 查询章节关联的资产（悲观锁）用于归档
     *
     * @param projectId  项目ID
     * @param chapterIds 章节ID数组
     * @return 关联的资产列表
     */
    List<AiVideoAsset> selectAiVideoChapterAssetsForUpdate(@Param("projectId") Long projectId,
            @Param("chapterIds") Long[] chapterIds);

    /**
     * 批量归档章节关联的故事圣经
     *
     * @param projectId  项目ID
     * @param chapterIds 章节ID数组
     * @param updateBy   操作人
     * @return 影响的行数
     */
    int archiveAiVideoStoryBiblesByChapterIds(@Param("projectId") Long projectId,
            @Param("chapterIds") Long[] chapterIds, @Param("updateBy") String updateBy);

    /**
     * 批量归档章节关联的镜头
     *
     * @param projectId  项目ID
     * @param chapterIds 章节ID数组
     * @param updateBy   操作人
     * @return 影响的行数
     */
    int archiveAiVideoShotsByChapterIds(@Param("projectId") Long projectId,
            @Param("chapterIds") Long[] chapterIds, @Param("updateBy") String updateBy);

    /**
     * 批量归档章节关联的场景
     *
     * @param projectId  项目ID
     * @param chapterIds 章节ID数组
     * @param updateBy   操作人
     * @return 影响的行数
     */
    int archiveAiVideoScenesByChapterIds(@Param("projectId") Long projectId,
            @Param("chapterIds") Long[] chapterIds, @Param("updateBy") String updateBy);

    /**
     * 批量归档章节关联的资产
     *
     * @param projectId  项目ID
     * @param chapterIds 章节ID数组
     * @param updateBy   操作人
     * @return 影响的行数
     */
    int archiveAiVideoAssetsByChapterIds(@Param("projectId") Long projectId,
            @Param("chapterIds") Long[] chapterIds, @Param("updateBy") String updateBy);

    /**
     * 批量归档章节关联的生成任务
     *
     * @param projectId  项目ID
     * @param chapterIds 章节ID数组
     * @param updateBy   操作人
     * @return 影响的行数
     */
    int archiveAiVideoTasksByChapterIds(@Param("projectId") Long projectId,
            @Param("chapterIds") Long[] chapterIds, @Param("updateBy") String updateBy);
}
