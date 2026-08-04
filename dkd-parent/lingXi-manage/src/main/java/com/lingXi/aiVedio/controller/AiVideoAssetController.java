package com.lingXi.aiVedio.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import com.lingXi.common.annotation.Log;
import com.lingXi.common.annotation.RepeatSubmit;
import com.lingXi.common.core.controller.BaseController;
import com.lingXi.common.core.domain.AjaxResult;
import com.lingXi.common.core.page.TableDataInfo;
import com.lingXi.common.enums.BusinessType;
import com.lingXi.aiVedio.domain.AiVideoAsset;
import com.lingXi.aiVedio.domain.dto.AiVideoAssetRegenerationDraftRequest;
import com.lingXi.aiVedio.domain.dto.AiVideoAssetPromptRequest;
import com.lingXi.aiVedio.domain.dto.AiVideoKeyframeReferenceBindingRequest;
import com.lingXi.aiVedio.domain.dto.AiVideoQuickGenerationRequest;
import com.lingXi.aiVedio.domain.dto.AiVideoVideoPromptRequest;
import com.lingXi.aiVedio.domain.dto.AiVideoVideoSourceBindingRequest;
import com.lingXi.aiVedio.domain.dto.AiVideoSubmissionResolutionRequest;
import com.lingXi.aiVedio.service.IAiVideoAssetService;
import com.lingXi.aiVedio.service.AiVideoQuickGenerationService;

/**
 * AI视频资产控制器
 * <p>
 * 提供AI视频资产的管理接口，包括资产列表查询、详情查看、审批、提示词管理、视频生成、版本管理等功能。
 * 支持关键帧和视频资产的完整生命周期管理。
 * </p>
 *
 * @author lingXi
 * @since 2026-07-23
 */
@RestController
@RequestMapping("/aivideo/asset")
public class AiVideoAssetController extends BaseController
{
    @Autowired
    private IAiVideoAssetService assetService;

    @Autowired
    private AiVideoQuickGenerationService quickGenerationService;

