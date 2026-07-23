package com.lingXi.aiVedio.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.lingXi.aiVedio.domain.AiVideoProject;

/**
 * AI视频项目数据访问接口
 * <p>
 * 提供AI视频项目数据的数据库操作方法，包括查询、新增、更新和删除等基本操作。
 * </p>
 *
 * @author lingXi
 * @since 2026-07-23
 */
public interface AiVideoProjectMapper
{
    /**
     * 根据项目ID查询AI视频项目
     *
     * @param projectId 项目ID
     * @return 项目信息对象，不存在时返回null
     */
    AiVideoProject selectAiVideoProjectByProjectId(Long projectId);

    /**
     * 查询AI视频项目列表
     *
     * @param project 查询条件对象
     * @return 项目列表
     */
    List<AiVideoProject> selectAiVideoProjectList(AiVideoProject project);

    /**
     * 新增AI视频项目
     *
     * @param project 项目信息对象
     * @return 影响的行数
     */
    int insertAiVideoProject(AiVideoProject project);

    /**
     * 更新AI视频项目
     *
     * @param project 项目信息对象
     * @return 影响的行数
     */
    int updateAiVideoProject(AiVideoProject project);

    /**
     * 根据项目ID数组删除AI视频项目
     *
     * @param projectIds   项目ID数组
     * @param ownerUserId  项目所有者用户ID，用于权限验证
     * @return 影响的行数
     */
    int deleteAiVideoProjectByProjectIds(@Param("projectIds") Long[] projectIds, @Param("ownerUserId") Long ownerUserId);
}
