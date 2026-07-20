package com.lingXi.aiVedio.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.lingXi.aiVedio.domain.AiVideoAsset;
import com.lingXi.aiVedio.domain.AiVideoAssetRelation;

public interface AiVideoAssetRelationMapper
{
    int insertAiVideoAssetRelation(AiVideoAssetRelation relation);

    int copyIncomingReferenceRelations(@Param("projectId") Long projectId,
            @Param("sourceAssetId") Long sourceAssetId, @Param("newAssetId") Long newAssetId);

    int deleteIncomingReferenceRelations(@Param("projectId") Long projectId,
            @Param("targetAssetId") Long targetAssetId);

    int countActiveKeyframeReferences(@Param("fromAssetId") Long fromAssetId);

    List<AiVideoAsset> selectActiveReferenceAssetsByTargetAssetId(
            @Param("targetAssetId") Long targetAssetId);
}
