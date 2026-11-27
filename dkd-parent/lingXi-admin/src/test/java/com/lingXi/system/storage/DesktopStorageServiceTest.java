package com.lingXi.system.storage;

import com.lingXi.common.exception.ServiceException;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DesktopStorageServiceTest
{
    @TempDir Path temporary;

    @Test
    void directoryChangesPreserveOldFilesAndSurviveRestart() throws Exception
    {
        Path data = temporary.resolve("运行数据");
        Path next = temporary.resolve("新的文件目录 with spaces");
        DesktopStorageService service = new DesktopStorageService(data.toString(), "http://127.0.0.1:18080");
        service.write("upload/old.txt", new ByteArrayInputStream("旧文件".getBytes(StandardCharsets.UTF_8)));
        String oldUrl = service.url("upload/old.txt", 0);
        service.changeDirectory(next.toString());
        service.write("upload/new.txt", new ByteArrayInputStream("新文件".getBytes(StandardCharsets.UTF_8)));
        assertTrue(Files.exists(next.resolve("upload/new.txt")));
        assertTrue(Files.exists(data.resolve("uploads/upload/old.txt")));
        DesktopStorageService restarted = new DesktopStorageService(data.toString(), "http://127.0.0.1:18080");
        assertEquals(next.toString(), restarted.settings().get("storagePath"));
        assertEquals("旧文件", Files.readString(restarted.read("upload/old.txt")));
        String encoded = URI.create(oldUrl).getPath().substring("/desktop/files/".length());
        String signature = oldUrl.substring(oldUrl.indexOf("signature=") + 10);
        assertEquals(restarted.read("upload/old.txt"), restarted.authorizedFile(encoded, 0, signature));
    }

    @Test
    void signedFilesRejectTamperingExpiryAndUnsafeDirectories() throws Exception
    {
        DesktopStorageService service = new DesktopStorageService(temporary.resolve("server").toString(), "http://127.0.0.1:18080");
        service.write("upload/image.png", new ByteArrayInputStream(new byte[] {1, 2, 3}));
        String expired = service.url("upload/image.png", 1);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString("upload/image.png".getBytes());
        assertThrows(ServiceException.class, () -> service.authorizedFile(encoded, 1, expired.substring(expired.indexOf("signature=") + 10)));
        assertThrows(ServiceException.class, () -> service.authorizedFile(encoded, 0, "invalid"));
        assertThrows(ServiceException.class, () -> service.read("../../file-signing.key"));
        assertThrows(ServiceException.class, () -> service.changeDirectory("relative-path"));
        assertThrows(ServiceException.class, () -> service.changeDirectory(temporary.toString()));
        assertThrows(ServiceException.class, () -> service.changeDirectory(temporary.resolve("server/mysql").toString()));
        assertTrue(service.modelImage(service.url("upload/image.png", 0)).startsWith("data:image/png;base64,"));
        assertEquals("https://example.test/reference.png", service.modelImage("https://example.test/reference.png"));
    }
}
