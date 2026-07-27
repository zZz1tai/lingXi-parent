package com.lingXi.system.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 系统安全配置数据传输对象。
 * <p>管理阿里云OSS、Redis密码、Token密钥、Agent API Key等敏感配置。</p>
 * <p>敏感字段仅允许写入，读取时返回掩码。</p>
 */
@Data
public class SystemSecurityConfig
{
    // ===== 阿里云 OSS =====

    /** OSS AccessKey */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String ossAccessKey;

    /** OSS AccessKey 掩码 */
    private String ossAccessKeyMasked;

    /** 是否已配置 OSS AccessKey */
    private Boolean ossAccessKeyConfigured;

    /** OSS SecretKey */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String ossSecretKey;

    /** OSS SecretKey 掩码 */
    private String ossSecretKeyMasked;

    /** 是否已配置 OSS SecretKey */
    private Boolean ossSecretKeyConfigured;

    /** OSS Endpoint */
    private String ossEndpoint;

    /** OSS BucketName */
    private String ossBucketName;

    /** OSS 访问域名 */
    private String ossDomain;

    /** OSS 基础路径 */
    private String ossBasePath;

    // ===== Agent =====

    /** Agent 服务 API Key */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String agentServiceApiKey;

    /** Agent 服务 API Key 掩码 */
    private String agentServiceApiKeyMasked;

    /** 是否已配置 Agent 服务 API Key */
    private Boolean agentServiceApiKeyConfigured;
}
