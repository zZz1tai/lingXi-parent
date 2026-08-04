package com.lingXi.system.config;

import org.dromara.x.file.storage.core.FileStorageProperties.AliyunOssConfig;
import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.platform.AliyunOssFileStorage;
import org.dromara.x.file.storage.core.platform.AliyunOssFileStorageClientFactory;
import org.dromara.x.file.storage.core.platform.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.lingXi.common.utils.StringUtils;
import com.lingXi.system.service.ISysConfigService;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 将 sys_config 表中的 OSS 配置（security.oss.*）动态绑定到 x-file-storage 的 aliyun-oss-1 存储平台。
 * <p>启动完成后执行一次，替换掉以空凭据构建的默认平台；配置更新后由
 * {@link SystemSecurityConfigService} 再次调用以热刷新。</p>
 */
@Component
public class OssStoragePlatformBinder implements ApplicationRunner
{
    private static final Logger log = LoggerFactory.getLogger(OssStoragePlatformBinder.class);

    /** 与 application.yml 中 aliyun-oss-1 平台标识保持一致 */
    private static final String PLATFORM = "aliyun-oss-1";

    private final ISysConfigService sysConfigService;
    private final FileStorageService fileStorageService;

    public OssStoragePlatformBinder(ISysConfigService sysConfigService, FileStorageService fileStorageService)
    {
        this.sysConfigService = sysConfigService;
        this.fileStorageService = fileStorageService;
    }

    @Override
    public void run(ApplicationArguments args)
    {
        bind();
    }

    /**
     * 从 sys_config 读取 OSS 配置并刷新存储平台。
     * <p>当关键配置缺失时保持现状（回退到 application.yml / 环境变量），不中断启动。</p>
     */
    public synchronized void bind()
    {
        try
        {
            String accessKey = SysConfigValue.readValue(sysConfigService, "security.oss.accessKey");
            String secretKey = SysConfigValue.readValue(sysConfigService, "security.oss.secretKey");
            String endPoint = SysConfigValue.readValue(sysConfigService, "security.oss.endpoint");
            String bucketName = SysConfigValue.readValue(sysConfigService, "security.oss.bucketName");
            String domain = SysConfigValue.readValue(sysConfigService, "security.oss.domain");
            String basePath = SysConfigValue.readValue(sysConfigService, "security.oss.basePath");

            if (StringUtils.isEmpty(accessKey) || StringUtils.isEmpty(secretKey)
                    || StringUtils.isEmpty(endPoint) || StringUtils.isEmpty(bucketName))
            {
                log.warn("【OSS】数据库配置不完整（accessKey/secretKey/endpoint/bucketName 缺失），跳过平台刷新，回退 application.yml 配置");
                return;
            }

            AliyunOssConfig config = new AliyunOssConfig();
            config.setPlatform(PLATFORM);
            config.setAccessKey(accessKey);
            config.setSecretKey(secretKey);
            config.setEndPoint(endPoint);
            config.setBucketName(bucketName);
            config.setDomain(domain);
            config.setBasePath(basePath);

            AliyunOssFileStorage storage = new AliyunOssFileStorage(config, new AliyunOssFileStorageClientFactory(config));
            replacePlatform(storage);
            log.info("【OSS】已从 sys_config 刷新存储平台 {}（endpoint={}, bucket={}）", PLATFORM, endPoint, bucketName);
        }
        catch (Exception e)
        {
            log.error("【OSS】刷新存储平台失败，保持原有配置：{}", e.getMessage(), e);
        }
    }

    /**
     * 用新存储平台替换同平台标识的旧实例；不存在则追加。
     */
    private void replacePlatform(AliyunOssFileStorage newStorage)
    {
        CopyOnWriteArrayList<FileStorage> storages = fileStorageService.getFileStorageList();
        for (int i = 0; i < storages.size(); i++)
        {
            FileStorage existing = storages.get(i);
            if (PLATFORM.equals(existing.getPlatform()))
            {
                storages.set(i, newStorage);
                closeQuietly(existing);
                return;
            }
        }
        storages.add(newStorage);
    }

    private void closeQuietly(FileStorage storage)
    {
        try
        {
            storage.close();
        }
        catch (Exception ignored)
        {
        }
    }

    /** 私有静态工具：读取配置项，null 归 ""。 */
    private static final class SysConfigValue
    {
        private static String readValue(ISysConfigService service, String key)
        {
            String value = service.selectConfigByKey(key);
            return value == null ? "" : value.trim();
        }
    }
}