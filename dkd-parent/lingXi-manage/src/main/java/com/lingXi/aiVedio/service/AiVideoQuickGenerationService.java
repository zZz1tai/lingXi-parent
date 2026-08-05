package com.lingXi.aiVedio.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.lingXi.aiVedio.domain.AiVideoAsset;
import com.lingXi.aiVedio.domain.AiVideoGenerationTask;
import com.lingXi.aiVedio.domain.AiVideoProject;
import com.lingXi.aiVedio.domain.dto.AiVideoQuickGenerationRequest;
import com.lingXi.aiVedio.domain.dto.AiVideoQuickGenerationResult;
import com.lingXi.aiVedio.domain.dto.AiVideoQuickGenerationStatus;
import com.lingXi.aiVedio.mapper.AiVideoAssetMapper;
import com.lingXi.aiVedio.mapper.AiVideoGenerationTaskMapper;
import com.lingXi.aiVedio.storage.AiVideoLocalAssetStorage;
import com.lingXi.aiVedio.storage.AiVideoLocalAssetStorage.StoredImage;
import com.lingXi.aiVedio.storage.AiVideoPublicAssetUrlResolver;
import com.lingXi.aiVedio.util.AiVideoReferenceImagePolicy;
import com.lingXi.common.exception.ServiceException;
import com.lingXi.common.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;

/**
 * 把 AI 对话页的文字描述和可选参考图转换为现有项目、资产和视频任务。
 * 有图片时第一张是起始关键帧，后续图片按上传顺序作为多图参考；无图片时走文生视频。
 */
@Service
@RequiredArgsConstructor
public class AiVideoQuickGenerationService
{
    private static final int MAX_IMAGE_COUNT = 5;
    private static final long MAX_IMAGE_BYTES = 10L * 1024L * 1024L;

    private final IAiVideoProjectService projectService;
    private final AiVideoAssetMapper assetMapper;
    private final AiVideoGenerationTaskMapper taskMapper;
    private final AiVideoGenerationService generationService;
    private final AiVideoLocalAssetStorage assetStorage;
    private final AiVideoPublicAssetUrlResolver publicAssetUrlResolver;
    private final ObjectMapper objectMapper;

    /** 创建可选参考资产并提交视频供应商任务。 */
    public AiVideoQuickGenerationResult submit(AiVideoQuickGenerationRequest request)
    {
        validateRequest(request);
        String prompt = request.getPrompt().trim();
        String username = SecurityUtils.getUsername();
        AiVideoProject project = createProject(prompt);
        List<String> storedPaths = new ArrayList<>();
        List<MultipartFile> images = request.getImages();
        if (images == null)
        {
            images = new ArrayList<>();
        }
        List<AiVideoAsset> uploadedAssets;
        AiVideoAsset keyframe;
        AiVideoAsset video;

        try
        {
            uploadedAssets = prepareUploadedAssets(project.getProjectId(), images,
                    username, storedPaths);
            keyframe = uploadedAssets.isEmpty() ? null : uploadedAssets.get(0);
            video = createVideoDraft(project.getProjectId(), keyframe, prompt,
                    request.getDurationMs(), username);
        }
        catch (RuntimeException exception)
        {
            cleanupFailedPreparation(project.getProjectId(), storedPaths);
            throw exception;
        }

        List<AiVideoAsset> references = uploadedAssets.size() > 1
                ? uploadedAssets.subList(1, uploadedAssets.size()) : new ArrayList<>();
        // 供应商提交可能已受理但本地未收到任务ID；进入该阶段后保留项目和资产供人工核对。
        try
        {
            Long taskId = generationService.submit(video, keyframe, references, username);
            return buildSubmissionResult(project.getProjectId(), video.getAssetId(), taskId);
        }
        catch (ServiceException exception)
        {
            AiVideoGenerationTask needsReviewTask = findNeedsReviewTask(
                    project.getProjectId(), video.getAssetId());
            if (needsReviewTask != null)
            {
                return buildSubmissionResult(project.getProjectId(), video.getAssetId(),
                        needsReviewTask.getTaskId());
            }
            throw exception;
        }
    }

