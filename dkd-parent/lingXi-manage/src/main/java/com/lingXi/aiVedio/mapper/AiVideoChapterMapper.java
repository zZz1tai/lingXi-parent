package com.lingXi.aiVedio.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.lingXi.aiVedio.domain.AiVideoChapter;

/**
 * AI视频章节数据访问接口
 * <p>
 * 提供AI视频章节数据的数据库操作方法，包括查询、新增、更新和删除等基本操作。
 * </p>
 *
 * @author lingXi
 * @since 2026-07-23
 */
public interface AiVideoChapterMapper
{
    /**
     * 根据章节ID查询AI视频章节
     *
     * @param chapterId 章节ID
     * @return 章节信息对象，不存在时返回null
     */
    AiVideoChapter selectAiVideoChapterByChapterId(Long chapterId);

    /**
     * 根据项目ID查询AI视频章节列表
     *
     * @param projectId 项目ID
     * @return 章节列表
     */
    List<AiVideoChapter> selectAiVideoChapterList(Long projectId);

    /**
     * 新增AI视频章节
     *
     * @param chapter 章节信息对象
     * @return 影响的行数
     */
    int insertAiVideoChapter(AiVideoChapter chapter);

    /**
     * 更新AI视频章节解析状态
     *
     * @param chapterId          章节ID
     * @param parseStatus        解析状态
     * @param pipelineStatus     流水线状态
     * @param summaryText        摘要文本
     * @param currentBibleVersion 当前故事圣经版本号
     * @return 影响的行数
     */
    int updateAiVideoChapterAnalysisStatus(@Param("chapterId") Long chapterId, @Param("parseStatus") String parseStatus,
            @Param("pipelineStatus") String pipelineStatus, @Param("summaryText") String summaryText,
            @Param("currentBibleVersion") Integer currentBibleVersion);

    /**
     * 根据章节ID数组删除AI视频章节
     *
     * @param chapterIds 章节ID数组
     * @param projectId  项目ID
     * @return 影响的行数
     */
    int deleteAiVideoChapterByChapterIds(@Param("chapterIds") Long[] chapterIds, @Param("projectId") Long projectId);
}
