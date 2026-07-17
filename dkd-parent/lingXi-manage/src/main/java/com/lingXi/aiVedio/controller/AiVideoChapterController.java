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

/** AI 视频章节管理 */
@RestController
@RequestMapping("/aivideo/project/{projectId}/chapter")
public class AiVideoChapterController extends BaseController
{
    @Autowired
    private IAiVideoChapterService chapterService;

    @PreAuthorize("@ss.hasPermi('aivideo:project:query')")
    @GetMapping("/list")
    public AjaxResult list(@PathVariable Long projectId)
    {
        List<AiVideoChapter> chapters = chapterService.selectAiVideoChapterList(projectId);
        return success(chapters);
    }

    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "AI视频章节", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@PathVariable Long projectId, @RequestBody AiVideoChapter chapter)
    {
        chapter.setProjectId(projectId);
        return toAjax(chapterService.insertAiVideoChapter(chapter));
    }

    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "AI视频章节解析", businessType = BusinessType.OTHER)
    @PostMapping("/{chapterId}/analyze")
    public AjaxResult analyze(@PathVariable Long projectId, @PathVariable Long chapterId)
    {
        return success().put("taskId", chapterService.startChapterAnalysis(projectId, chapterId));
    }

    @PreAuthorize("@ss.hasPermi('aivideo:project:query')")
    @GetMapping("/{chapterId}/story-bible")
    public AjaxResult storyBible(@PathVariable Long projectId, @PathVariable Long chapterId)
    {
        AiVideoStoryBible bible = chapterService.selectLatestStoryBible(projectId, chapterId);
        return success(bible);
    }

    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "AI视频章节", businessType = BusinessType.DELETE)
    @DeleteMapping("/{chapterIds}")
    public AjaxResult remove(@PathVariable Long projectId, @PathVariable Long[] chapterIds)
    {
        return toAjax(chapterService.deleteAiVideoChapterByChapterIds(projectId, chapterIds));
    }
}