    private AiVideoQuickGenerationResult buildSubmissionResult(Long projectId,
            Long videoAssetId, Long taskId)
    {
        AiVideoGenerationTask task = taskMapper.selectAiVideoGenerationTaskByTaskId(taskId);
        if (task == null || !projectId.equals(task.getProjectId())
                || !videoAssetId.equals(task.getAssetId()))
        {
            throw new ServiceException("视频任务创建后无法读取，请勿重复提交");
        }
        AiVideoQuickGenerationResult result = new AiVideoQuickGenerationResult();
        result.setProjectId(projectId);
        result.setVideoAssetId(videoAssetId);
        result.setTaskId(taskId);
        result.setStatus(task.getStatus());
        result.setProgress(task.getProgress());
        result.setErrorCode(task.getErrorCode());
        result.setErrorMessage(task.getErrorMessage());
        return result;
    }

    private AiVideoGenerationTask findNeedsReviewTask(Long projectId, Long videoAssetId)
    {
        List<AiVideoGenerationTask> tasks = taskMapper.selectAiVideoGenerationTaskList(projectId);
        if (tasks == null)
        {
            return null;
        }
        for (AiVideoGenerationTask task : tasks)
        {
            if (videoAssetId.equals(task.getAssetId())
                    && "VIDEO".equals(task.getTaskType())
                    && "NEEDS_REVIEW".equals(task.getStatus()))
            {
                return task;
            }
        }
        return null;
    }

    /** 返回当前用户可访问的快速视频任务状态。 */
    public AiVideoQuickGenerationStatus status(Long projectId, Long taskId)
    {
        if (projectId == null || taskId == null)
        {
            throw new ServiceException("视频任务参数无效");
        }
        projectService.checkProjectOwner(projectId);
        AiVideoGenerationTask task = taskMapper.selectAiVideoGenerationTaskByTaskId(taskId);
        if (task == null || !projectId.equals(task.getProjectId())
                || !"VIDEO".equals(task.getTaskType()))
        {
            throw new ServiceException("视频任务不存在或无权访问");
        }
        AiVideoAsset video = assetMapper.selectAiVideoAssetByAssetId(task.getAssetId());
        if (video == null || !projectId.equals(video.getProjectId())
                || !"VIDEO_CLIP".equals(video.getAssetType()))
        {
            throw new ServiceException("视频资产不存在或无权访问");
        }

        AiVideoQuickGenerationStatus result = new AiVideoQuickGenerationStatus();
        result.setProjectId(projectId);
        result.setVideoAssetId(video.getAssetId());
        result.setTaskId(taskId);
        result.setStatus(task.getStatus());
        result.setProgress(task.getProgress());
        result.setErrorCode(task.getErrorCode());
        result.setErrorMessage(task.getErrorMessage());
        result.setDurationMs(video.getDurationMs());
        if (video.getObjectKey() != null && !video.getObjectKey().trim().isEmpty())
        {
            result.setVideoUrl(publicAssetUrlResolver.resolve(video.getObjectKey()));
        }
        return result;
    }

    private AiVideoProject createProject(String prompt)
    {
        AiVideoProject project = new AiVideoProject();
        project.setProjectName("对话快创 · " + abbreviate(prompt, 36));
        project.setVisualStyle("AI 对话快速视频");
        project.setDefaultAspectRatio("16:9");
        if (projectService.insertAiVideoProject(project) != 1 || project.getProjectId() == null)
        {
            throw new ServiceException("快速视频项目创建失败");
        }
        return project;
    }

