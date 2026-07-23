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

    /**
     * 将远程图片下载并转存到文件存储平台。
     *
     * @param projectId  项目ID
     * @param assetId    资产ID
     * @param versionNo  版本号
     * @param assetCode  资产编码
     * @param remoteUrl  远程图片地址
     * @return 存储结果信息（含URL、大小、哈希、尺寸等）
     * @throws Exception 下载或上传失败时抛出异常
     */
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

    /**
     * 将远程视频下载并转存到文件存储平台。
     *
     * @param projectId  项目ID
     * @param assetId    资产ID
     * @param versionNo  版本号
     * @param assetCode  资产编码
     * @param remoteUrl  远程视频地址
     * @return 存储结果信息（含URL、大小、哈希、平台）
     * @throws Exception 下载或上传失败时抛出异常
     */
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

    /**
     * 生成带版本号的文件名，确保同一资产的不同版本不会覆盖。
     *
     * @param assetCode  资产编码
     * @param assetId    资产ID
     * @param versionNo  版本号
     * @param extension  文件扩展名
     * @return 带版本号的文件名
     */
    private String versionedFilename(String assetCode, Long assetId, Integer versionNo, String extension)
    {
        String normalizedCode = assetCode == null || assetCode.trim().isEmpty()
                ? "asset" : assetCode.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        long normalizedAssetId = assetId == null ? 0L : assetId.longValue();
        int normalizedVersion = versionNo == null || versionNo.intValue() < 1 ? 1 : versionNo.intValue();
        return normalizedCode + "-v" + normalizedVersion + "-a" + normalizedAssetId + extension;
    }

    /**
     * 根据资源路径删除文件存储平台上的对象。
     *
     * @param resourcePath 资源路径
     * @return 是否删除成功
     */
    public boolean delete(String resourcePath)
    {
        return resourcePath == null || resourcePath.trim().isEmpty()
                || fileStorageService.delete(resourcePath.trim());
    }

    /**
     * 从远程URL下载文件内容到字节数组。
     *
     * @param remoteUrl    远程地址
     * @param readTimeout  读取超时时间（毫秒）
     * @return 文件字节数组
     * @throws Exception 下载失败时抛出异常
     */
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

    /**
     * 计算字节数组的SHA-256哈希值。
     *
     * @param bytes 字节数组
     * @return SHA-256哈希字符串
     * @throws Exception 哈希计算失败时抛出异常
     */
    private String sha256(byte[] bytes) throws Exception
    {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        StringBuilder hash = new StringBuilder();
        for (byte value : digest.digest(bytes)) hash.append(String.format("%02x", value));
        return hash.toString();
    }

    /**
     * 图片存储结果，包含资源路径、大小、SHA-256哈希、尺寸和平台信息。
     */
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

    /**
     * 视频存储结果，包含资源路径、大小、SHA-256哈希和平台信息。
     */
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
