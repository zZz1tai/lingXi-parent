package com.lingXi.aiVedio.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.lingXi.aiVedio.domain.AiVideoAsset;

/**
 * AI视频资产数据访问接口
 * <p>
 * 提供AI视频资产数据的数据库操作方法，包括查询、新增、更新、版本管理和状态流转等操作。
 * 资产分为关键帧和视频两种类型，支持多版本管理和引用绑定。
 * </p>
 *
 * @author lingXi
 * @since 2026-07-23
 */
public interface AiVideoAssetMapper
{
    /**
     * 根据查询条件获取AI视频资产列表
     *
     * @param asset 查询条件对象
     * @return 资产列表
     */
    List<AiVideoAsset> selectAiVideoAssetList(AiVideoAsset asset);

    /**
     * 根据资产ID查询AI视频资产
     *
     * @param assetId 资产ID
     * @return 资产信息对象，不存在时返回null
     */
    AiVideoAsset selectAiVideoAssetByAssetId(Long assetId);

    /**
     * 根据资产ID锁定查询AI视频资产（悲观锁）
     *
     * @param assetId 资产ID
     * @return 锁定的资产信息，不存在时返回null
     */
    AiVideoAsset selectAiVideoAssetByAssetIdForUpdate(Long assetId);

    /**
     * 根据源资产ID查询最新的可编辑视频草稿
     *
     * @param sourceAssetId 源资产ID
     * @return 最新的视频草稿信息，不存在时返回null
     */
    AiVideoAsset selectLatestEditableVideoDraftBySourceAssetId(Long sourceAssetId);

    /**
     * 查询指定条件下的最新参考资产版本
     *
     * @param projectId   项目ID
     * @param assetType   资产类型
     * @param sceneId     场景ID
     * @param characterId 角色ID
     * @param assetCode   资产编码
     * @return 最新参考版本资产信息，不存在时返回null
     */
    AiVideoAsset selectLatestReferenceAssetVersion(@Param("projectId") Long projectId,
            @Param("assetType") String assetType, @Param("sceneId") Long sceneId,
            @Param("characterId") Long characterId, @Param("assetCode") String assetCode);

    /**
     * 根据镜头ID查询关键帧的所有版本
     *
     * @param projectId 项目ID
     * @param shotId    镜头ID
     * @return 关键帧版本列表
     */
    List<AiVideoAsset> selectKeyframeVersionsByShotId(@Param("projectId") Long projectId,
            @Param("shotId") Long shotId);

    /**
     * 查询项目角色参考资产（悲观锁）
     *
     * @param projectId    项目ID
     * @param characterId  角色ID
     * @param characterCode 角色编码
     * @return 锁定的角色参考资产信息，不存在时返回null
     */
    AiVideoAsset selectProjectCharacterReferenceForUpdate(@Param("projectId") Long projectId,
            @Param("characterId") Long characterId, @Param("characterCode") String characterCode);

    /**
     * 查询指定资产编码的最大版本号（悲观锁）
     *
     * @param projectId 项目ID
     * @param assetCode 资产编码
     * @return 最大版本号，不存在时返回null
     */
    Integer selectMaxAssetVersionForUpdate(@Param("projectId") Long projectId,
            @Param("assetCode") String assetCode);

    /**
     * 新增AI视频资产
     *
     * @param asset 资产信息对象
     * @return 影响的行数
     */
    int insertAiVideoAsset(AiVideoAsset asset);

    /**
     * 将资产提升为项目角色参考版本
     *
     * @param assetId     资产ID
     * @param characterId 角色ID
     * @param updateBy    操作人
     * @return 影响的行数
     */
    int promoteProjectCharacterReference(@Param("assetId") Long assetId,
            @Param("characterId") Long characterId, @Param("updateBy") String updateBy);

    /**
     * 标记AI视频资产为已生成状态
     *
     * @param asset 资产信息对象（包含ID和存储路径等）
     * @return 影响的行数
     */
    int markAiVideoAssetGenerated(AiVideoAsset asset);

    /**
     * 审批通过AI视频资产
     *
     * @param assetId  资产ID
     * @param updateBy 操作人
     * @return 影响的行数
     */
    int approveAiVideoAsset(@Param("assetId") Long assetId, @Param("updateBy") String updateBy);

    /**
     * 更新AI视频资产的图片提示词
     *
     * @param assetId              资产ID
     * @param promptText           正向提示词
     * @param negativePromptText   负向提示词
     * @param updateBy             操作人
     * @return 影响的行数
     */
    int updateAiVideoAssetPrompt(@Param("assetId") Long assetId, @Param("promptText") String promptText,
            @Param("negativePromptText") String negativePromptText, @Param("updateBy") String updateBy);

    /**
     * 更新AI视频资产的视频提示词
     *
     * @param assetId                资产ID
     * @param promptText             正向提示词
     * @param negativePromptText     负向提示词
     * @param durationMs             视频时长（毫秒）
     * @param generationParamsJson   生成参数JSON
     * @param updateBy               操作人
     * @return 影响的行数
     */
    int updateAiVideoAssetVideoPrompt(@Param("assetId") Long assetId,
            @Param("promptText") String promptText,
            @Param("negativePromptText") String negativePromptText,
            @Param("durationMs") Integer durationMs,
            @Param("generationParamsJson") String generationParamsJson,
            @Param("updateBy") String updateBy);