    /**
     * 获取AI视频资产列表
     * <p>
     * 根据查询条件获取AI视频资产的分页列表，支持按资产类型、状态等条件进行筛选。
     * </p>
     *
     * @param asset 查询条件对象
     * @return 包含资产列表的分页数据
     */
    @PreAuthorize("@ss.hasPermi('aivideo:asset:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiVideoAsset asset)
    {
        startPage();
        List<AiVideoAsset> list = assetService.selectAiVideoAssetList(asset);
        return getDataTable(list);
    }

    /**
     * 根据ID获取AI视频资产详情
     * <p>
     * 通过资产ID获取资产的详细信息，包括资产基本信息、版本信息等。
     * </p>
     *
     * @param assetId 资产ID
     * @return 包含资产详情的结果对象
     */
    @PreAuthorize("@ss.hasPermi('aivideo:asset:query')")
    @GetMapping("/{assetId}")
    public AjaxResult getInfo(@PathVariable Long assetId)
    {
        return success(assetService.selectAiVideoAssetByAssetId(assetId));
    }

    /**
     * 审批AI视频关键帧
     * <p>
     * 对指定的关键帧资产进行审批操作，审批通过后可以用于后续的视频生成。
     * 操作会记录日志信息，支持防重复提交。
     * </p>
     *
     * @param assetId 资产ID
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "AI视频关键帧审批", businessType = BusinessType.UPDATE)
    @RepeatSubmit(message = "该关键帧正在审批，请勿重复操作")
    @PostMapping("/{assetId}/approve")
    public AjaxResult approve(@PathVariable Long assetId)
    {
        assetService.approveAiVideoAsset(assetId);
        return success();
    }

    /**
     * 更新图片提示词
     * <p>
     * 更新指定资产的图片生成提示词，包括正向提示词和负向提示词。
     * 操作会记录日志信息。
     * </p>
     *
     * @param assetId 资产ID
     * @param request 提示词请求对象
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "AI视频图片提示词", businessType = BusinessType.UPDATE)
    @PutMapping("/{assetId}/prompt")
    public AjaxResult updatePrompt(@PathVariable Long assetId, @RequestBody AiVideoAssetPromptRequest request)
    {
        assetService.updateImagePrompt(assetId, request.getPromptText(), request.getNegativePromptText());
        return success();
    }

    /**
     * 创建视频提示词草稿
     * <p>
     * 基于关键帧资产创建视频提示词草稿，系统将自动生成适合视频生成的提示词。
     * 操作会记录日志信息，支持防重复提交。
     * </p>
     *
     * @param keyframeAssetId 关键帧资产ID
     * @return 包含草稿ID的结果对象
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "AI视频提示词草稿", businessType = BusinessType.INSERT)
    @RepeatSubmit(message = "视频提示词草稿正在生成，请勿重复操作")
    @PostMapping("/{keyframeAssetId}/video-draft")
    public AjaxResult createVideoDraft(@PathVariable Long keyframeAssetId)
    {
        return success(assetService.createVideoPromptDraft(keyframeAssetId));
    }

    /**
     * 更新视频提示词
     * <p>
     * 更新视频资产的提示词信息，包括正向提示词、负向提示词和视频时长。
     * 操作会记录日志信息。
     * </p>
     *
     * @param videoAssetId 视频资产ID
     * @param request      视频提示词请求对象
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "AI视频提示词", businessType = BusinessType.UPDATE)
    @PutMapping("/{videoAssetId}/video-prompt")
    public AjaxResult updateVideoPrompt(@PathVariable Long videoAssetId,
            @RequestBody AiVideoVideoPromptRequest request)
    {
        return success(assetService.updateVideoPrompt(videoAssetId, request.getPromptText(),
                request.getNegativePromptText(), request.getDurationMs()));
    }

    /**
     * 创建资产新版本草稿
     * <p>
     * 为指定资产创建新的版本草稿，用于重新生成资产内容。
     * 操作会记录日志信息，支持防重复提交。
     * </p>
     *
     * @param assetId 资产ID
     * @param request 重新生成草稿请求对象（可选）
     * @return 包含新草稿ID的结果对象
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "AI视频资产新版本草稿", businessType = BusinessType.INSERT)
    @RepeatSubmit(message = "新版本草稿正在创建，请勿重复操作")
    @PostMapping("/{assetId}/regeneration-draft")
    public AjaxResult createRegenerationDraft(@PathVariable Long assetId,
            @RequestBody(required = false) AiVideoAssetRegenerationDraftRequest request)
    {
        return success(assetService.createRegenerationDraft(assetId, request));
    }

    /**
     * 获取关键帧参考绑定
     * <p>
     * 获取指定关键帧资产的参考版本绑定信息，用于了解该关键帧与其他版本的关联关系。
     * </p>
     *
     * @param assetId 资产ID
     * @return 包含参考绑定信息的结果对象
     */
    @PreAuthorize("@ss.hasPermi('aivideo:asset:query')")
    @GetMapping("/{assetId}/references")
    public AjaxResult getKeyframeReferences(@PathVariable Long assetId)
    {
        return success(assetService.getKeyframeReferenceBinding(assetId));
    }

    /**
     * 更新关键帧参考版本绑定
     * <p>
     * 更新指定关键帧资产的参考版本绑定关系，可以手动选择参考的版本。
     * 操作会记录日志信息，支持防重复提交。
     * </p>
     *
     * @param assetId 资产ID
     * @param request 参考版本绑定请求对象
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "AI视频关键帧参考版本绑定", businessType = BusinessType.UPDATE)
    @RepeatSubmit(message = "关键帧参考版本正在更新，请勿重复操作")
    @PutMapping("/{assetId}/references")
    public AjaxResult updateKeyframeReferences(@PathVariable Long assetId,
            @RequestBody AiVideoKeyframeReferenceBindingRequest request)
    {
        return success(assetService.updateKeyframeReferenceBinding(assetId, request));
    }

    /**
     * 恢复关键帧自动绑定
     * <p>
     * 将关键帧的参考版本绑定恢复为系统自动绑定模式。
     * 操作会记录日志信息，支持防重复提交。
     * </p>
     *
     * @param assetId 资产ID
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "AI视频关键帧恢复自动绑定", businessType = BusinessType.UPDATE)
    @RepeatSubmit(message = "关键帧正在恢复自动绑定，请勿重复操作")
    @PostMapping("/{assetId}/references/reset-auto")
    public AjaxResult resetKeyframeReferences(@PathVariable Long assetId)
    {
        return success(assetService.resetKeyframeReferenceBinding(assetId));
    }

    /**
     * 获取视频来源关键帧
     * <p>
     * 获取视频资产的来源关键帧绑定信息，用于了解该视频是基于哪个关键帧生成的。
     * </p>
     *
     * @param videoAssetId 视频资产ID
     * @return 包含来源关键帧信息的结果对象
     */
    @PreAuthorize("@ss.hasPermi('aivideo:asset:query')")
    @GetMapping("/{videoAssetId}/source-keyframe")
    public AjaxResult getVideoSourceKeyframe(@PathVariable Long videoAssetId)
    {
        return success(assetService.getVideoSourceBinding(videoAssetId));
    }

    /**
     * 更新视频来源关键帧绑定
     * <p>
     * 更新视频资产的来源关键帧绑定关系，可以手动选择生成该视频的关键帧。
     * 操作会记录日志信息，支持防重复提交。
     * </p>
     *
     * @param videoAssetId 视频资产ID
     * @param request      来源关键帧绑定请求对象
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "AI视频来源关键帧版本绑定", businessType = BusinessType.UPDATE)
    @RepeatSubmit(message = "视频来源关键帧正在更新，请勿重复操作")
    @PutMapping("/{videoAssetId}/source-keyframe")
    public AjaxResult updateVideoSourceKeyframe(@PathVariable Long videoAssetId,
            @RequestBody AiVideoVideoSourceBindingRequest request)
    {
        return success(assetService.updateVideoSourceBinding(videoAssetId, request));
    }

    /**
     * 恢复视频自动关键帧绑定
     * <p>
     * 将视频的来源关键帧绑定恢复为系统自动绑定模式。
     * 操作会记录日志信息，支持防重复提交。
     * </p>
     *
     * @param videoAssetId 视频资产ID
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "AI视频恢复自动关键帧绑定", businessType = BusinessType.UPDATE)
    @RepeatSubmit(message = "视频正在恢复自动关键帧绑定，请勿重复操作")
    @PostMapping("/{videoAssetId}/source-keyframe/reset-auto")
    public AjaxResult resetVideoSourceKeyframe(@PathVariable Long videoAssetId)
    {
        return success(assetService.resetVideoSourceBinding(videoAssetId));
    }

    /**
     * 删除AI视频资产
     * <p>
     * 删除指定的AI视频资产，删除后不可恢复。
     * 操作会记录日志信息，支持防重复提交。
     * </p>
     *
     * @param assetId 资产ID
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "AI视频资产删除", businessType = BusinessType.DELETE)
    @RepeatSubmit(message = "资产正在删除，请勿重复操作")
    @DeleteMapping("/{assetId}")
    public AjaxResult remove(@PathVariable Long assetId)
    {
        assetService.deleteAiVideoAsset(assetId);
        return success();
    }

    /**
     * 切换AI视频资产版本
     * <p>
     * 将指定资产的某个版本设置为当前激活版本，用于切换不同的生成结果。
     * 操作会记录日志信息，支持防重复提交。
     * </p>
     *
     * @param assetId 资产ID
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "AI视频资产版本切换", businessType = BusinessType.UPDATE)
    @RepeatSubmit(message = "资产版本正在切换，请勿重复操作")
    @PostMapping("/{assetId}/activate")
    public AjaxResult activate(@PathVariable Long assetId)
    {
        assetService.activateAiVideoAssetVersion(assetId);
        return success();
    }

    /**
     * 生成AI图片
     * <p>
     * 启动AI图片生成任务，根据资产的提示词生成对应的图片。
     * 操作会记录日志信息，支持防重复提交（间隔30秒）。
     * </p>
     *
     * @param assetId 资产ID
     * @return 包含任务ID的结果对象
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "AI视频图片生成", businessType = BusinessType.OTHER)
    @RepeatSubmit(interval = 30000, message = "图片生成任务正在提交，请勿重复操作")
    @PostMapping("/{assetId}/generate-image")
    public AjaxResult generateImage(@PathVariable Long assetId)
    {
        return success().put("taskId", assetService.startImageGeneration(assetId));
    }

    /**
     * 重试AI图片生成
     * <p>
     * 重新尝试生成失败的AI图片任务。
     * 操作会记录日志信息，支持防重复提交（间隔30秒）。
     * </p>
     *
     * @param assetId 资产ID
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "AI视频图片重试", businessType = BusinessType.OTHER)
    @RepeatSubmit(interval = 30000, message = "图片重试任务正在提交，请勿重复操作")
    @PostMapping("/{assetId}/retry-image")
    public AjaxResult retryImage(@PathVariable Long assetId)
    {
        assetService.retryImageGeneration(assetId);
        return success();
    }

    /**
     * 生成AI视频
     * <p>
     * 启动AI视频生成任务，根据关键帧和提示词生成对应的视频。
     * 操作会记录日志信息，支持防重复提交（间隔30秒）。
     * </p>
     *
     * @param videoAssetId 视频资产ID
     * @return 包含任务ID的结果对象
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "AI视频生成", businessType = BusinessType.OTHER)
    @RepeatSubmit(interval = 30000, message = "视频任务正在提交，请勿重复操作")
    @PostMapping("/{videoAssetId}/video")
    public AjaxResult generateVideo(@PathVariable Long videoAssetId)
    {
        return success().put("taskId", assetService.startVideoGeneration(videoAssetId));
    }

    /**
     * 从 AI 对话页提交一组用户参考图，快速创建并生成视频。
     * 第一张图作为起始关键帧，其余图片作为有序参考图。
     *
     * @param request 视频描述、时长和参考图片
     * @return 快速项目、视频资产和任务ID
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "AI对话快速视频生成", businessType = BusinessType.OTHER)
    @RepeatSubmit(interval = 30000, message = "视频任务正在提交，请勿重复操作")
    @PostMapping(value = "/quick-video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AjaxResult generateQuickVideo(
            @Validated @ModelAttribute AiVideoQuickGenerationRequest request)
    {
        return success(quickGenerationService.submit(request));
    }

    /** 查询当前用户的快速视频任务状态。 */
    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @GetMapping("/quick-video/{projectId}/{taskId}")
    public AjaxResult getQuickVideoStatus(@PathVariable Long projectId, @PathVariable Long taskId)
    {
        return success(quickGenerationService.status(projectId, taskId));
    }

    /**
     * 处理视频供应商任务核对
     * <p>
     * 处理视频生成供应商的任务核对请求，可以接受或拒绝供应商提交的视频结果。
     * 操作会记录日志信息，支持防重复提交。
     * </p>
     *
     * @param videoAssetId 视频资产ID
     * @param request      核对请求对象
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "AI视频供应商任务核对", businessType = BusinessType.UPDATE)
    @RepeatSubmit(message = "视频供应商任务核对正在处理，请勿重复操作")
    @PostMapping("/{videoAssetId}/video-resolution")
    public AjaxResult resolveVideoSubmission(@PathVariable Long videoAssetId,
            @RequestBody AiVideoSubmissionResolutionRequest request)
    {
        assetService.resolveVideoSubmission(videoAssetId, request.getAction(), request.getProviderTaskId());
        return success();
    }
}
