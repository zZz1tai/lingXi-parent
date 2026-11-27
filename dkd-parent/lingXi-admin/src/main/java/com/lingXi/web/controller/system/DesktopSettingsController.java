package com.lingXi.web.controller.system;

import com.lingXi.common.annotation.Anonymous;
import com.lingXi.common.core.domain.AjaxResult;
import com.lingXi.system.storage.DesktopStorageService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 桌面设置与带签名的本机文件读取。 */
@RestController
@ConditionalOnProperty(name = "lingxi.desktop.enabled", havingValue = "true")
public class DesktopSettingsController
{
    private final DesktopStorageService storage;
    public DesktopSettingsController(DesktopStorageService storage) { this.storage = storage; }

    @GetMapping("/desktop/settings")
    @PreAuthorize("@ss.hasPermi('system:config:edit')")
    public AjaxResult get() { return AjaxResult.success(storage.settings()); }

    @PutMapping("/desktop/settings")
    @PreAuthorize("@ss.hasPermi('system:config:edit')")
    public AjaxResult update(@RequestBody Map<String, String> input)
    {
        return AjaxResult.success(storage.changeDirectory(input.get("storagePath")));
    }

    @Anonymous
    @GetMapping("/desktop/files/{key}")
    public ResponseEntity<FileSystemResource> file(@PathVariable String key, @RequestParam long expires,
            @RequestParam String signature)
    {
        try
        {
            Path file = storage.authorizedFile(key, expires, signature);
            String mime = Files.probeContentType(file);
            if (mime == null || !(mime.startsWith("image/") || mime.startsWith("video/") || mime.equals("application/pdf")))
                mime = "application/octet-stream";
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(mime))
                .header("X-Content-Type-Options", "nosniff").header("Content-Security-Policy", "sandbox")
                .header("Cache-Control", "private, no-store")
                .body(new FileSystemResource(file));
        }
        catch (Exception exception) { return ResponseEntity.status(HttpStatus.NOT_FOUND).build(); }
    }
}