    /**
     * 更新关键帧的参考版本绑定关系
     *
     * @param assetId       资产ID
     * @param sourceAssetId 源资产ID（参考版本）
     * @param metadataJson  绑定元数据JSON
     * @param updateBy      操作人
     * @return 影响的行数
     */
    int updateAiVideoAssetReferenceBinding(@Param("assetId") Long assetId,
            @Param("sourceAssetId") Long sourceAssetId,
            @Param("metadataJson") String metadataJson,
            @Param("updateBy") String updateBy);

    /**
     * 更新视频的来源关键帧绑定关系
     *
     * @param assetId       视频资产ID
     * @param sourceAssetId 来源关键帧资产ID
     * @param metadataJson  绑定元数据JSON
     * @param updateBy      操作人
     * @return 影响的行数
     */
    int updateVideoSourceBinding(@Param("assetId") Long assetId,
            @Param("sourceAssetId") Long sourceAssetId,
            @Param("metadataJson") String metadataJson,
            @Param("updateBy") String updateBy);

    /**
     * 标记草稿资产为生成中状态
     *
     * @param assetId              资产ID
     * @param generationParamsJson 生成参数JSON
     * @param updateBy             操作人
     * @return 影响的行数
     */
    int markDraftAiVideoAssetGenerating(@Param("assetId") Long assetId,
            @Param("generationParamsJson") String generationParamsJson, @Param("updateBy") String updateBy);

    /**
     * 标记可编辑视频资产为生成中状态
     *
     * @param assetId              资产ID
     * @param promptText           正向提示词
     * @param negativePromptText   负向提示词
     * @param durationMs           视频时长（毫秒）
     * @param generationParamsJson 生成参数JSON
     * @param updateBy             操作人
     * @return 影响的行数
     */
    int markEditableVideoAssetGenerating(@Param("assetId") Long assetId,
            @Param("promptText") String promptText,
            @Param("negativePromptText") String negativePromptText,
            @Param("durationMs") Integer durationMs,
            @Param("generationParamsJson") String generationParamsJson,
            @Param("updateBy") String updateBy);

    /**
     * 更新正在生成中的视频时长
     *
     * @param assetId              资产ID
     * @param durationMs           视频时长（毫秒）
     * @param generationParamsJson 生成参数JSON
     * @param updateBy             操作人
     * @return 影响的行数
     */
    int updateGeneratingVideoDuration(@Param("assetId") Long assetId,
            @Param("durationMs") Integer durationMs,
            @Param("generationParamsJson") String generationParamsJson,
            @Param("updateBy") String updateBy);

    /**
     * 标记AI视频资产为生成失败状态
     *
     * @param asset 资产信息对象（包含ID和错误信息）
     * @return 影响的行数
     */
    int markAiVideoAssetFailed(AiVideoAsset asset);

    /**
     * 标记AI视频资产为生成中状态
     *
     * @param assetId              资产ID
     * @param generationParamsJson 生成参数JSON
     * @param updateBy             操作人
     * @return 影响的行数
     */
    int markAiVideoAssetGenerating(@Param("assetId") Long assetId,
            @Param("generationParamsJson") String generationParamsJson, @Param("updateBy") String updateBy);

    /**
     * 逻辑删除AI视频资产
     *
     * @param assetId  资产ID
     * @param updateBy 操作人
     * @return 影响的行数
     */
    int logicallyDeleteAiVideoAsset(@Param("assetId") Long assetId, @Param("updateBy") String updateBy);

    /**
     * 归档同资产编码的其他版本
     *
     * @param projectId       项目ID
     * @param assetCode       资产编码
     * @param currentAssetId  当前资产ID（不归档）
     * @param updateBy        操作人
     * @return 影响的行数
     */
    int archiveOtherAssetVersions(@Param("projectId") Long projectId,
            @Param("assetCode") String assetCode, @Param("currentAssetId") Long currentAssetId,
            @Param("updateBy") String updateBy);

    /**
     * 激活指定资产版本
     *
     * @param assetId  资产ID
     * @param updateBy 操作人
     * @return 影响的行数
     */
    int activateAiVideoAssetVersion(@Param("assetId") Long assetId, @Param("updateBy") String updateBy);

    /**
     * 查询使用同族关键帧的自动视频ID列表
     *
     * @param projectId       项目ID
     * @param assetCode       资产编码
     * @param currentAssetId  当前资产ID（不包含）
     * @return 视频资产ID列表
     */
    List<Long> selectAutoVideoIdsUsingKeyframeFamily(@Param("projectId") Long projectId,
            @Param("assetCode") String assetCode, @Param("currentAssetId") Long currentAssetId);

    /**
     * 统计指定源资产的活跃视频资产数量
     *
     * @param sourceAssetId 源资产ID
     * @return 活跃视频资产数量
     */
    int countActiveVideoAssetsBySourceAssetId(Long sourceAssetId);

    /**
     * 统计使用相同存储路径的其他活跃资产数量
     *
     * @param assetId     资产ID（排除自身）
     * @param storagePath 存储路径
     * @return 其他活跃资产数量
     */
    int countOtherActiveAssetsByStoragePath(@Param("assetId") Long assetId,
            @Param("storagePath") String storagePath);

    /**
     * 拒绝孤立的生成中资产
     *
     * @return 影响的行数
     */
    int rejectOrphanedGeneratingAssets();
}
