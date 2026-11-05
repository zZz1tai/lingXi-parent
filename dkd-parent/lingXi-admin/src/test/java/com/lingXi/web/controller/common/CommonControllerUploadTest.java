package com.lingXi.web.controller.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lingXi.common.core.domain.AjaxResult;
import com.lingXi.common.config.RuoYiConfig;
import com.lingXi.common.utils.file.FileUploadUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.upload.UploadPretreatment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

class CommonControllerUploadTest
{
    private CommonController controller;
    private FileStorageService storage;

    @BeforeEach
    void setUp()
    {
        controller = new CommonController();
        storage = mock(FileStorageService.class);
        ReflectionTestUtils.setField(controller, "fileStorageService", storage);
    }

    @Test
    void invalidSingleFilesNeverReachStorage() throws Exception
    {
        MultipartFile tooLarge = mock(MultipartFile.class);
        when(tooLarge.getOriginalFilename()).thenReturn("large.png");
        when(tooLarge.getSize()).thenReturn(FileUploadUtils.DEFAULT_MAX_SIZE + 1);
        for (MultipartFile file : new MultipartFile[] {null,
                new MockMultipartFile("file", "empty.png", "image/png", new byte[0]),
                new MockMultipartFile("file", "run.exe", "application/octet-stream", new byte[] {1}),
                new MockMultipartFile("file", "a".repeat(101) + ".png", "image/png", new byte[] {1}), tooLarge})
        {
            assertEquals(500, controller.uploadFile(file).get(AjaxResult.CODE_TAG));
        }
        verifyNoInteractions(storage);
    }

    @Test
    void validSingleFileUsesStorageAndKeepsResponseContract() throws Exception
    {
        MultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1});
        UploadPretreatment upload = mock(UploadPretreatment.class);
        when(storage.of(file)).thenReturn(upload);
        when(upload.setPath("upload/")).thenReturn(upload);
        when(upload.upload()).thenReturn(new FileInfo().setUrl("https://storage.example.test/photo.png"));
        AjaxResult response = controller.uploadFile(file);
        assertEquals(200, response.get(AjaxResult.CODE_TAG));
        assertEquals("https://storage.example.test/photo.png", response.get("url"));
        assertEquals("photo.png", response.get("originalFilename"));
    }

    @Test
    void storageExceptionDoesNotExposeCredentials() throws Exception
    {
        MultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1});
        when(storage.of(file)).thenThrow(new IllegalStateException("secret-token-in-provider-error"));
        AjaxResult response = controller.uploadFile(file);
        assertEquals(500, response.get(AjaxResult.CODE_TAG));
        assertFalse(response.get(AjaxResult.MSG_TAG).toString().contains("secret-token"));
    }

    @Test
    void batchValidatesAllFilesBeforeWriting(@TempDir Path directory) throws Exception
    {
        String previousProfile = RuoYiConfig.getProfile();
        RuoYiConfig config = new RuoYiConfig();
        config.setProfile(directory.toString());
        try
        {
            assertEquals(500, controller.uploadFiles(List.of(
                    new MockMultipartFile("file", "first.png", "image/png", new byte[] {1}),
                    new MockMultipartFile("file", "second.exe", "application/octet-stream", new byte[] {1})))
                    .get(AjaxResult.CODE_TAG));
            try (var paths = Files.list(directory))
            {
                assertEquals(0L, paths.count(), "非法批次不得提前写入有效文件");
            }
        }
        finally
        {
            config.setProfile(previousProfile);
        }
    }
}
