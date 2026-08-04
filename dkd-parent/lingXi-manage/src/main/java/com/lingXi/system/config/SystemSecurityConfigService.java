package com.lingXi.system.config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.lingXi.common.exception.ServiceException;
import com.lingXi.common.utils.StringUtils;
import com.lingXi.system.domain.SysConfig;
import com.lingXi.system.service.ISysConfigService;

/**
 * 系统安全配置服务。
 * <p>从 sys_config 表读写敏感配置项，包括阿里云OSS、Redis密码、Token密钥、Agent API Key。</p>
 * <p>所有密钥仅在写入时接受明文，读取时以掩码返回，绝不通过 JSON 回传完整内容。</p>
 */
@Service
public class SystemSecurityConfigService
{
    // ===== 配置键常量 =====
    private static final String KEY_OSS_ACCESS_KEY = "security.oss.accessKey";
    private static final String KEY_OSS_SECRET_KEY = "security.oss.secretKey";
    private static final String KEY_OSS_ENDPOINT = "security.oss.endpoint";
    private static final String KEY_OSS_BUCKET_NAME = "security.oss.bucketName";
    private static final String KEY_OSS_DOMAIN = "security.oss.domain";
    private static final String KEY_OSS_BASE_PATH = "security.oss.basePath";
    private static final String KEY_AGENT_SERVICE_API_KEY = "security.agent.serviceApiKey";

    /** 所有敏感键集合，用于在 SysConfigController 中保护这些配置不被通用接口修改 */
    public static final Set<String> SENSITIVE_KEYS = new HashSet<>(Arrays.asList(
            KEY_OSS_ACCESS_KEY, KEY_OSS_SECRET_KEY, KEY_AGENT_SERVICE_API_KEY));

    private static final Set<String> VALID_OSS_ENDPOINTS = new HashSet<>(Arrays.asList(
            "oss-cn-hangzhou.aliyuncs.com",
            "oss-cn-shanghai.aliyuncs.com",
            "oss-cn-beijing.aliyuncs.com",
            "oss-cn-shenzhen.aliyuncs.com",
            "oss-cn-guangzhou.aliyuncs.com",
            "oss-cn-chengdu.aliyuncs.com",
            "oss-cn-hongkong.aliyuncs.com",
            "oss-ap-southeast-1.aliyuncs.com",
            "oss-ap-northeast-1.aliyuncs.com",
            "oss-us-west-1.aliyuncs.com",
            "oss-eu-central-1.aliyuncs.com"));

    @Autowired
    private ISysConfigService sysConfigService;

    @Autowired
    private OssStoragePlatformBinder ossStoragePlatformBinder;

    /**
     * 获取页面展示用的安全配置，敏感字段以掩码返回。
     */
    public SystemSecurityConfig getConfig()
    {
        return readStoredConfig(false);
    }

    /**
     * 获取完整配置供内部服务调用（含明文密钥）。
     */
    public SystemSecurityConfig getRequiredConfig()
    {
        return readStoredConfig(true);
    }

    /**
     * 更新安全配置并持久化到数据库。
     */
    @Transactional
    public SystemSecurityConfig updateConfig(SystemSecurityConfig input, String username)
    {
        if (input == null)
        {
            throw new ServiceException("配置不能为空");
        }

        // OSS AccessKey
        String ossAccessKey = normalizeSecretKey(input.getOssAccessKey(), "OSS AccessKey", 8, 128);
        String storedOssAccessKey = read(KEY_OSS_ACCESS_KEY);
        if (ossAccessKey == null && StringUtils.isEmpty(storedOssAccessKey))
        {
            throw new ServiceException("请填写 OSS AccessKey");
        }

        // OSS SecretKey
        String ossSecretKey = normalizeSecretKey(input.getOssSecretKey(), "OSS SecretKey", 8, 128);
        String storedOssSecretKey = read(KEY_OSS_SECRET_KEY);
        if (ossSecretKey == null && StringUtils.isEmpty(storedOssSecretKey))
        {
            throw new ServiceException("请填写 OSS SecretKey");
        }

        // OSS Endpoint
        String ossEndpoint = required(input.getOssEndpoint(), "OSS Endpoint").toLowerCase();
        if (!VALID_OSS_ENDPOINTS.contains(ossEndpoint) && !ossEndpoint.endsWith(".aliyuncs.com"))
        {
            throw new ServiceException("OSS Endpoint 格式不正确，应为阿里云 OSS 标准 Endpoint");
        }

        // OSS BucketName
        String ossBucketName = required(input.getOssBucketName(), "OSS BucketName");
        if (ossBucketName.length() < 3 || ossBucketName.length() > 63)
        {
            throw new ServiceException("OSS BucketName 长度应在 3 到 63 个字符之间");
        }
        if (!ossBucketName.matches("^[a-z0-9][a-z0-9\\-]*[a-z0-9]$"))
        {
            throw new ServiceException("OSS BucketName 格式不正确，仅支持小写字母、数字和短横线");
        }

        // OSS Domain
        String ossDomain = normalizeDomain(input.getOssDomain());

        // OSS BasePath
        String ossBasePath = input.getOssBasePath() != null ? input.getOssBasePath().trim() : "";

        // Agent Service API Key
        String agentApiKey = normalizeSecretKey(input.getAgentServiceApiKey(), "Agent API Key", 0, 256);
        // Agent API Key 允许为空（通过环境变量提供）

        // 持久化
        upsert(KEY_OSS_ACCESS_KEY, "阿里云OSS-AccessKey", ossAccessKey != null ? ossAccessKey : storedOssAccessKey, username);
        upsert(KEY_OSS_SECRET_KEY, "阿里云OSS-SecretKey", ossSecretKey != null ? ossSecretKey : storedOssSecretKey, username);
        upsert(KEY_OSS_ENDPOINT, "阿里云OSS-Endpoint", ossEndpoint, username);
        upsert(KEY_OSS_BUCKET_NAME, "阿里云OSS-BucketName", ossBucketName, username);
        upsert(KEY_OSS_DOMAIN, "阿里云OSS-访问域名", ossDomain, username);
        upsert(KEY_OSS_BASE_PATH, "阿里云OSS-基础路径", ossBasePath, username);
        if (agentApiKey != null)
        {
            upsert(KEY_AGENT_SERVICE_API_KEY, "Agent服务-API Key", agentApiKey, username);
        }

        // 配置持久化后热刷新存储平台（若 OSS 已配置完整则立即生效）
        ossStoragePlatformBinder.bind();

        return getConfig();
    }

