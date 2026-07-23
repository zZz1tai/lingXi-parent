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

/**
 * AI视频项目管理控制器
 * <p>
 * 提供AI视频项目的增删改查等管理接口，包括项目列表查询、详情查看、新增、修改和删除等功能。
 * 该控制器继承自BaseController，提供了分页查询等基础功能。
 * </p>
 *
 * @author lingXi
 * @since 2026-07-23
 */
@RestController
@RequestMapping("/aivideo/project")
public class AiVideoProjectController extends BaseController
{
    @Autowired
    private IAiVideoProjectService projectService;

    /**
     * 获取AI视频项目列表
     * <p>
     * 根据查询条件获取AI视频项目的分页列表，支持按项目名称、状态等条件进行筛选。
     * </p>
     *
     * @param project 查询条件对象
     * @return 包含项目列表的分页数据
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiVideoProject project)
    {
        startPage();
        List<AiVideoProject> list = projectService.selectAiVideoProjectList(project);
        return getDataTable(list);
    }

    /**
     * 根据ID获取AI视频项目详情
     * <p>
     * 通过项目ID获取项目的详细信息，包括项目基本信息、配置等。
     * </p>
     *
     * @param projectId 项目ID
     * @return 包含项目详情的结果对象
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:query')")
    @GetMapping("/{projectId}")
    public AjaxResult getInfo(@PathVariable Long projectId)
    {
        return success(projectService.selectAiVideoProjectByProjectId(projectId));
    }

    /**
     * 新增AI视频项目
     * <p>
     * 创建新的AI视频项目，需要提供项目的基本信息，如项目名称、描述等。
     * 操作会记录日志信息。
     * </p>
     *
     * @param project 项目信息对象
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:add')")
    @Log(title = "AI视频项目", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiVideoProject project)
    {
        return toAjax(projectService.insertAiVideoProject(project));
    }

    /**
     * 更新AI视频项目
     * <p>
     * 更新现有AI视频项目的信息，需要提供完整的项目信息和ID。
     * 操作会记录日志信息。
     * </p>
     *
     * @param project 项目信息对象
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "AI视频项目", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiVideoProject project)
    {
        return toAjax(projectService.updateAiVideoProject(project));
    }

    /**
     * 删除AI视频项目
     * <p>
     * 根据项目ID列表删除指定的AI视频项目，支持批量删除。
     * 操作会记录日志信息。
     * </p>
     *
     * @param projectIds 项目ID数组
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:remove')")
    @Log(title = "AI视频项目", businessType = BusinessType.DELETE)
    @DeleteMapping("/{projectIds}")
    public AjaxResult remove(@PathVariable Long[] projectIds)
    {
        return toAjax(projectService.deleteAiVideoProjectByProjectIds(projectIds));
    }
}
