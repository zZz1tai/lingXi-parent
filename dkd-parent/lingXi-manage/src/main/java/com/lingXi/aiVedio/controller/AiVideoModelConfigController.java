package com.lingXi.aiVedio.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lingXi.aiVedio.config.AiVideoModelConfigService;
import com.lingXi.aiVedio.domain.dto.AiVideoModelConfig;
import com.lingXi.common.annotation.Log;
import com.lingXi.common.core.controller.BaseController;
import com.lingXi.common.core.domain.AjaxResult;
import com.lingXi.common.enums.BusinessType;
import com.lingXi.common.utils.SecurityUtils;

/**
 * AI视频模型配置控制器
 * <p>
 * 提供AI视频模型运行时配置的管理接口，包括配置查看和更新功能。
 * 用于管理文本、图片和视频生成模型的相关参数配置。
 * </p>
 *
 * @author lingXi
 * @since 2026-07-23
 */
@RestController
@RequestMapping("/aivideo/model-config")
public class AiVideoModelConfigController extends BaseController
{
    @Autowired
    private AiVideoModelConfigService modelConfigService;

    /**
     * 获取AI视频模型配置
     * <p>
     * 获取当前系统的AI视频模型运行时配置信息，包括各模型的参数设置。
     * </p>
     *
     * @return 包含模型配置信息的结果对象
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @GetMapping
    public AjaxResult getConfig()
    {
        return success(modelConfigService.getConfig());
    }

    /**
     * 更新AI视频模型配置
     * <p>
     * 更新系统的AI视频模型运行时配置信息，操作会记录日志。
     * </p>
     *
     * @param config 新的模型配置信息
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "AI视频模型配置", businessType = BusinessType.UPDATE,
            isSaveRequestData = false)
    @PutMapping
    public AjaxResult updateConfig(@RequestBody AiVideoModelConfig config)
    {
        return success(modelConfigService.updateConfig(config, SecurityUtils.getUsername()));
    }
}