    private SystemSecurityConfig readStoredConfig(boolean includeSecret)
    {
        SystemSecurityConfig config = new SystemSecurityConfig();

        // OSS AccessKey
        String ossAccessKey = read(KEY_OSS_ACCESS_KEY);
        config.setOssAccessKeyConfigured(StringUtils.isNotEmpty(ossAccessKey));
        if (includeSecret) config.setOssAccessKey(ossAccessKey);
        config.setOssAccessKeyMasked(maskSecret(ossAccessKey));

        // OSS SecretKey
        String ossSecretKey = read(KEY_OSS_SECRET_KEY);
        config.setOssSecretKeyConfigured(StringUtils.isNotEmpty(ossSecretKey));
        if (includeSecret) config.setOssSecretKey(ossSecretKey);
        config.setOssSecretKeyMasked(maskSecret(ossSecretKey));

        // OSS 其他
        config.setOssEndpoint(read(KEY_OSS_ENDPOINT));
        config.setOssBucketName(read(KEY_OSS_BUCKET_NAME));
        config.setOssDomain(read(KEY_OSS_DOMAIN));
        config.setOssBasePath(read(KEY_OSS_BASE_PATH));

        // Agent
        String agentApiKey = read(KEY_AGENT_SERVICE_API_KEY);
        config.setAgentServiceApiKeyConfigured(StringUtils.isNotEmpty(agentApiKey));
        if (includeSecret) config.setAgentServiceApiKey(agentApiKey);
        config.setAgentServiceApiKeyMasked(maskSecret(agentApiKey));

        return config;
    }

    private String normalizeSecretKey(String value, String label, int minLength, int maxLength)
    {
        if (value == null || value.trim().isEmpty())
        {
            return null;
        }
        String key = value.trim();
        if (key.length() > maxLength)
        {
            throw new ServiceException(label + "长度不能超过 " + maxLength + " 个字符");
        }
        if (minLength > 0 && key.length() < minLength)
        {
            throw new ServiceException(label + "长度不能少于 " + minLength + " 个字符");
        }
        for (int i = 0; i < key.length(); i++)
        {
            if (Character.isISOControl(key.charAt(i)))
            {
                throw new ServiceException(label + "不能包含控制字符");
            }
        }
        return key;
    }

    private String normalizeDomain(String value)
    {
        if (value == null || value.trim().isEmpty())
        {
            return "";
        }
        String domain = value.trim();
        if (!domain.startsWith("http://") && !domain.startsWith("https://"))
        {
            domain = "https://" + domain;
        }
        if (!domain.endsWith("/"))
        {
            domain = domain + "/";
        }
        return domain;
    }

    private String maskSecret(String value)
    {
        if (value == null || value.isEmpty())
        {
            return "";
        }
        if (value.length() <= 8)
        {
            return value.substring(0, 2) + "******";
        }
        return value.substring(0, 4) + "********" + value.substring(value.length() - 4);
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
        boolean isSensitive = SENSITIVE_KEYS.contains(key);
        if (existing == null)
        {
            SysConfig created = new SysConfig();
            created.setConfigName(name);
            created.setConfigKey(key);
            created.setConfigValue(value);
            created.setConfigType(isSensitive ? "Y" : "N");
            created.setCreateBy(username);
            created.setRemark("系统安全配置页面维护");
            if (sysConfigService.insertConfig(created) != 1)
            {
                throw new ServiceException("保存配置失败：" + name);
            }
            return;
        }
        existing.setConfigName(name);
        existing.setConfigValue(value);
        existing.setConfigType(isSensitive ? "Y" : "N");
        existing.setUpdateBy(username);
        if (sysConfigService.updateConfig(existing) != 1)
        {
            throw new ServiceException("保存配置失败：" + name);
        }
    }

    private String required(String value, String label)
    {
        if (value == null || value.trim().isEmpty())
        {
            throw new ServiceException(label + "不能为空");
        }
        return value.trim();
    }
}
