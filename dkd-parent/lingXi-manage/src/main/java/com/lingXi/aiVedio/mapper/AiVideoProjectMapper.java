package com.lingXi.aiVedio.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.lingXi.aiVedio.domain.AiVideoProject;

public interface AiVideoProjectMapper
{
    AiVideoProject selectAiVideoProjectByProjectId(Long projectId);

    List<AiVideoProject> selectAiVideoProjectList(AiVideoProject project);

    int insertAiVideoProject(AiVideoProject project);

    int updateAiVideoProject(AiVideoProject project);

    int deleteAiVideoProjectByProjectIds(@Param("projectIds") Long[] projectIds, @Param("ownerUserId") Long ownerUserId);
}
