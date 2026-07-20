package com.lingXi.aiVedio.storage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.lingXi.aiVedio.config.AiVideoStorageProperties;
import com.lingXi.common.exception.ServiceException;

/** 将本地资源路径转换为视频生成供应商可访问的公网 URL。 */
@Component
public class AiVideoPublicAssetUrlResolver
{
    @Autowired
    private AiVideoStorageProperties properties;

    public String resolve(String assetPath)
    {
        if (assetPath == null || assetPath.trim().isEmpty())
        {
            throw new ServiceException("关键帧图片尚未转存完成");
        }
        if (assetPath.startsWith("http://") || assetPath.startsWith("https://"))
        {
            return assetPath;
        }
        String baseUrl = properties.getPublicAssetBaseUrl();
        if (baseUrl == null || baseUrl.trim().isEmpty())
        {
            throw new ServiceException("请配置 aivideo.public-asset-base-url，使视频供应商能访问参考图片");
        }
        return baseUrl.replaceAll("/$", "") + (assetPath.startsWith("/") ? assetPath : "/" + assetPath);
    }
}
