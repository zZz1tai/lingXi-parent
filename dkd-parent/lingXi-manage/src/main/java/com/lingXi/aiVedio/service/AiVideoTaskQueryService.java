package com.lingXi.aiVedio.service;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.lingXi.aiVedio.domain.AiVideoGenerationTask;
import com.lingXi.aiVedio.mapper.AiVideoGenerationTaskMapper;

/**
 * AI视频生成任务查询服务。
 * <p>承载任务队列页面的列表/分页/详情查询，避免控制器直接依赖 Mapper。</p>
 */
@Service
public class AiVideoTaskQueryService
{
    @Autowired
    private AiVideoGenerationTaskMapper taskMapper;

    /**
     * 查询项目下的全部生成任务。
     *
     * @param projectId 项目ID
     * @return 任务列表
     */
    public List<AiVideoGenerationTask> listByProject(Long projectId)
    {
        return taskMapper.selectAiVideoGenerationTaskList(projectId);
    }

    /**
     * 分页查询生成任务（跨项目）。
     *
     * @param query 筛选条件
     * @return 分页任务列表
     */
    public List<AiVideoGenerationTask> page(AiVideoGenerationTask query)
    {
        return taskMapper.selectAiVideoGenerationTaskPage(query);
    }

    /**
     * 按任务ID查询生成任务。
     *
     * @param taskId 任务ID
     * @return 任务或 null
     */
    public AiVideoGenerationTask getByTaskId(Long taskId)
    {
        return taskMapper.selectAiVideoGenerationTaskByTaskId(taskId);
    }
}
