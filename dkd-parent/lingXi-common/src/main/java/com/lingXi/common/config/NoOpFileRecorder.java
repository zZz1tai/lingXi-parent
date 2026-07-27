package com.lingXi.common.config;

import org.dromara.x.file.storage.core.recorder.FileRecorder;
import org.dromara.x.file.storage.core.FileInfo;
import org.springframework.stereotype.Component;

/**
 * 文件记录器空实现，消除 x-file-storage 启动警告。
 * 如需记录上传元数据到数据库，可替换为基于 MyBatis 的实现。
 */
@Component
public class NoOpFileRecorder implements FileRecorder
{
    @Override
    public void record(FileInfo fileInfo)
    {
        // 不记录
    }

    @Override
    public void delete(String url)
    {
        // 不处理
    }

    @Override
    public FileInfo getByUrl(String url)
    {
        return null;
    }
}
