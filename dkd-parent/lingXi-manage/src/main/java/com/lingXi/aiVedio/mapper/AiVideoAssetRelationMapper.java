package com.lingXi.aiVedio.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.lingXi.aiVedio.domain.AiVideoAsset;
import com.lingXi.aiVedio.domain.AiVideoAssetRelation;

/**
 * AI视频资产关系数据访问接口
 * <p>
 * 提供AI视频资产间引用关系的数据库操作方法，包括关系的新增、复制、删除和查询等操作。
 * 资产关系用于管理关键帧之间的版本引用绑定。
 * </p>
 *
 * @author lingXi
 * @since 2026-07-23
 */
public interface AiVideoAssetRelationMapper
{
    /**
     * 新增AI视频资产引用关系
     *
     * @param relation 资产关系信息对象
     * @return 影响的行数
     */
    int insertAiVideoAssetRelation(AiVideoAssetRelation relation);

    /**
     * 复制源资产的入站引用关系到新资产
     *
     * @param projectId     项目ID
     * @param sourceAssetId 源资产ID
     * @param newAssetId    新资产ID
     * @return 影响的行数
     */
    int copyIncomingReferenceRelations(@Param("projectId") Long projectId,
            @Param("sourceAssetId") Long sourceAssetId, @Param("newAssetId") Long newAssetId);

    /**
     * 删除指定目标资产的所有入站引用关系
     *
     * @param projectId     项目ID
     * @param targetAssetId 目标资产ID
     * @return 影响的行数
     */
    int deleteIncomingReferenceRelations(@Param("projectId") Long projectId,
            @Param("targetAssetId") Long targetAssetId);

    /**
     * 统计指定资产的活跃关键帧引用数量
     *
     * @param fromAssetId 源资产ID
     * @return 活跃引用数量
     */
    int countActiveKeyframeReferences(@Param("fromAssetId") Long fromAssetId);

    /**
     * 查询指定目标资产的活跃引用资产列表
     *
     * @param targetAssetId 目标资产ID
     * @return 引用该资产的活跃资产列表
     */
    List<AiVideoAsset> selectActiveReferenceAssetsByTargetAssetId(
            @Param("targetAssetId") Long targetAssetId);

    /**
     * 查询引用同族资产的自动关键帧ID列表
     *
     * @param projectId       项目ID
     * @param assetCode       资产编码
     * @param currentAssetId  当前资产ID（不包含）
     * @return 关键帧资产ID列表
     */
    List<Long> selectAutoKeyframeIdsReferencingAssetFamily(
            @Param("projectId") Long projectId, @Param("assetCode") String assetCode,
            @Param("currentAssetId") Long currentAssetId);
}
