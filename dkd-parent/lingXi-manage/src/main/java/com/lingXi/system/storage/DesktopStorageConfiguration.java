package com.lingXi.system.storage;

import org.dromara.x.file.storage.core.FileStorageService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
@ConditionalOnProperty(name = "lingxi.desktop.enabled", havingValue = "true")
public class DesktopStorageConfiguration
{
    @Bean
    @Order(-100)
    public ApplicationRunner desktopFilePlatform(FileStorageService files, DesktopStorageService storage)
    {
        return arguments -> {
            files.getFileStorageList().add(new DesktopFileStorage(storage));
            files.getProperties().setDefaultPlatform(DesktopStorageService.PLATFORM);
        };
    }
}
