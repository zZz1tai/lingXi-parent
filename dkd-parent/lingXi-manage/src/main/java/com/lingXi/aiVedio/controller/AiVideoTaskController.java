package com.lingXi.aiVedio.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.lingXi.common.core.controller.BaseController;
import com.lingXi.common.core.domain.AjaxResult;
import com.lingXi.common.core.page.TableDataInfo;
import com.lingXi.aiVedio.domain.AiVideoGenerationTask;
import com.lingXi.aiVedio.service.AiVideoTaskCancellationService;
import com.lingXi.aiVedio.service.AiVideoTaskQueryService;
import com.lingXi.aiVedio.service.IAiVideoProjectService;
import com.lingXi.common.utils.SecurityUtils;

/**
 * AI视频生成任务控制器
 * <p>
 * 提供AI视频生成任务的查询接口，用于查看指定项目下的所有生成任务状态和信息。
 * </p>
 *
 * @author lingXi
 * @since 2026-07-23
 */
@RestController
@RequestMapping("/aivideo/task")
public class AiVideoTaskController extends BaseController
{
    @Autowired
    private AiVideoTaskQueryService taskQueryService;

    @Autowired
    private IAiVideoProjectService projectService;

    @Autowired
    private AiVideoTaskCancellationService cancellationService;

    /**
     * 获取项目的生成任务列表
     * <p>
     * 根据项目ID获取该项目下所有AI视频生成任务的列表信息，包括任务状态、进度等。
     * 会验证用户对项目的访问权限。
     * </p>
     *
     * @param projectId 项目ID
     * @return 包含生成任务列表的结果对象
     */
    @PreAuthorize("@ss.hasPermi('aivideo:asset:list')")
    @GetMapping("/list")
    public AjaxResult list(@RequestParam Long projectId)
    {
        projectService.checkProjectOwner(projectId);
        List<AiVideoGenerationTask> tasks = taskQueryService.listByProject(projectId);
        return success(tasks);
    }

    /**
     * 分页查询生成任务队列。
     * <p>
     * 支持按项目、任务类型和状态筛选，跨项目查看全部生成任务；
     * 指定项目时校验用户对项目的访问权限。
     * </p>
     *
     * @param query 筛选条件（projectId/taskType/status）
     * @return 分页任务列表
     */
    @PreAuthorize("@ss.hasPermi('aivideo:task:list')")
    @GetMapping("/page")
    public TableDataInfo page(AiVideoGenerationTask query)
    {
        if (query.getProjectId() != null)
        {
            projectService.checkProjectOwner(query.getProjectId());
        }
        startPage();
        return getDataTable(taskQueryService.page(query));
    }

    /**
     * 取消生成任务。
     * <p>
     * 排队/重试中的任务直接取消；等待回调或轮询中的视频任务取消后为终态，
     * 供应商晚到结果会被忽略。需校验用户对任务所属项目的访问权限。
     * </p>
     *
     * @param taskId 任务ID
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @PostMapping("/{taskId}/cancel")
    public AjaxResult cancel(@PathVariable Long taskId)
    {
        AiVideoGenerationTask task = taskQueryService.getByTaskId(taskId);
        if (task != null)
        {
            projectService.checkProjectOwner(task.getProjectId());
        }
        cancellationService.cancel(taskId, SecurityUtils.getUsername());
        return success();
    }
}
