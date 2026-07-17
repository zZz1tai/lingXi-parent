package com.lingXi.aiVedio.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lingXi.common.annotation.Log;
import com.lingXi.common.annotation.RepeatSubmit;
import com.lingXi.common.core.controller.BaseController;
import com.lingXi.common.core.domain.AjaxResult;
import com.lingXi.common.core.page.TableDataInfo;
import com.lingXi.common.enums.BusinessType;
import com.lingXi.aiVedio.domain.AiVideoAsset;
import com.lingXi.aiVedio.domain.dto.AiVideoAssetRegenerationDraftRequest;
import com.lingXi.aiVedio.domain.dto.AiVideoAssetPromptRequest;
import com.lingXi.aiVedio.domain.dto.AiVideoVideoPromptRequest;
import com.lingXi.aiVedio.domain.dto.AiVideoWanxSubmissionResolutionRequest;
import com.lingXi.aiVedio.service.IAiVideoAssetService;

/** AI 视频资产查询 */
@RestController
@RequestMapping("/aivideo/asset")
public class AiVideoAssetController extends BaseController
{
    @Autowired
    private IAiVideoAssetService assetService;

    @PreAuthorize("@ss.hasPermi('aivideo:asset:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiVideoAsset asset)
    {
        startPage();
        List<AiVideoAsset> list = assetService.selectAiVideoAssetList(asset);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('aivideo:asset:query')")
    @GetMapping("/{assetId}")
    public AjaxResult getInfo(@PathVariable Long assetId)
    {
        return success(assetService.selectAiVideoAssetByAssetId(assetId));
    }

    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "AI视频关键帧审批", businessType = BusinessType.UPDATE)
    @RepeatSubmit(message = "该关键帧正在审批，请勿重复操作")
    @PostMapping("/{assetId}/approve")
    public AjaxResult approve(@PathVariable Long assetId)
    {
        assetService.approveAiVideoAsset(assetId);
        return success();
    }

    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "AI视频图片提示词", businessType = BusinessType.UPDATE)
    @PutMapping("/{assetId}/prompt")
    public AjaxResult updatePrompt(@PathVariable Long assetId, @RequestBody AiVideoAssetPromptRequest request)
    {
        assetService.updateImagePrompt(assetId, request.getPromptText(), request.getNegativePromptText());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "AI视频提示词草稿", businessType = BusinessType.INSERT)
    @RepeatSubmit(message = "视频提示词草稿正在生成，请勿重复操作")
    @PostMapping("/{keyframeAssetId}/video-draft")
    public AjaxResult createVideoDraft(@PathVariable Long keyframeAssetId)
    {
        return success(assetService.createVideoPromptDraft(keyframeAssetId));
    }

    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "AI视频提示词", businessType = BusinessType.UPDATE)
    @PutMapping("/{videoAssetId}/video-prompt")
    public AjaxResult updateVideoPrompt(@PathVariable Long videoAssetId,
            @RequestBody AiVideoVideoPromptRequest request)
    {
        return success(assetService.updateVideoPrompt(videoAssetId, request.getPromptText(),
                request.getNegativePromptText(), request.getDurationMs()));
    }

    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "AI视频资产新版本草稿", businessType = BusinessType.INSERT)
    @RepeatSubmit(message = "新版本草稿正在创建，请勿重复操作")
    @PostMapping("/{assetId}/regeneration-draft")
    public AjaxResult createRegenerationDraft(@PathVariable Long assetId,
            @RequestBody(required = false) AiVideoAssetRegenerationDraftRequest request)
    {
        return success(assetService.createRegenerationDraft(assetId, request));
    }

    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "AI视频资产删除", businessType = BusinessType.DELETE)
    @RepeatSubmit(message = "资产正在删除，请勿重复操作")
    @DeleteMapping("/{assetId}")
    public AjaxResult remove(@PathVariable Long assetId)
    {
        assetService.deleteAiVideoAsset(assetId);
        return success();
    }

    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "AI视频图片生成", businessType = BusinessType.OTHER)
    @RepeatSubmit(interval = 30000, message = "图片生成任务正在提交，请勿重复操作")
    @PostMapping("/{assetId}/generate-image")
    public AjaxResult generateImage(@PathVariable Long assetId)
    {
        return success().put("taskId", assetService.startImageGeneration(assetId));
    }

    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "AI视频图片重试", businessType = BusinessType.OTHER)
    @RepeatSubmit(interval = 30000, message = "图片重试任务正在提交，请勿重复操作")
    @PostMapping("/{assetId}/retry-image")
    public AjaxResult retryImage(@PathVariable Long assetId)
    {
        assetService.retryImageGeneration(assetId);
        return success();
    }

    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "AI视频生成", businessType = BusinessType.OTHER)
    @RepeatSubmit(interval = 30000, message = "视频任务正在提交，请勿重复操作")
    @PostMapping("/{videoAssetId}/video")
    public AjaxResult generateVideo(@PathVariable Long videoAssetId)
    {
        return success().put("taskId", assetService.startVideoGeneration(videoAssetId));
    }

    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "AI视频Wanx任务核对", businessType = BusinessType.UPDATE)
    @RepeatSubmit(message = "Wanx任务核对正在处理，请勿重复操作")
    @PostMapping("/{videoAssetId}/video-resolution")
    public AjaxResult resolveVideoSubmission(@PathVariable Long videoAssetId,
            @RequestBody AiVideoWanxSubmissionResolutionRequest request)
    {
        assetService.resolveWanxSubmission(videoAssetId, request.getAction(), request.getProviderTaskId());
        return success();
    }
}