    private List<AiVideoAsset> prepareUploadedAssets(Long projectId, List<MultipartFile> images,
            String username, List<String> storedPaths)
    {
        List<AiVideoAsset> assets = new ArrayList<>();
        for (int index = 0; index < images.size(); index++)
        {
            MultipartFile image = images.get(index);
            byte[] imageBytes = validateImage(image, index);
            AiVideoAsset asset = createImageDraft(projectId, index, username);
            try
            {
                StoredImage stored = assetStorage.storeUploadedImage(projectId, asset.getAssetId(),
                        asset.getVersionNo(), asset.getAssetCode(), imageBytes);
                storedPaths.add(stored.getResourcePath());
                completeUploadedAsset(asset, stored, username, index == 0);
                AiVideoAsset completed = assetMapper.selectAiVideoAssetByAssetId(asset.getAssetId());
                if (completed == null)
                {
                    throw new ServiceException("参考图片资产保存失败");
                }
                assets.add(completed);
            }
            catch (IOException exception)
            {
                throw new ServiceException("读取参考图片失败：" + safeFilename(image, index));
            }
            catch (ServiceException exception)
            {
                throw exception;
            }
            catch (Exception exception)
            {
                throw new ServiceException("处理参考图片失败：" + safeFilename(image, index));
            }
        }
        return assets;
    }

    private AiVideoAsset createImageDraft(Long projectId, int index, String username)
    {
        boolean keyframe = index == 0;
        AiVideoAsset asset = new AiVideoAsset();
        asset.setProjectId(projectId);
        asset.setAssetCode(keyframe ? "quick-keyframe" : "quick-reference-" + index);
        asset.setAssetName(keyframe ? "对话快创起始帧" : "对话快创参考图 " + index);
        asset.setAssetType(keyframe ? "SHOT_KEYFRAME" : "CHARACTER_REFERENCE");
        asset.setAssetScope(keyframe ? "SHOT" : "PROJECT");
        asset.setCanonicalFlag(1);
        asset.setStatus("DRAFT");
        asset.setVersionNo(1);
        asset.setGenerationParamsJson(uploadMetadata(index).toString());
        asset.setMetadataJson(uploadMetadata(index).toString());
        asset.setCreateBy(username);
        if (assetMapper.insertAiVideoAsset(asset) != 1 || asset.getAssetId() == null)
        {
            throw new ServiceException("参考图片资产创建失败");
        }
        if (assetMapper.markDraftAiVideoAssetGenerating(asset.getAssetId(),
                asset.getGenerationParamsJson(), username) != 1)
        {
            throw new ServiceException("参考图片资产状态已变化，请重试");
        }
        return asset;
    }

    private void completeUploadedAsset(AiVideoAsset asset, StoredImage stored,
            String username, boolean keyframe)
    {
        asset.setStorageProvider(stored.getPlatform());
        asset.setObjectKey(stored.getResourcePath());
        asset.setPreviewObjectKey(stored.getResourcePath());
        asset.setMimeType("image/png");
        asset.setFileSize(stored.getSize());
        asset.setContentHash(stored.getSha256());
        asset.setWidth(stored.getWidth());
        asset.setHeight(stored.getHeight());
        asset.setUpdateBy(username);
        if (assetMapper.markAiVideoAssetGenerated(asset) != 1)
        {
            throw new ServiceException("参考图片转存状态已变化，请重试");
        }
        if (keyframe && assetMapper.approveAiVideoAsset(asset.getAssetId(), username) != 1)
        {
            throw new ServiceException("起始关键帧确认失败");
        }
    }

