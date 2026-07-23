package com.lingXi.aiVedio.service;

import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.lingXi.aiVedio.domain.AiVideoAsset;
import com.lingXi.aiVedio.domain.dto.AiVideoAssetRegenerationDraftRequest;
import com.lingXi.aiVedio.domain.dto.AiVideoKeyframeReferenceBindingRequest;
import com.lingXi.aiVedio.domain.dto.AiVideoVideoSourceBindingRequest;

/**
 * AI视频资产服务接口，提供资产的增删改查、版本管理、生成任务控制及引用绑定等功能。
 */
public interface IAiVideoAssetService
{
    /**
     * 根据条件查询资产列表。
     *
     * @param asset 查询条件
     * @return 资产列表
     */
    List<AiVideoAsset> selectAiVideoAssetList(AiVideoAsset asset);

    /**
     * 根据资产ID查询资产详情。
     *
     * @param assetId 资产ID
     * @return 资产信息
     */
    AiVideoAsset selectAiVideoAssetByAssetId(Long assetId);

    /**
     * 确认使用图片资产并激活版本。
     *
     * @param assetId 资产ID
     */
    void approveAiVideoAsset(Long assetId);

    /**
     * 重试失败的图片生成任务。
     *
     * @param assetId 资产ID
     */
    void retryImageGeneration(Long assetId);

    /**
     * 更新图片生成提示词。
     *
     * @param assetId 资产ID
     * @param promptText 正向提示词
     * @param negativePromptText 反向提示词
     */
    void updateImagePrompt(Long assetId, String promptText, String negativePromptText);

    /**
     * 根据关键帧资产创建视频提示词草稿。
     *
     * @param keyframeAssetId 关键帧资产ID
     * @return 视频草稿资产
     */
    AiVideoAsset createVideoPromptDraft(Long keyframeAssetId);

    /**
     * 更新视频生成提示词。
     *
     * @param videoAssetId 视频资产ID
     * @param promptText 正向提示词
     * @param negativePromptText 反向提示词
     * @param durationMs 视频时长（毫秒）
     * @return 更新后的视频资产
     */
    AiVideoAsset updateVideoPrompt(Long videoAssetId, String promptText, String negativePromptText,
            Integer durationMs);

    /**
     * 基于现有资产创建重新生成草稿（新版本）。
     *
     * @param assetId 源资产ID
     * @param request 重新生成请求参数
     * @return 新版本草稿资产
     */
    AiVideoAsset createRegenerationDraft(Long assetId,
            AiVideoAssetRegenerationDraftRequest request);

    /**
     * 查询关键帧的参考图绑定详情。
     *
     * @param assetId 关键帧资产ID
     * @return 绑定详情JSON
     */
    JsonNode getKeyframeReferenceBinding(Long assetId);

    /**
     * 更新关键帧的参考图绑定关系。
     *
     * @param assetId 关键帧资产ID
     * @param request 绑定请求参数
     * @return 更新后的资产
     */
    AiVideoAsset updateKeyframeReferenceBinding(Long assetId,
            AiVideoKeyframeReferenceBindingRequest request);

    /**
     * 重置关键帧的参考图绑定为自动模式。
     *
     * @param assetId 关键帧资产ID
     * @return 重置后的资产
     */
    AiVideoAsset resetKeyframeReferenceBinding(Long assetId);

    /**
     * 查询视频资产的来源关键帧绑定详情。
     *
     * @param videoAssetId 视频资产ID
     * @return 绑定详情JSON
     */
    JsonNode getVideoSourceBinding(Long videoAssetId);

    /**
     * 更新视频资产的来源关键帧绑定。
     *
     * @param videoAssetId 视频资产ID
     * @param request 绑定请求参数
     * @return 更新后的资产
     */
    AiVideoAsset updateVideoSourceBinding(Long videoAssetId,
            AiVideoVideoSourceBindingRequest request);

    /**
     * 重置视频资产的来源关键帧绑定为自动模式。
     *
     * @param videoAssetId 视频资产ID
     * @return 重置后的资产
     */
    AiVideoAsset resetVideoSourceBinding(Long videoAssetId);

    /**
     * 删除AI视频资产（逻辑删除）。
     *
     * @param assetId 资产ID
     */
    void deleteAiVideoAsset(Long assetId);

    /**
     * 激活图片资产版本（切换为当前版本）。
     *
     * @param assetId 资产ID
     */
    void activateAiVideoAssetVersion(Long assetId);

    /**
     * 后台生成完成后激活版本，不依赖Web登录态。
     *
     * @param assetId 资产ID
     * @param updateBy 操作人
     */
    void activateGeneratedAiVideoAssetVersion(Long assetId, String updateBy);

    /**
     * 启动图片生成任务。
     *
     * @param assetId 资产ID
     * @return 生成任务ID
     */
    Long startImageGeneration(Long assetId);

    /**
     * 启动视频生成任务。
     *
     * @param videoAssetId 视频资产ID
     * @return 生成任务ID
     */
    Long startVideoGeneration(Long videoAssetId);

    /**
     * 处理视频提交结果的核对操作。
     *
     * @param videoAssetId 视频资产ID
     * @param action 核对操作类型
     * @param providerTaskId 供应商任务ID
     */
    void resolveVideoSubmission(Long videoAssetId, String action, String providerTaskId);
}
