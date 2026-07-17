package com.lingXi.aiVedio.storage;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import javax.imageio.ImageIO;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 将模型临时下载地址转存到当前默认文件存储平台（生产环境为阿里云 OSS）。 */
@Component
public class AiVideoLocalAssetStorage
{
    @Autowired
    private FileStorageService fileStorageService;

    public StoredImage store(Long projectId, Long assetId, Integer versionNo,
            String assetCode, String remoteUrl) throws Exception
    {
        byte[] bytes = download(remoteUrl, 60000);
        String filename = versionedFilename(assetCode, assetId, versionNo, ".png");
        FileInfo fileInfo = fileStorageService.of(bytes, filename, "image/png")
                .setPath("aivideo/" + projectId + "/images/")
                .setSaveFilename(filename)
                .upload();
        if (fileInfo == null || fileInfo.getUrl() == null) throw new IllegalStateException("OSS 图片上传失败");
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        return new StoredImage(fileInfo.getUrl(), fileInfo.getSize(), sha256(bytes), image == null ? null : image.getWidth(),
                image == null ? null : image.getHeight(), fileInfo.getPlatform());
    }

    public StoredFile storeVideo(Long projectId, Long assetId, Integer versionNo,
            String assetCode, String remoteUrl) throws Exception
    {
        byte[] bytes = download(remoteUrl, 120000);
        String filename = versionedFilename(assetCode, assetId, versionNo, ".mp4");
        FileInfo fileInfo = fileStorageService.of(bytes, filename, "video/mp4")
                .setPath("aivideo/" + projectId + "/videos/")
                .setSaveFilename(filename)
                .upload();
        if (fileInfo == null || fileInfo.getUrl() == null) throw new IllegalStateException("OSS 视频上传失败");
        return new StoredFile(fileInfo.getUrl(), fileInfo.getSize(), sha256(bytes), fileInfo.getPlatform());
    }

    /** 同一 assetCode 的不同版本必须落到不同 OSS 对象，旧版本才能真正保留。 */
    private String versionedFilename(String assetCode, Long assetId, Integer versionNo, String extension)
    {
        String normalizedCode = assetCode == null || assetCode.trim().isEmpty()
                ? "asset" : assetCode.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        long normalizedAssetId = assetId == null ? 0L : assetId.longValue();
        int normalizedVersion = versionNo == null || versionNo.intValue() < 1 ? 1 : versionNo.intValue();
        return normalizedCode + "-v" + normalizedVersion + "-a" + normalizedAssetId + extension;
    }

    /** 用户明确删除资产后，按上传时保存的完整资源 URL 回收 OSS 对象。 */
    public boolean delete(String resourcePath)
    {
        return resourcePath == null || resourcePath.trim().isEmpty()
                || fileStorageService.delete(resourcePath.trim());
    }

    private byte[] download(String remoteUrl, int readTimeout) throws Exception
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(remoteUrl).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(readTimeout);
        try (InputStream input = connection.getInputStream(); java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream())
        {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1)
            {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private String sha256(byte[] bytes) throws Exception
    {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        StringBuilder hash = new StringBuilder();
        for (byte value : digest.digest(bytes)) hash.append(String.format("%02x", value));
        return hash.toString();
    }

    public static class StoredImage
    {
        private final String resourcePath;
        private final long size;
        private final String sha256;
        private final Integer width;
        private final Integer height;
        private final String platform;
        public StoredImage(String resourcePath, Long size, String sha256, Integer width, Integer height, String platform)
        { this.resourcePath = resourcePath; this.size = size == null ? 0 : size; this.sha256 = sha256; this.width = width; this.height = height; this.platform = platform; }
        public String getResourcePath() { return resourcePath; }
        public long getSize() { return size; }
        public String getSha256() { return sha256; }
        public Integer getWidth() { return width; }
        public Integer getHeight() { return height; }
        public String getPlatform() { return platform; }
    }

    public static class StoredFile
    {
        private final String resourcePath;
        private final long size;
        private final String sha256;
        private final String platform;
        public StoredFile(String resourcePath, Long size, String sha256, String platform)
        { this.resourcePath = resourcePath; this.size = size == null ? 0 : size; this.sha256 = sha256; this.platform = platform; }
        public String getResourcePath() { return resourcePath; }
        public long getSize() { return size; }
        public String getSha256() { return sha256; }
        public String getPlatform() { return platform; }
    }
}
