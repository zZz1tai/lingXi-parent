package com.lingXi.aiVedio.service;

import java.util.List;
import com.lingXi.aiVedio.domain.AiVideoAsset;
import com.lingXi.aiVedio.domain.dto.AiVideoAssetRegenerationDraftRequest;

public interface IAiVideoAssetService
{
    List<AiVideoAsset> selectAiVideoAssetList(AiVideoAsset asset);

    AiVideoAsset selectAiVideoAssetByAssetId(Long assetId);

    void approveAiVideoAsset(Long assetId);

    void retryImageGeneration(Long assetId);

    void updateImagePrompt(Long assetId, String promptText, String negativePromptText);

    AiVideoAsset createVideoPromptDraft(Long keyframeAssetId);

    AiVideoAsset updateVideoPrompt(Long videoAssetId, String promptText, String negativePromptText,
            Integer durationMs);

    AiVideoAsset createRegenerationDraft(Long assetId,
            AiVideoAssetRegenerationDraftRequest request);

    void deleteAiVideoAsset(Long assetId);

    Long startImageGeneration(Long assetId);

    Long startVideoGeneration(Long videoAssetId);

    void resolveWanxSubmission(Long videoAssetId, String action, String providerTaskId);
}
