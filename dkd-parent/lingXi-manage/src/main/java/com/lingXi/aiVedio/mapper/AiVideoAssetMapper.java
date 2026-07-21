package com.lingXi.aiVedio.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.lingXi.aiVedio.domain.AiVideoAsset;

public interface AiVideoAssetMapper
{
    List<AiVideoAsset> selectAiVideoAssetList(AiVideoAsset asset);

    AiVideoAsset selectAiVideoAssetByAssetId(Long assetId);

    AiVideoAsset selectAiVideoAssetByAssetIdForUpdate(Long assetId);

    AiVideoAsset selectLatestEditableVideoDraftBySourceAssetId(Long sourceAssetId);

    AiVideoAsset selectLatestReferenceAssetVersion(@Param("projectId") Long projectId,
            @Param("assetType") String assetType, @Param("sceneId") Long sceneId,
            @Param("characterId") Long characterId, @Param("assetCode") String assetCode);

    List<AiVideoAsset> selectKeyframeVersionsByShotId(@Param("projectId") Long projectId,
            @Param("shotId") Long shotId);

    AiVideoAsset selectProjectCharacterReferenceForUpdate(@Param("projectId") Long projectId,
            @Param("characterId") Long characterId, @Param("characterCode") String characterCode);

    Integer selectMaxAssetVersionForUpdate(@Param("projectId") Long projectId,
            @Param("assetCode") String assetCode);

    int insertAiVideoAsset(AiVideoAsset asset);

    int promoteProjectCharacterReference(@Param("assetId") Long assetId,
            @Param("characterId") Long characterId, @Param("updateBy") String updateBy);

    int markAiVideoAssetGenerated(AiVideoAsset asset);

    int approveAiVideoAsset(@Param("assetId") Long assetId, @Param("updateBy") String updateBy);

    int updateAiVideoAssetPrompt(@Param("assetId") Long assetId, @Param("promptText") String promptText,
            @Param("negativePromptText") String negativePromptText, @Param("updateBy") String updateBy);

    int updateAiVideoAssetVideoPrompt(@Param("assetId") Long assetId,
            @Param("promptText") String promptText,
            @Param("negativePromptText") String negativePromptText,
            @Param("durationMs") Integer durationMs,
            @Param("generationParamsJson") String generationParamsJson,
            @Param("updateBy") String updateBy);

    int updateAiVideoAssetReferenceBinding(@Param("assetId") Long assetId,
            @Param("sourceAssetId") Long sourceAssetId,
            @Param("metadataJson") String metadataJson,
            @Param("updateBy") String updateBy);

    int updateVideoSourceBinding(@Param("assetId") Long assetId,
            @Param("sourceAssetId") Long sourceAssetId,
            @Param("metadataJson") String metadataJson,
            @Param("updateBy") String updateBy);

    int markDraftAiVideoAssetGenerating(@Param("assetId") Long assetId,
            @Param("generationParamsJson") String generationParamsJson, @Param("updateBy") String updateBy);

    int markEditableVideoAssetGenerating(@Param("assetId") Long assetId,
            @Param("promptText") String promptText,
            @Param("negativePromptText") String negativePromptText,
            @Param("durationMs") Integer durationMs,
            @Param("generationParamsJson") String generationParamsJson,
            @Param("updateBy") String updateBy);

    int updateGeneratingVideoDuration(@Param("assetId") Long assetId,
            @Param("durationMs") Integer durationMs,
            @Param("generationParamsJson") String generationParamsJson,
            @Param("updateBy") String updateBy);

    int markAiVideoAssetFailed(AiVideoAsset asset);

    int markAiVideoAssetGenerating(@Param("assetId") Long assetId,
            @Param("generationParamsJson") String generationParamsJson, @Param("updateBy") String updateBy);

    int logicallyDeleteAiVideoAsset(@Param("assetId") Long assetId, @Param("updateBy") String updateBy);

    int archiveOtherAssetVersions(@Param("projectId") Long projectId,
            @Param("assetCode") String assetCode, @Param("currentAssetId") Long currentAssetId,
            @Param("updateBy") String updateBy);

    int activateAiVideoAssetVersion(@Param("assetId") Long assetId, @Param("updateBy") String updateBy);

    List<Long> selectAutoVideoIdsUsingKeyframeFamily(@Param("projectId") Long projectId,
            @Param("assetCode") String assetCode, @Param("currentAssetId") Long currentAssetId);

    int countActiveVideoAssetsBySourceAssetId(Long sourceAssetId);

    int countOtherActiveAssetsByStoragePath(@Param("assetId") Long assetId,
            @Param("storagePath") String storagePath);

    int rejectOrphanedGeneratingAssets();
}
