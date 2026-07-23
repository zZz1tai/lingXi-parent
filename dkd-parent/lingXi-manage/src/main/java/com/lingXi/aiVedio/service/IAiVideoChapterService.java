package com.lingXi.aiVedio.service;

import java.util.List;
import com.lingXi.aiVedio.domain.AiVideoChapter;
import com.lingXi.aiVedio.domain.AiVideoStoryBible;

/**
 * AI视频章节服务接口，提供章节管理、故事圣经解析控制等功能。
 */
public interface IAiVideoChapterService
{
    /**
     * 根据项目ID查询章节列表。
     *
     * @param projectId 项目ID
     * @return 章节列表
     */
    List<AiVideoChapter> selectAiVideoChapterList(Long projectId);

    /**
     * 新增AI视频章节。
     *
     * @param chapter 章节信息
     * @return 受影响行数
     */
    int insertAiVideoChapter(AiVideoChapter chapter);

    /**
     * 批量删除AI视频章节。
     *
     * @param projectId 项目ID
     * @param chapterIds 章节ID数组
     * @return 受影响行数
     */
    int deleteAiVideoChapterByChapterIds(Long projectId, Long[] chapterIds);

    /**
     * 启动章节故事圣经解析。
     *
     * @param projectId 项目ID
     * @param chapterId 章节ID
     * @return 生成任务ID
     */
    Long startChapterAnalysis(Long projectId, Long chapterId);

    /**
     * 暂停章节故事圣经解析。
     *
     * @param projectId 项目ID
     * @param chapterId 章节ID
     * @return 生成任务ID
     */
    Long pauseChapterAnalysis(Long projectId, Long chapterId);

    /**
     * 查询章节最新版本的故事圣经。
     *
     * @param projectId 项目ID
     * @param chapterId 章节ID
     * @return 故事圣经信息
     */
    AiVideoStoryBible selectLatestStoryBible(Long projectId, Long chapterId);
}
