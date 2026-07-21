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

/** AI 视频文本、图片和视频模型运行时配置。 */
@RestController
@RequestMapping("/aivideo/model-config")
public class AiVideoModelConfigController extends BaseController
{
    @Autowired
    private AiVideoModelConfigService modelConfigService;

    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @GetMapping
    public AjaxResult getConfig()
    {
        return success(modelConfigService.getConfig());
    }

    @PreAuthorize("@ss.hasPermi('aivideo:project:edit')")
    @Log(title = "AI视频模型配置", businessType = BusinessType.UPDATE,
            isSaveRequestData = false)
    @PutMapping
    public AjaxResult updateConfig(@RequestBody AiVideoModelConfig config)
    {
        return success(modelConfigService.updateConfig(config, SecurityUtils.getUsername()));
    }
}
