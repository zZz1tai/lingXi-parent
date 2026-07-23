package com.lingXi.aiVedio.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.lingXi.aiVedio.domain.AiVideoAsset;
import com.lingXi.aiVedio.mapper.AiVideoAssetRelationMapper;
import com.lingXi.aiVedio.storage.AiVideoPublicAssetUrlResolver;
import com.lingXi.common.exception.ServiceException;

/**
 * 图片引用服务，校验资产关系并将分镜参考图解析为下游可访问的数据。
 */
@Service
public class AiVideoImageReferenceService
{
    @Autowired
    private AiVideoAssetRelationMapper relationMapper;
    @Autowired
    private AiVideoPublicAssetUrlResolver publicAssetUrlResolver;

    /**
     * 解析并校验目标资产的参考图引用关系。
     *
     * @param targetAsset 目标资产（必须是SHOT_KEYFRAME类型）
     * @return 解析后的图片引用集合
     */
    public ResolvedImageReferences resolveAndValidate(AiVideoAsset targetAsset)
    {
        if (targetAsset == null)
        {
            throw new ServiceException("图片资产不能为空");
        }
        if (!"SHOT_KEYFRAME".equals(targetAsset.getAssetType()))
        {
            return ResolvedImageReferences.empty();
        }

        List<AiVideoAsset> references = relationMapper
                .selectActiveReferenceAssetsByTargetAssetId(targetAsset.getAssetId());
        List<AiVideoAsset> characterReferences = new ArrayList<>();
        AiVideoAsset sceneReference = null;
        if (references != null)
        {
            for (AiVideoAsset reference : references)
            {
                validateCommonReference(targetAsset, reference);
                if ("CHARACTER_REFERENCE".equals(reference.getAssetType()))
                {
                    characterReferences.add(reference);
                }
                else if ("SCENE_REFERENCE".equals(reference.getAssetType()))
                {
                    if (sceneReference != null)
                    {
                        throw new ServiceException("镜头关键帧必须且只能关联1张场景参考图");
                    }
                    sceneReference = reference;
                }
                else
                {
                    throw new ServiceException("镜头关联了不支持的参考图类型：" + reference.getAssetType());
                }
            }
        }
        if (sceneReference == null)
        {
            throw new ServiceException("镜头关键帧缺少场景参考图，请先完成场景图片");
        }
        List<Long> assetIds = new ArrayList<>(characterReferences.size() + 1);
        List<String> imageUrls = new ArrayList<>(characterReferences.size() + 1);
        Set<Long> uniqueAssetIds = new LinkedHashSet<>();
        for (AiVideoAsset characterReference : characterReferences)
        {
            addResolvedReference(characterReference, uniqueAssetIds, assetIds, imageUrls);
        }
        // 保持人物关系在前、场景关系在后的稳定搬运顺序。
        addResolvedReference(sceneReference, uniqueAssetIds, assetIds, imageUrls);
        return new ResolvedImageReferences(assetIds, imageUrls);
    }

    /**
     * 校验参考图的通用约束（项目一致性、状态、存储等）。
     *
     * @param targetAsset 目标资产
     * @param reference 参考图资产
     */
    private void validateCommonReference(AiVideoAsset targetAsset, AiVideoAsset reference)
    {
        if (reference == null || reference.getAssetId() == null)
        {
            throw new ServiceException("镜头参考图记录不存在");
        }
        if (targetAsset.getProjectId() == null
                || !targetAsset.getProjectId().equals(reference.getProjectId()))
        {
            throw new ServiceException("镜头参考图与关键帧不属于同一项目");
        }
        if (!"APPROVED".equals(reference.getStatus()))
        {
            throw new ServiceException("参考图尚未生成完成：" + referenceName(reference));
        }
        if (reference.getObjectKey() == null || reference.getObjectKey().trim().isEmpty())
        {
            throw new ServiceException("参考图尚未转存完成：" + referenceName(reference));
        }
        if (reference.getFileSize() == null || reference.getFileSize().longValue() < 1L)
        {
            throw new ServiceException("参考图缺少文件大小信息：" + referenceName(reference));
        }
    }

    /**
     * 添加已解析的参考图引用到结果集合。
     *
     * @param reference 参考图资产
     * @param uniqueAssetIds 已处理的资产ID集合（去重用）
     * @param assetIds 资产ID结果列表
     * @param imageUrls 图片URL结果列表
     */
    private void addResolvedReference(AiVideoAsset reference, Set<Long> uniqueAssetIds,
            List<Long> assetIds, List<String> imageUrls)
    {
        if (!uniqueAssetIds.add(reference.getAssetId()))
        {
            throw new ServiceException("镜头重复关联了同一张参考图：" + referenceName(reference));
        }
        String publicUrl = publicAssetUrlResolver.resolve(reference.getObjectKey().trim());
        validatePublicHttpUrl(publicUrl, reference);
        assetIds.add(reference.getAssetId());
        imageUrls.add(publicUrl);
    }

    /**
     * 校验公网HTTP(S)地址格式。
     *
     * @param publicUrl 公网URL
     * @param reference 参考图资产（用于错误提示）
     */
    private void validatePublicHttpUrl(String publicUrl, AiVideoAsset reference)
    {
        try
        {
            URI uri = new URI(publicUrl);
            String scheme = uri.getScheme();
            if ((!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null || uri.getHost().trim().isEmpty())
            {
                throw new IllegalArgumentException("unsupported scheme");
            }
        }
        catch (Exception ex)
        {
            throw new ServiceException("参考图不是下游服务可访问的公网HTTP(S)地址："
                    + referenceName(reference));
        }
    }

    /**
     * 获取参考图资产的显示名称。
     *
     * @param reference 参考图资产
     * @return 资产名称或资产ID
     */
    private String referenceName(AiVideoAsset reference)
    {
        return reference.getAssetName() == null || reference.getAssetName().trim().isEmpty()
                ? "资产" + reference.getAssetId() : reference.getAssetName();
    }

    /**
     * 已解析的图片引用结果集合，包含资产ID列表和对应的图片URL列表。
     */
    public static final class ResolvedImageReferences
    {
        private static final ResolvedImageReferences EMPTY = new ResolvedImageReferences(
                Collections.<Long>emptyList(), Collections.<String>emptyList());

        private final List<Long> assetIds;
        private final List<String> imageUrls;

        private ResolvedImageReferences(List<Long> assetIds, List<String> imageUrls)
        {
            this.assetIds = Collections.unmodifiableList(new ArrayList<>(assetIds));
            this.imageUrls = Collections.unmodifiableList(new ArrayList<>(imageUrls));
        }

        /**
         * 返回空的引用结果。
         *
         * @return 空的ResolvedImageReferences实例
         */
        public static ResolvedImageReferences empty()
        {
            return EMPTY;
        }

        /**
         * 获取资产ID列表。
         *
         * @return 资产ID列表
         */
        public List<Long> getAssetIds()
        {
            return assetIds;
        }

        /**
         * 获取图片URL列表。
         *
         * @return 图片URL列表
         */
        public List<String> getImageUrls()
        {
            return imageUrls;
        }
    }
}