    private AiVideoAsset createVideoDraft(Long projectId, AiVideoAsset keyframe, String prompt,
            Integer durationMs, String username)
    {
        AiVideoAsset video = new AiVideoAsset();
        video.setProjectId(projectId);
        video.setAssetCode("quick-video");
        video.setAssetName("对话快创视频");
        video.setAssetType("VIDEO_CLIP");
        video.setAssetScope("PROJECT");
        video.setCanonicalFlag(1);
        video.setStatus("DRAFT");
        video.setVersionNo(1);
        if (keyframe != null)
        {
            video.setSourceAssetId(keyframe.getAssetId());
        }
        video.setDurationMs(durationMs);
        video.setPromptText(prompt);
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("source", "CHAT_QUICK_VIDEO");
        metadata.put("generationMode", keyframe == null ? "TEXT_TO_VIDEO" : "IMAGE_TO_VIDEO");
        metadata.put("sourceBindingMode", keyframe == null ? "NONE" : "MANUAL");
        if (keyframe != null)
        {
            metadata.put("sourceAssetId", keyframe.getAssetId());
        }
        metadata.put("durationMs", durationMs.intValue());
        video.setGenerationParamsJson(metadata.toString());
        video.setMetadataJson(metadata.toString());
        video.setCreateBy(username);
        if (assetMapper.insertAiVideoAsset(video) != 1 || video.getAssetId() == null)
        {
            throw new ServiceException("视频草稿创建失败");
        }
        return video;
    }

    private ObjectNode uploadMetadata(int index)
    {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("source", "CHAT_QUICK_VIDEO_UPLOAD");
        metadata.put("referenceOrder", index);
        metadata.put("userConfirmed", true);
        return metadata;
    }

    private void validateRequest(AiVideoQuickGenerationRequest request)
    {
        if (request == null || request.getPrompt() == null || request.getPrompt().trim().isEmpty())
        {
            throw new ServiceException("请输入视频画面描述");
        }
        if (request.getPrompt().trim().length() > 2500)
        {
            throw new ServiceException("视频画面描述不能超过2500个字符");
        }
        if (request.getDurationMs() == null || request.getDurationMs() < 1000
                || request.getDurationMs() > 15000)
        {
            throw new ServiceException("视频时长需在1到15秒之间");
        }
        if (request.getImages() != null && request.getImages().size() > MAX_IMAGE_COUNT)
        {
            throw new ServiceException("最多添加5张参考图片");
        }
    }

    byte[] validateImage(MultipartFile image, int index)
    {
        if (image == null || image.isEmpty())
        {
            throw new ServiceException("第" + (index + 1) + "张参考图片为空");
        }
        if (image.getSize() > MAX_IMAGE_BYTES)
        {
            throw new ServiceException(safeFilename(image, index) + "：单张图片不能超过10MB");
        }
        String filename = safeFilename(image, index);
        try
        {
            byte[] imageBytes = image.getBytes();
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (decoded == null)
            {
                throw new ServiceException(filename + "：无法读取图片内容，请选择有效的 PNG 或 JPG");
            }
            AiVideoReferenceImagePolicy.validateDimensions(
                    decoded.getWidth(), decoded.getHeight(), filename);
            return imageBytes;
        }
        catch (IOException exception)
        {
            throw new ServiceException(filename + "：无法读取图片内容，请重新选择");
        }
    }

    private void cleanupFailedPreparation(Long projectId, List<String> storedPaths)
    {
        for (String path : storedPaths)
        {
            try
            {
                assetStorage.delete(path);
            }
            catch (RuntimeException ignored)
            {
                // 数据库项目随后会被隐藏；存储清理由运维巡检继续兜底。
            }
        }
        try
        {
            projectService.deleteAiVideoProjectByProjectIds(new Long[] {projectId});
        }
        catch (RuntimeException ignored)
        {
            // 保留原始业务异常，避免清理失败覆盖真实原因。
        }
    }

    private static String safeFilename(MultipartFile image, int index)
    {
        String filename = image == null ? null : image.getOriginalFilename();
        if (filename == null || filename.trim().isEmpty())
        {
            return "第" + (index + 1) + "张图片";
        }
        String normalized = filename.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        normalized = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        return abbreviate(normalized, 80);
    }

    private static String abbreviate(String value, int maxCodePoints)
    {
        if (value == null)
        {
            return "";
        }
        int count = value.codePointCount(0, value.length());
        if (count <= maxCodePoints)
        {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxCodePoints)) + "…";
    }
}
