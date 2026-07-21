package com.lingXi.aiVedio.config;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.lingXi.aiVedio.domain.dto.AiVideoModelConfig;
import com.lingXi.common.exception.ServiceException;
import com.lingXi.common.utils.StringUtils;
import com.lingXi.system.domain.SysConfig;
import com.lingXi.system.service.ISysConfigService;

/**
 * 从 sys_config 读取 AI 生产链路的运行时配置。
 * 数据库是唯一配置源；API Key 仅在专用接口中以掩码返回。
 */
@Service
public class AiVideoModelConfigService
{
    private static final String KEY_WORKSPACE_BASE_URL = "aivideo.model.workspaceBaseUrl";
    private static final String KEY_API_KEY = "aivideo.model.apiKey";
    private static final String KEY_TEXT_MODEL = "aivideo.model.textModel";
    private static final String KEY_IMAGE_MODEL = "aivideo.model.imageModel";
    private static final String KEY_VIDEO_MODEL = "aivideo.model.videoModel";
    private static final String KEY_VIDEO_RESOLUTION = "aivideo.model.videoResolution";
    private static final String KEY_VIDEO_RATIO = "aivideo.model.videoRatio";
    private static final String KEY_VIDEO_WATERMARK = "aivideo.model.videoWatermark";

    private static final Pattern MODEL_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    private static final Set<String> VIDEO_RESOLUTIONS = new HashSet<>(Arrays.asList("720P", "1080P"));
    private static final Set<String> VIDEO_RATIOS = new HashSet<>(Arrays.asList(
            "16:9", "9:16", "3:4", "4:3", "4:5", "5:4", "1:1", "9:21", "21:9"));

    @Autowired
    private ISysConfigService sysConfigService;
    @Autowired
    private AiVideoProviderProperties videoProviderProperties;

    /** 页面读取使用，不把 API Key 明文放入返回对象。 */
    public AiVideoModelConfig getConfig()
    {
        return readStoredConfig(false);
    }

    /** 模型调用使用；缺少任一页面配置时拒绝启动新任务。 */
    public AiVideoModelConfig getRequiredConfig()
    {
        AiVideoModelConfig config = readStoredConfig(true);
        validateStoredConfig(config);
        return config;
    }

    @Transactional
    public AiVideoModelConfig updateConfig(AiVideoModelConfig input, String username)
    {
        if (input == null)
        {
            throw new ServiceException("模型配置不能为空");
        }
        AiVideoModelConfig normalized = validateAndNormalize(input);
        String storedApiKey = read(KEY_API_KEY);
        String replacementApiKey = normalizeOptionalApiKey(input.getApiKey());
        if (replacementApiKey == null && StringUtils.isEmpty(storedApiKey))
        {
            throw new ServiceException("请填写 API Key");
        }
        upsert(KEY_WORKSPACE_BASE_URL, "AI视频-业务空间地址", normalized.getWorkspaceBaseUrl(), username);
        if (replacementApiKey != null)
        {
            upsert(KEY_API_KEY, "AI服务-API Key（敏感）", replacementApiKey, username);
        }
        upsert(KEY_TEXT_MODEL, "AI视频-章节分析模型", normalized.getTextModel(), username);
        upsert(KEY_IMAGE_MODEL, "AI视频-图片生成模型", normalized.getImageModel(), username);
        upsert(KEY_VIDEO_MODEL, "AI视频-视频生成模型", normalized.getVideoModel(), username);
        upsert(KEY_VIDEO_RESOLUTION, "AI视频-视频分辨率", normalized.getVideoResolution(), username);
        upsert(KEY_VIDEO_RATIO, "AI视频-视频比例", normalized.getVideoRatio(), username);
        upsert(KEY_VIDEO_WATERMARK, "AI视频-视频水印",
                String.valueOf(normalized.getVideoWatermark()), username);
        return getConfig();
    }

    private AiVideoModelConfig readStoredConfig(boolean includeSecret)
    {
        AiVideoModelConfig config = new AiVideoModelConfig();
        config.setWorkspaceBaseUrl(read(KEY_WORKSPACE_BASE_URL));
        config.setTextModel(read(KEY_TEXT_MODEL));
        config.setImageModel(read(KEY_IMAGE_MODEL));
        config.setVideoProvider(videoProviderProperties.getProvider());
        config.setVideoModel(read(KEY_VIDEO_MODEL));
        config.setVideoResolution(read(KEY_VIDEO_RESOLUTION));
        config.setVideoRatio(read(KEY_VIDEO_RATIO));
        config.setVideoWatermark(parseBoolean(read(KEY_VIDEO_WATERMARK)));
        String storedApiKey = read(KEY_API_KEY);
        boolean apiKeyConfigured = StringUtils.isNotEmpty(storedApiKey);
        config.setApiKeyConfigured(Boolean.valueOf(apiKeyConfigured));
        if (apiKeyConfigured)
        {
            config.setApiKeyMasked(maskApiKey(storedApiKey));
            if (includeSecret)
            {
                config.setApiKey(storedApiKey);
            }
        }
        return config;
    }

