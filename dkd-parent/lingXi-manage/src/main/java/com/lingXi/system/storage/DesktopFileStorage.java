package com.lingXi.system.storage;

import java.io.InputStream;
import java.nio.file.Files;
import java.util.function.Consumer;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.UploadPretreatment;
import org.dromara.x.file.storage.core.platform.FileStorage;
import org.dromara.x.file.storage.core.presigned.GeneratePresignedUrlPretreatment;
import org.dromara.x.file.storage.core.presigned.GeneratePresignedUrlResult;

/** 通用上传、会话附件和视频资产共用的桌面存储平台。 */
public class DesktopFileStorage implements FileStorage
{
    private final DesktopStorageService storage;
    public DesktopFileStorage(DesktopStorageService storage) { this.storage = storage; }
    @Override public String getPlatform() { return DesktopStorageService.PLATFORM; }
    @Override public void setPlatform(String ignored) { }
    @Override public boolean isSupportAcl() { return true; }
    @Override public boolean isSupportPresignedUrl() { return true; }

    @Override
    public boolean save(FileInfo info, UploadPretreatment pre)
    {
        info.setBasePath("");
        try (InputStream input = pre.getInputStreamPlus())
        {
            info.setSize(storage.write(getFileKey(info), input));
            // 数据库只保存稳定的文件地址；私有附件展示时另发短期签名。
            boolean privateFile = org.dromara.x.file.storage.core.constant.Constant.ACL.PRIVATE.equals(info.getFileAcl());
            info.setUrl(storage.url(getFileKey(info), privateFile ? 1 : 0));
            return true;
        }
        catch (Exception exception) { throw new IllegalStateException("本地文件保存失败，请检查保存目录和磁盘空间", exception); }
    }

    @Override
    public GeneratePresignedUrlResult generatePresignedUrl(GeneratePresignedUrlPretreatment pre)
    {
        long expires = pre.getExpiration() == null ? System.currentTimeMillis() / 1000 + 600
            : pre.getExpiration().getTime() / 1000;
        return new GeneratePresignedUrlResult(getPlatform(), "", pre)
            .setUrl(storage.url(pre.getPath() + pre.getFilename(), expires));
    }

    @Override public boolean delete(FileInfo info) { return storage.delete(getFileKey(info)); }
    @Override public boolean exists(FileInfo info)
    {
        try { storage.read(getFileKey(info)); return true; }
        catch (Exception exception) { return false; }
    }
    @Override public void download(FileInfo info, Consumer<InputStream> consumer)
    {
        try (InputStream input = Files.newInputStream(storage.read(getFileKey(info)))) { consumer.accept(input); }
        catch (Exception exception) { throw new IllegalStateException("本地文件读取失败", exception); }
    }
    @Override public void downloadTh(FileInfo info, Consumer<InputStream> consumer) { download(info, consumer); }
}
