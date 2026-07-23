package com.lingXi.aiVedio.mapper;

import com.lingXi.aiVedio.domain.AiVideoShot;

/**
 * AI视频镜头数据访问接口
 * <p>
 * 提供AI视频镜头数据的数据库操作方法，包括查询和新增等基本操作。
 * 镜头是场景下的三级结构，用于承载关键帧和视频资产。
 * </p>
 *
 * @author lingXi
 * @since 2026-07-23
 */
public interface AiVideoShotMapper
{
    /**
     * 根据镜头ID查询AI视频镜头
     *
     * @param shotId 镜头ID
     * @return 镜头信息对象，不存在时返回null
     */
    AiVideoShot selectAiVideoShotByShotId(Long shotId);

    /**
     * 新增AI视频镜头
     *
     * @param shot 镜头信息对象
     * @return 影响的行数
     */
    int insertAiVideoShot(AiVideoShot shot);
}
