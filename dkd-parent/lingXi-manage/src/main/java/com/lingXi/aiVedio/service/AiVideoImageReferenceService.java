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

/** 统一校验并解析 Qwen Image 分镜参考图，避免入队与执行阶段规则漂移。 */
@Service
public class AiVideoImageReferenceService
{
    private static final long MAX_REFERENCE_IMAGE_BYTES = 10L * 1024L * 1024L;

    @Autowired
    private AiVideoAssetRelationMapper relationMapper;
    @Autowired
    private AiVideoPublicAssetUrlResolver publicAssetUrlResolver;

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
        if (characterReferences.size() > 2)
        {
            throw new ServiceException("Qwen Image 最多支持2张人物参考图加1张场景参考图");
        }

        List<Long> assetIds = new ArrayList<>(characterReferences.size() + 1);
        List<String> imageUrls = new ArrayList<>(characterReferences.size() + 1);
        Set<Long> uniqueAssetIds = new LinkedHashSet<>();
        for (AiVideoAsset characterReference : characterReferences)
        {
            addResolvedReference(characterReference, uniqueAssetIds, assetIds, imageUrls);
        }
        // 官方多图接口以最后一张输入图决定输出比例，因此场景图必须最后发送。
        addResolvedReference(sceneReference, uniqueAssetIds, assetIds, imageUrls);
        return new ResolvedImageReferences(assetIds, imageUrls);
    }

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
        if (reference.getFileSize().longValue() > MAX_REFERENCE_IMAGE_BYTES)
        {
            throw new ServiceException("参考图超过10MB：" + referenceName(reference));
        }
    }

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
            throw new ServiceException("参考图不是可供 DashScope 访问的公网HTTP(S)地址："
                    + referenceName(reference));
        }
    }

    private String referenceName(AiVideoAsset reference)
    {
        return reference.getAssetName() == null || reference.getAssetName().trim().isEmpty()
                ? "资产" + reference.getAssetId() : reference.getAssetName();
    }

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

        public static ResolvedImageReferences empty()
        {
            return EMPTY;
        }

        public List<Long> getAssetIds()
        {
            return assetIds;
        }

        public List<String> getImageUrls()
        {
            return imageUrls;
        }
    }
}
