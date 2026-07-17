package com.lingXi.aiVedio.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.lingXi.common.core.controller.BaseController;
import com.lingXi.common.core.domain.AjaxResult;
import com.lingXi.aiVedio.domain.AiVideoGenerationTask;
import com.lingXi.aiVedio.mapper.AiVideoGenerationTaskMapper;
import com.lingXi.aiVedio.service.IAiVideoProjectService;

/** AI 视频生成任务查询。 */
@RestController
@RequestMapping("/aivideo/task")
public class AiVideoTaskController extends BaseController
{
    @Autowired
    private AiVideoGenerationTaskMapper taskMapper;

    @Autowired
    private IAiVideoProjectService projectService;

    @PreAuthorize("@ss.hasPermi('aivideo:asset:list')")
    @GetMapping("/list")
    public AjaxResult list(@RequestParam Long projectId)
    {
        projectService.checkProjectOwner(projectId);
        List<AiVideoGenerationTask> tasks = taskMapper.selectAiVideoGenerationTaskList(projectId);
        return success(tasks);
    }
}
