package com.lingXi.aiVedio.service;

import java.util.List;
import com.lingXi.aiVedio.domain.AiVideoProject;

/**
 * AI视频项目服务接口，提供项目的增删改查及权限校验功能。
 */
public interface IAiVideoProjectService
{
    /**
     * 根据项目ID查询项目信息。
     *
     * @param projectId 项目ID
     * @return 项目信息
     */
    AiVideoProject selectAiVideoProjectByProjectId(Long projectId);

    /**
     * 根据条件查询项目列表。
     *
     * @param project 查询条件
     * @return 项目列表
     */
    List<AiVideoProject> selectAiVideoProjectList(AiVideoProject project);

    /**
     * 新增AI视频项目。
     *
     * @param project 项目信息
     * @return 受影响行数
     */
    int insertAiVideoProject(AiVideoProject project);

    /**
     * 更新AI视频项目信息。
     *
     * @param project 项目信息
     * @return 受影响行数
     */
    int updateAiVideoProject(AiVideoProject project);

    /**
     * 批量删除AI视频项目。
     *
     * @param projectIds 项目ID数组
     * @return 受影响行数
     */
    int deleteAiVideoProjectByProjectIds(Long[] projectIds);

    /**
     * 校验当前用户是否为项目所有者。
     *
     * @param projectId 项目ID
     */
    void checkProjectOwner(Long projectId);
}
