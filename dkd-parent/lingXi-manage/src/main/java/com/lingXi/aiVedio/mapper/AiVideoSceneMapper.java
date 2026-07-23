package com.lingXi.aiVedio.mapper;

import com.lingXi.aiVedio.domain.AiVideoScene;

/**
 * AI视频场景数据访问接口
 * <p>
 * 提供AI视频场景数据的数据库操作方法，包括查询和新增等基本操作。
 * 场景是章节下的二级结构，用于组织镜头和角色信息。
 * </p>
 *
 * @author lingXi
 * @since 2026-07-23
 */
public interface AiVideoSceneMapper
{
    /**
     * 根据场景ID查询AI视频场景
     *
     * @param sceneId 场景ID
     * @return 场景信息对象，不存在时返回null
     */
    AiVideoScene selectAiVideoSceneBySceneId(Long sceneId);

    /**
     * 新增AI视频场景
     *
     * @param scene 场景信息对象
     * @return 影响的行数
     */
    int insertAiVideoScene(AiVideoScene scene);
}
