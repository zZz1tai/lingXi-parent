package com.lingXi.aiVedio.service;

import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.lingXi.aiVedio.domain.AiVideoAsset;
import com.lingXi.aiVedio.domain.dto.AiVideoAssetRegenerationDraftRequest;
import com.lingXi.aiVedio.domain.dto.AiVideoKeyframeReferenceBindingRequest;
import com.lingXi.aiVedio.domain.dto.AiVideoVideoSourceBindingRequest;

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

    JsonNode getKeyframeReferenceBinding(Long assetId);

    AiVideoAsset updateKeyframeReferenceBinding(Long assetId,
            AiVideoKeyframeReferenceBindingRequest request);

    AiVideoAsset resetKeyframeReferenceBinding(Long assetId);

    JsonNode getVideoSourceBinding(Long videoAssetId);

    AiVideoAsset updateVideoSourceBinding(Long videoAssetId,
            AiVideoVideoSourceBindingRequest request);

    AiVideoAsset resetVideoSourceBinding(Long videoAssetId);

    void deleteAiVideoAsset(Long assetId);

    Long startImageGeneration(Long assetId);

    Long startVideoGeneration(Long videoAssetId);

    void resolveVideoSubmission(Long videoAssetId, String action, String providerTaskId);
}
