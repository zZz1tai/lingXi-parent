package com.lingXi.aiVedio.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lingXi.common.annotation.Log;
import com.lingXi.common.core.controller.BaseController;
import com.lingXi.common.core.domain.AjaxResult;
import com.lingXi.common.enums.BusinessType;
import com.lingXi.aiVedio.domain.AiVideoChapter;
import com.lingXi.aiVedio.domain.AiVideoStoryBible;
import com.lingXi.aiVedio.service.IAiVideoChapterService;

/**
 * AI视频章节管理控制器
 * <p>
 * 提供AI视频章节的增删改查等管理接口，包括章节列表查询、新增、解析、暂停解析、故事圣经查看和删除等功能。
 * 章节属于特定的项目，通过projectId进行关联。
 * </p>
 *
 * @author lingXi
 * @since 2026-07-23
 */
@RestController
@RequestMapping("/aivideo/project/{projectId}/chapter")
public class AiVideoChapterController extends BaseController
{
    @Autowired
    private IAiVideoChapterService chapterService;

    /**
     * 获取指定项目的章节列表
     * <p>
     * 根据项目ID获取该项目下所有章节的列表信息。
     * </p>
     *
     * @param projectId 项目ID
     * @return 包含章节列表的结果对象
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:query')")
    @GetMapping("/list")
    public AjaxResult list(@PathVariable Long projectId)
    {
        List<AiVideoChapter> chapters = chapterService.selectAiVideoChapterList(projectId);
        return success(chapters);
    }

    /**
     * 新增AI视频章节
     * <p>
     * 在指定项目下创建新的章节，需要提供章节的基本信息。
     * 操作会记录日志信息。
     * </p>
     *
     * @param projectId 项目ID
     * @param chapter   章节信息对象
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "AI视频章节", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@PathVariable Long projectId, @RequestBody AiVideoChapter chapter)
    {
        chapter.setProjectId(projectId);
        return toAjax(chapterService.insertAiVideoChapter(chapter));
    }

    /**
     * 开始AI视频章节解析
     * <p>
     * 启动对指定章节的内容解析任务，系统将自动分析章节内容并生成相应的结构化数据。
     * 操作会记录日志信息。
     * </p>
     *
     * @param projectId 项目ID
     * @param chapterId 章节ID
     * @return 包含任务ID的结果对象
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "AI视频章节解析", businessType = BusinessType.OTHER)
    @PostMapping("/{chapterId}/analyze")
    public AjaxResult analyze(@PathVariable Long projectId, @PathVariable Long chapterId)
    {
        return success().put("taskId", chapterService.startChapterAnalysis(projectId, chapterId));
    }

    /**
     * 暂停AI视频章节解析
     * <p>
     * 暂停正在进行的章节解析任务，可以在后续恢复解析进度。
     * 操作会记录日志信息。
     * </p>
     *
     * @param projectId 项目ID
     * @param chapterId 章节ID
     * @return 包含任务ID的结果对象
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "暂停AI视频章节解析", businessType = BusinessType.OTHER)
    @PostMapping("/{chapterId}/analysis/pause")
    public AjaxResult pauseAnalysis(@PathVariable Long projectId, @PathVariable Long chapterId)
    {
        return success().put("taskId", chapterService.pauseChapterAnalysis(projectId, chapterId));
    }

    /**
     * 获取章节的故事圣经
     * <p>
     * 获取指定章节的最新故事圣经内容，故事圣经包含章节的详细结构化信息。
     * </p>
     *
     * @param projectId 项目ID
     * @param chapterId 章节ID
     * @return 包含故事圣经内容的结果对象
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:query')")
    @GetMapping("/{chapterId}/story-bible")
    public AjaxResult storyBible(@PathVariable Long projectId, @PathVariable Long chapterId)
    {
        AiVideoStoryBible bible = chapterService.selectLatestStoryBible(projectId, chapterId);
        return success(bible);
    }

    /**
     * 删除AI视频章节
     * <p>
     * 根据章节ID列表删除指定项目下的章节，支持批量删除。
     * 操作会记录日志信息。
     * </p>
     *
     * @param projectId 项目ID
     * @param chapterIds 章节ID数组
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "AI视频章节", businessType = BusinessType.DELETE)
    @DeleteMapping("/{chapterIds}")
    public AjaxResult remove(@PathVariable Long projectId, @PathVariable Long[] chapterIds)
    {
        return toAjax(chapterService.deleteAiVideoChapterByChapterIds(projectId, chapterIds));
    }
}
