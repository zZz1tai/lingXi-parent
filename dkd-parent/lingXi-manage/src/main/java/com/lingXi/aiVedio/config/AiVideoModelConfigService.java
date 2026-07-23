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

    /**
     * 获取页面展示用的模型配置，API Key以掩码形式返回。
     *
     * @return 模型配置对象
     */
    public AiVideoModelConfig getConfig()
    {
        return readStoredConfig(false);
    }

    /**
     * 获取完整的模型配置用于任务调用，缺少必要配置时拒绝启动新任务。
     *
     * @return 完整模型配置对象
     * @throws ServiceException 配置不完整时抛出异常
     */
    public AiVideoModelConfig getRequiredConfig()
    {
        AiVideoModelConfig config = readStoredConfig(true);
        validateStoredConfig(config);
        return config;
    }

    /**
     * 更新模型配置信息并持久化到数据库。
     *
     * @param input    待更新的配置输入
     * @param username 操作用户名
     * @return 更新后的配置对象
     * @throws ServiceException 配置校验失败时抛出异常
     */
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

    /**
     * 从数据库读取已存储的配置。
     *
     * @param includeSecret 是否包含API Key明文
     * @return 模型配置对象
     */
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

    /**
     * 校验并标准化输入的配置参数。
     *
     * @param input 原始配置输入
     * @return 标准化后的配置对象
     * @throws ServiceException 参数校验失败时抛出异常
     */
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

    /**
     * 校验已存储的配置是否完整有效。
     *
     * @param config 模型配置对象
     * @throws ServiceException 配置不完整时抛出异常
     */
    private void validateStoredConfig(AiVideoModelConfig config)
    {
        if (!Boolean.TRUE.equals(config.getApiKeyConfigured()) || StringUtils.isEmpty(config.getApiKey()))
        {
            throw new ServiceException("AI 模型配置未完成：请先在模型配置页面保存 API Key");
        }
        validateAndNormalize(config);
    }

    /**
     * 标准化业务空间地址，强制使用HTTPS并归属阿里云百炼北京地域。
     *
     * @param value 原始地址
     * @return 标准化后的地址
     * @throws ServiceException 地址格式或归属不合法时抛出异常
     */
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

    /**
     * 校验模型名称格式是否合法。
     *
     * @param value 模型名称
     * @param label 字段标签（用于错误提示）
     * @return 校验后的模型名称
     * @throws ServiceException 格式不合法时抛出异常
     */
    private String validateModel(String value, String label)
    {
        String model = required(value, label);
        if (!MODEL_NAME.matcher(model).matches())
        {
            throw new ServiceException(label + "格式不正确，仅支持字母、数字、点、下划线、冒号和短横线");
        }
        return model;
    }

    /**
     * 标准化可选的API Key，校验长度和字符合法性。
     *
     * @param value 原始API Key
     * @return 标准化后的API Key，若为空则返回null
     * @throws ServiceException 格式不合法时抛出异常
     */
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

    /**
     * 对API Key进行脱敏处理，仅保留首尾部分字符。
     *
     * @param apiKey 原始API Key
     * @return 脱敏后的字符串
     */
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

    /**
     * 解析布尔值字符串。
     *
     * @param value 字符串值
     * @return Boolean对象，无法解析时返回null
     */
    private Boolean parseBoolean(String value)
    {
        if ("true".equalsIgnoreCase(value)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(value)) return Boolean.FALSE;
        return null;
    }

    /**
     * 校验必填字段非空。
     *
     * @param value 字段值
     * @param label 字段标签
     * @return 非空的字段值
     * @throws ServiceException 字段为空时抛出异常
     */
    private String required(String value, String label)
    {
        if (value == null || value.trim().isEmpty())
        {
            throw new ServiceException(label + "不能为空");
        }
        return value.trim();
    }

    /**
     * 从系统配置表读取指定键的配置值。
     *
     * @param key 配置键
     * @return 配置值，不存在则返回null
     */
    private String read(String key)
    {
        String value = sysConfigService.selectConfigByKey(key);
        return StringUtils.isEmpty(value) ? null : value.trim();
    }

    /**
     * 新增或更新系统配置记录。
     *
     * @param key      配置键
     * @param name     配置名称
     * @param value    配置值
     * @param username 操作用户名
     * @throws ServiceException 保存失败时抛出异常
     */
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
