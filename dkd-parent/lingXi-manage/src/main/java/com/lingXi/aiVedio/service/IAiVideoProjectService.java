package com.lingXi.aiVedio.service;

import java.util.List;
import com.lingXi.aiVedio.domain.AiVideoProject;

public interface IAiVideoProjectService
{
    AiVideoProject selectAiVideoProjectByProjectId(Long projectId);

    List<AiVideoProject> selectAiVideoProjectList(AiVideoProject project);

    int insertAiVideoProject(AiVideoProject project);

    int updateAiVideoProject(AiVideoProject project);

    int deleteAiVideoProjectByProjectIds(Long[] projectIds);

    void checkProjectOwner(Long projectId);
}