    private AiVideoModelConfig validateAndNormalize(AiVideoModelConfig input)
    {
        AiVideoModelConfig config = new AiVideoModelConfig();
        config.setWorkspaceBaseUrl(normalizeWorkspaceBaseUrl(input.getWorkspaceBaseUrl()));
        config.setTextModel(validateModel(input.getTextModel(), "章节分析模型"));
        config.setImageModel(validateModel(input.getImageModel(), "图片生成模型"));
        String videoModel = validateModel(input.getVideoModel(), "视频生成模型");
        String activeProvider = videoProviderProperties.getProvider();
        if (StringUtils.isNotEmpty(input.getVideoProvider())
                && !activeProvider.equalsIgnoreCase(input.getVideoProvider().trim()))
        {
            throw new ServiceException("当前仅支持已安装的视频适配器：" + activeProvider);
        }
        if ("happyhorse".equalsIgnoreCase(activeProvider)
                && !videoModel.toLowerCase(Locale.ROOT).startsWith("happyhorse-"))
        {
            throw new ServiceException("HappyHorse 适配器只能使用 happyhorse-* 视频模型");
        }
        config.setVideoProvider(activeProvider);
        config.setVideoModel(videoModel);
        String resolution = required(input.getVideoResolution(), "视频分辨率").toUpperCase(Locale.ROOT);
        if (!VIDEO_RESOLUTIONS.contains(resolution))
        {
            throw new ServiceException("视频分辨率仅支持 720P 或 1080P");
        }
        config.setVideoResolution(resolution);
        String ratio = required(input.getVideoRatio(), "视频比例");
        if (!VIDEO_RATIOS.contains(ratio))
        {
            throw new ServiceException("视频比例不受 HappyHorse 支持：" + ratio);
        }
        config.setVideoRatio(ratio);
        if (input.getVideoWatermark() == null)
        {
            throw new ServiceException("请选择是否添加视频水印");
        }
        config.setVideoWatermark(input.getVideoWatermark());
        return config;
    }

    private void validateStoredConfig(AiVideoModelConfig config)
    {
        if (!Boolean.TRUE.equals(config.getApiKeyConfigured()) || StringUtils.isEmpty(config.getApiKey()))
        {
            throw new ServiceException("AI 模型配置未完成：请先在模型配置页面保存 API Key");
        }
        validateAndNormalize(config);
    }

    private String normalizeWorkspaceBaseUrl(String value)
    {
        String raw = required(value, "业务空间地址");
        try
        {
            URI uri = new URI(raw);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443))
            {
                throw new ServiceException("业务空间地址必须是标准 HTTPS 地址，且不能包含账号、查询参数或自定义端口");
            }
            host = host.toLowerCase(Locale.ROOT);
            if (!"dashscope.aliyuncs.com".equals(host)
                    && !host.endsWith(".cn-beijing.maas.aliyuncs.com"))
            {
                throw new ServiceException("业务空间地址必须属于阿里云百炼北京地域");
            }
            return "https://" + host + "/compatible-mode/v1";
        }
        catch (ServiceException ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            throw new ServiceException("业务空间地址格式不正确");
        }
    }

    private String validateModel(String value, String label)
    {
        String model = required(value, label);
        if (!MODEL_NAME.matcher(model).matches())
        {
            throw new ServiceException(label + "格式不正确，仅支持字母、数字、点、下划线、冒号和短横线");
        }
        return model;
    }

    private String normalizeOptionalApiKey(String value)
    {
        if (value == null || value.trim().isEmpty())
        {
            return null;
        }
        String apiKey = value.trim();
        if (apiKey.length() < 8 || apiKey.length() > 256)
        {
            throw new ServiceException("API Key 长度必须在 8 到 256 个字符之间");
        }
        for (int i = 0; i < apiKey.length(); i++)
        {
            if (Character.isWhitespace(apiKey.charAt(i)) || Character.isISOControl(apiKey.charAt(i)))
            {
                throw new ServiceException("API Key 不能包含空格或控制字符");
            }
        }
        return apiKey;
    }

    private String maskApiKey(String apiKey)
    {
        if (apiKey == null || apiKey.isEmpty())
        {
            return "";
        }
        if (apiKey.length() <= 10)
        {
            return apiKey.substring(0, 2) + "******" + apiKey.substring(apiKey.length() - 2);
        }
        return apiKey.substring(0, 4) + "********" + apiKey.substring(apiKey.length() - 4);
    }

    private Boolean parseBoolean(String value)
    {
        if ("true".equalsIgnoreCase(value)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(value)) return Boolean.FALSE;
        return null;
    }

    private String required(String value, String label)
    {
        if (value == null || value.trim().isEmpty())
        {
            throw new ServiceException(label + "不能为空");
        }
        return value.trim();
    }

    private String read(String key)
    {
        String value = sysConfigService.selectConfigByKey(key);
        return StringUtils.isEmpty(value) ? null : value.trim();
    }

    private void upsert(String key, String name, String value, String username)
    {
        SysConfig query = new SysConfig();
        query.setConfigKey(key);
        List<SysConfig> matches = sysConfigService.selectConfigList(query);
        SysConfig existing = null;
        for (SysConfig candidate : matches)
        {
            if (key.equals(candidate.getConfigKey()))
            {
                existing = candidate;
                break;
            }
        }
        if (existing == null)
        {
            SysConfig created = new SysConfig();
            created.setConfigName(name);
            created.setConfigKey(key);
            created.setConfigValue(value);
            created.setConfigType(KEY_API_KEY.equals(key) ? "Y" : "N");
            created.setCreateBy(username);
            created.setRemark("AI视频模型配置页面维护");
            if (sysConfigService.insertConfig(created) != 1)
            {
                throw new ServiceException("保存模型配置失败：" + name);
            }
            return;
        }
        existing.setConfigName(name);
        existing.setConfigValue(value);
        existing.setConfigType(KEY_API_KEY.equals(key) ? "Y" : "N");
        existing.setUpdateBy(username);
        if (sysConfigService.updateConfig(existing) != 1)
        {
            throw new ServiceException("保存模型配置失败：" + name);
        }
    }
}
