package com.lingXi.common.config;

import org.dromara.x.file.storage.core.recorder.FileRecorder;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.upload.FilePartInfo;
import org.springframework.stereotype.Component;

/**
 * 文件记录器空实现，消除 x-file-storage 启动警告。
 * 如需记录上传元数据到数据库，可替换为基于 MyBatis 的实现。
 */
@Component
public class NoOpFileRecorder implements FileRecorder
{
    @Override
    public boolean save(FileInfo fileInfo)
    {
        return true;
    }

    @Override
    public void update(FileInfo fileInfo)
    {
        // 不更新
    }

    @Override
    public void saveFilePart(FilePartInfo filePartInfo)
    {
        // 不记录分片信息
    }

    @Override
    public boolean delete(String url)
    {
        return true;
    }

    @Override
    public void deleteFilePartByUploadId(String uploadId)
    {
        // 不处理分片上传记录
    }

    @Override
    public FileInfo getByUrl(String url)
    {
        return null;
    }
}
