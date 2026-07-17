package com.lingXi.aiVedio.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lingXi.common.annotation.Log;
import com.lingXi.common.core.controller.BaseController;
import com.lingXi.common.core.domain.AjaxResult;
import com.lingXi.common.core.page.TableDataInfo;
import com.lingXi.common.enums.BusinessType;
import com.lingXi.aiVedio.domain.AiVideoProject;
import com.lingXi.aiVedio.service.IAiVideoProjectService;

/** AI 视频项目管理 */
@RestController
@RequestMapping("/aivideo/project")
public class AiVideoProjectController extends BaseController
{
    @Autowired
    private IAiVideoProjectService projectService;

    @PreAuthorize("@ss.hasPermi('aivideo:project:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiVideoProject project)
    {
        startPage();
        List<AiVideoProject> list = projectService.selectAiVideoProjectList(project);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('aivideo:project:query')")
    @GetMapping("/{projectId}")
    public AjaxResult getInfo(@PathVariable Long projectId)
    {
        return success(projectService.selectAiVideoProjectByProjectId(projectId));
    }

    @PreAuthorize("@ss.hasPermi('aivideo:project:add')")
    @Log(title = "AI视频项目", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiVideoProject project)
    {
        return toAjax(projectService.insertAiVideoProject(project));
    }

    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "AI视频项目", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiVideoProject project)
    {
        return toAjax(projectService.updateAiVideoProject(project));
    }

    @PreAuthorize("@ss.hasPermi('aivideo:project:remove')")
    @Log(title = "AI视频项目", businessType = BusinessType.DELETE)
    @DeleteMapping("/{projectIds}")
    public AjaxResult remove(@PathVariable Long[] projectIds)
    {
        return toAjax(projectService.deleteAiVideoProjectByProjectIds(projectIds));
    }
}
