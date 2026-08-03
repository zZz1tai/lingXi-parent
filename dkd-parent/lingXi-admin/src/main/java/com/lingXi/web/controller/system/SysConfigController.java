package com.lingXi.web.controller.system;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lingXi.common.annotation.Log;
import com.lingXi.common.core.controller.BaseController;
import com.lingXi.common.core.domain.AjaxResult;
import com.lingXi.common.core.page.TableDataInfo;
import com.lingXi.common.enums.BusinessType;
import com.lingXi.common.utils.poi.ExcelUtil;
import com.lingXi.system.config.SystemSecurityConfigService;
import com.lingXi.system.domain.SysConfig;
import com.lingXi.system.service.ISysConfigService;

/**
 * 参数配置 信息操作处理
 * 
 * @author ruoyi
 */
@RestController
@RequestMapping("/system/config")
public class SysConfigController extends BaseController
{
    private static final String AI_VIDEO_API_KEY = "aivideo.model.apiKey";
    private static final String MASKED_SECRET = "******";

    @Autowired
    private ISysConfigService configService;

    /**
     * 判断是否为敏感配置键（AI视频模型配置或系统安全配置）。
     */
    private boolean isSensitiveKey(String configKey)
    {
        return AI_VIDEO_API_KEY.equals(configKey)
                || SystemSecurityConfigService.SENSITIVE_KEYS.contains(configKey);
    }

    /**
     * 获取参数配置列表
     */
    @PreAuthorize("@ss.hasPermi('system:config:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysConfig config)
    {
        startPage();
        List<SysConfig> list = configService.selectConfigList(config);
        maskSensitiveConfigs(list);
        return getDataTable(list);
    }

    @Log(title = "参数管理", businessType = BusinessType.EXPORT)
    @PreAuthorize("@ss.hasPermi('system:config:export')")
    @PostMapping("/export")
    public void export(HttpServletResponse response, SysConfig config)
    {
        List<SysConfig> list = configService.selectConfigList(config);
        maskSensitiveConfigs(list);
        ExcelUtil<SysConfig> util = new ExcelUtil<SysConfig>(SysConfig.class);
        util.exportExcel(response, list, "参数数据");
    }

    /**
     * 根据参数编号获取详细信息
     */
    @PreAuthorize("@ss.hasPermi('system:config:query')")
    @GetMapping(value = "/{configId}")
    public AjaxResult getInfo(@PathVariable Long configId)
    {
        SysConfig config = configService.selectConfigById(configId);
        maskSensitiveConfig(config);
        return success(config);
    }

    /**
     * 根据参数键名查询参数值
     */
    @GetMapping(value = "/configKey/{configKey}")
    public AjaxResult getConfigKey(@PathVariable String configKey)
    {
        if (isSensitiveKey(configKey))
        {
            return success(MASKED_SECRET);
        }
        return success(configService.selectConfigByKey(configKey));
    }

    /**
     * 新增参数配置
     */
    @PreAuthorize("@ss.hasPermi('system:config:add')")
    @Log(title = "参数管理", businessType = BusinessType.INSERT,
            isSaveRequestData = false)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody SysConfig config)
    {
        if (isSensitiveKey(config.getConfigKey()))
        {
            return error("敏感配置项只能在专用配置页面维护");
        }
        if (!configService.checkConfigKeyUnique(config))
        {
            return error("新增参数'" + config.getConfigName() + "'失败，参数键名已存在");
        }
        config.setCreateBy(getUsername());
        return toAjax(configService.insertConfig(config));
    }

    /**
     * 修改参数配置
     */
    @PreAuthorize("@ss.hasPermi('system:config:edit')")
    @Log(title = "参数管理", businessType = BusinessType.UPDATE,
            isSaveRequestData = false)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody SysConfig config)
    {
        SysConfig existing = configService.selectConfigById(config.getConfigId());
        if (isSensitiveKey(config.getConfigKey()) || isSensitiveConfig(existing))
        {
            return error("敏感配置项只能在专用配置页面维护");
        }
        if (!configService.checkConfigKeyUnique(config))
        {
            return error("修改参数'" + config.getConfigName() + "'失败，参数键名已存在");
        }
        config.setUpdateBy(getUsername());
        return toAjax(configService.updateConfig(config));
    }

    /**
     * 删除参数配置
     */
    @PreAuthorize("@ss.hasPermi('system:config:remove')")
    @Log(title = "参数管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{configIds}")
    public AjaxResult remove(@PathVariable Long[] configIds)
    {
        for (Long configId : configIds)
        {
            if (isSensitiveConfig(configService.selectConfigById(configId)))
            {
                return error("敏感配置项只能在专用配置页面维护");
            }
        }
        configService.deleteConfigByIds(configIds);
        return success();
    }

    /**
     * 刷新参数缓存
     */
    @PreAuthorize("@ss.hasPermi('system:config:remove')")
    @Log(title = "参数管理", businessType = BusinessType.CLEAN)
    @DeleteMapping("/refreshCache")
    public AjaxResult refreshCache()
    {
        configService.resetConfigCache();
        return success();
    }

    private void maskSensitiveConfigs(List<SysConfig> configs)
    {
        if (configs == null) return;
        for (SysConfig config : configs)
        {
            maskSensitiveConfig(config);
        }
    }

    private void maskSensitiveConfig(SysConfig config)
    {
        if (isSensitiveConfig(config))
        {
            config.setConfigValue(MASKED_SECRET);
        }
    }

    private boolean isSensitiveConfig(SysConfig config)
    {
        return config != null && isSensitiveKey(config.getConfigKey());
    }
}
