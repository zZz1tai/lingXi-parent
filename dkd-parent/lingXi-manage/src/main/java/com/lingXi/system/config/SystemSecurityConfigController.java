package com.lingXi.system.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lingXi.common.annotation.Log;
import com.lingXi.common.core.controller.BaseController;
import com.lingXi.common.core.domain.AjaxResult;
import com.lingXi.common.enums.BusinessType;
import com.lingXi.common.utils.SecurityUtils;

/**
 * 系统安全配置控制器
 * <p>
 * 提供阿里云OSS、Redis密码、Token密钥、Agent API Key等敏感配置的管理接口。
 * 敏感字段仅在写入时接受明文，读取时以掩码返回。
 * </p>
 */
@RestController
@RequestMapping("/system/security-config")
public class SystemSecurityConfigController extends BaseController
{
    @Autowired
    private SystemSecurityConfigService securityConfigService;

    /**
     * 获取系统安全配置（敏感字段以掩码返回）
     */
    @PreAuthorize("@ss.hasPermi('system:config:edit')")
    @GetMapping
    public AjaxResult getConfig()
    {
        return success(securityConfigService.getConfig());
    }

    /**
     * 更新系统安全配置
     */
    @PreAuthorize("@ss.hasPermi('system:config:edit')")
    @Log(title = "系统安全配置", businessType = BusinessType.UPDATE,
            isSaveRequestData = false)
    @PutMapping
    public AjaxResult updateConfig(@RequestBody SystemSecurityConfig config)
    {
        return success(securityConfigService.updateConfig(config, SecurityUtils.getUsername()));
    }
}
