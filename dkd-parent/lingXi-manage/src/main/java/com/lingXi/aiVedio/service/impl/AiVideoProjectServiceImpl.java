package com.lingXi.aiVedio.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.lingXi.common.exception.ServiceException;
import com.lingXi.common.utils.DateUtils;
import com.lingXi.common.utils.SecurityUtils;
import com.lingXi.common.utils.StringUtils;
import com.lingXi.aiVedio.domain.AiVideoProject;
import com.lingXi.aiVedio.mapper.AiVideoProjectMapper;
import com.lingXi.aiVedio.service.IAiVideoProjectService;

@Service
public class AiVideoProjectServiceImpl implements IAiVideoProjectService
{
    @Autowired
    private AiVideoProjectMapper projectMapper;

    @Override
    public AiVideoProject selectAiVideoProjectByProjectId(Long projectId)
    {
        AiVideoProject project = projectMapper.selectAiVideoProjectByProjectId(projectId);
        checkProjectOwner(project);
        return project;
    }

    @Override
    public List<AiVideoProject> selectAiVideoProjectList(AiVideoProject project)
    {
        project.setOwnerUserId(SecurityUtils.getUserId());
        return projectMapper.selectAiVideoProjectList(project);
    }

    @Override
    public int insertAiVideoProject(AiVideoProject project)
    {
        validateProject(project);
        project.setProjectId(null);
        project.setOwnerUserId(SecurityUtils.getUserId());
        project.setStatus("DRAFT");
        project.setSourceType(defaultIfBlank(project.getSourceType(), "NOVEL"));
        project.setAdaptationMode(defaultIfBlank(project.getAdaptationMode(), "FAITHFUL"));
        project.setDefaultAspectRatio(defaultIfBlank(project.getDefaultAspectRatio(), "16:9"));
        project.setDefaultLanguage(defaultIfBlank(project.getDefaultLanguage(), "zh-CN"));
        project.setCreateBy(SecurityUtils.getUsername());
        project.setCreateTime(DateUtils.getNowDate());
        return projectMapper.insertAiVideoProject(project);
    }

    @Override
    public int updateAiVideoProject(AiVideoProject project)
    {
        if (project.getProjectId() == null)
        {
            throw new ServiceException("项目ID不能为空");
        }
        AiVideoProject existing = projectMapper.selectAiVideoProjectByProjectId(project.getProjectId());
        checkProjectOwner(existing);
        validateProject(project);
        existing.setProjectName(project.getProjectName());
        existing.setCoverUrl(project.getCoverUrl());
        existing.setVisualStyle(project.getVisualStyle());
        existing.setUpdateBy(SecurityUtils.getUsername());
        existing.setUpdateTime(DateUtils.getNowDate());
        return projectMapper.updateAiVideoProject(existing);
    }

    @Override
    public int deleteAiVideoProjectByProjectIds(Long[] projectIds)
    {
        if (projectIds == null || projectIds.length == 0)
        {
            throw new ServiceException("请选择需要删除的项目");
        }
        return projectMapper.deleteAiVideoProjectByProjectIds(projectIds, SecurityUtils.getUserId());
    }

    @Override
    public void checkProjectOwner(Long projectId)
    {
        checkProjectOwner(projectMapper.selectAiVideoProjectByProjectId(projectId));
    }

    private void checkProjectOwner(AiVideoProject project)
    {
        if (project == null || !SecurityUtils.getUserId().equals(project.getOwnerUserId()))
        {
            throw new ServiceException("项目不存在或无权访问");
        }
    }

    private void validateProject(AiVideoProject project)
    {
        if (StringUtils.isEmpty(project.getProjectName()))
        {
            throw new ServiceException("项目名称不能为空");
        }
        if (project.getProjectName().length() > 128)
        {
            throw new ServiceException("项目名称不能超过128个字符");
        }
        if (!StringUtils.isEmpty(project.getCoverUrl()) && project.getCoverUrl().length() > 1024)
        {
            throw new ServiceException("项目封面地址不能超过1024个字符");
        }
    }

    private String defaultIfBlank(String value, String defaultValue)
    {
        return StringUtils.isEmpty(value) ? defaultValue : value;
    }
}
