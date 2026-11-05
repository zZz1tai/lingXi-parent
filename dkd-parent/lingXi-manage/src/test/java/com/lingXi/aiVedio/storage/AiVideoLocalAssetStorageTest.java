package com.lingXi.aiVedio.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.upload.UploadPretreatment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** 供应商临时产物转存校验：垃圾内容（错误页/空文件/未知容器）不得晋升为正式资产。 */
class AiVideoLocalAssetStorageTest
{
    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private UploadPretreatment pretreatment;

    @Mock
    private AiVideoRemoteAssetDownloader remoteAssetDownloader;

    @InjectMocks
    private AiVideoLocalAssetStorage storage;

    private AutoCloseable mocks;
    private final AtomicReference<byte[]> responseBody = new AtomicReference<>(new byte[0]);
    private final AtomicReference<Path> videoDownloadPath = new AtomicReference<>();

    private static byte[] onePixelPng()
    {
        try
        {
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(1, 1,
                    java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
        catch (java.io.IOException ex)
        {
            throw new IllegalStateException(ex);
        }
    }

    private static byte[] mp4ContainerBytes(int length)
    {
        byte[] bytes = new byte[length];
        bytes[4] = (byte) 'f';
        bytes[5] = (byte) 't';
        bytes[6] = (byte) 'y';
        bytes[7] = (byte) 'p';
        return bytes;
    }

    @BeforeEach
    void setUp() throws Exception
    {
        mocks = MockitoAnnotations.openMocks(this);
        when(fileStorageService.of(any(), anyString(), anyString())).thenReturn(pretreatment);
        when(pretreatment.setPath(anyString())).thenReturn(pretreatment);
        when(pretreatment.setSaveFilename(anyString())).thenReturn(pretreatment);
        FileInfo fileInfo = mock(FileInfo.class);
        when(fileInfo.getUrl()).thenReturn("http://storage.test/out");
        when(fileInfo.getSize()).thenReturn(Long.valueOf(42));
        when(fileInfo.getPlatform()).thenReturn("aliyun-oss-1");
        when(pretreatment.upload()).thenReturn(fileInfo);
        when(remoteAssetDownloader.download(anyString(), anyInt(), anyLong()))
                .thenAnswer(invocation -> responseBody.get());
        doAnswer(invocation -> {
            Path target = invocation.getArgument(3);
            videoDownloadPath.set(target);
            Files.write(target, responseBody.get());
            return null;
        }).when(remoteAssetDownloader).downloadToFile(anyString(), anyInt(), anyLong(), any(Path.class));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        mocks.close();
        if (videoDownloadPath.get() != null)
        {
            assertFalse(Files.exists(videoDownloadPath.get()), "视频临时文件必须在成功或异常后删除");
        }
    }

    private String remoteUrl(String path)
    {
        return "https://assets.example.test" + path;
    }

    @Test
    void imageRejectsHtmlErrorPageFromSupplier() throws Exception
    {
        responseBody.set("<html><body>Access Denied</body></html>".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class,
                () -> storage.store(1L, 10L, 1, "SHOT_KEYFRAME", remoteUrl("/img.png")));
        verify(pretreatment, never()).upload();
    }

    @Test
    void imageRejectsEmptyContent() throws Exception
    {
        responseBody.set(new byte[0]);
        assertThrows(IllegalArgumentException.class,
                () -> storage.store(1L, 10L, 1, "SHOT_KEYFRAME", remoteUrl("/img.png")));
        verify(pretreatment, never()).upload();
    }

    @Test
    void imageStoresValidPng() throws Exception
    {
        responseBody.set(onePixelPng());
        AiVideoLocalAssetStorage.StoredImage stored =
                storage.store(1L, 10L, 1, "SHOT_KEYFRAME", remoteUrl("/img.png"));
        verify(pretreatment).upload();
        assertEquals(Integer.valueOf(1), stored.getWidth());
        assertEquals(Integer.valueOf(1), stored.getHeight());
        assertTrue(stored.getSha256() != null && stored.getSha256().length() == 64);
    }

    @Test
    void imageRejectsHugeDimensionsBeforeDecodingPixels() throws Exception
    {
        byte[] png = onePixelPng();
        // 修改 IHDR 宽度和 CRC，不分配巨幅像素；解码像素前必须拒绝头部声明的尺寸。
        java.nio.ByteBuffer.wrap(png, 16, 4).putInt(100_000);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(png, 12, 17);
        java.nio.ByteBuffer.wrap(png, 29, 4).putInt((int) crc.getValue());
        responseBody.set(png);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> storage.store(1L, 10L, 1, "SHOT_KEYFRAME", remoteUrl("/huge.png")));
        assertTrue(exception.getMessage().contains("8192"));
        verify(pretreatment, never()).upload();
    }

    @Test
    void supplierJpegIsNormalizedToDeclaredPng() throws Exception
    {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(1, 1,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "jpg", output);
        responseBody.set(output.toByteArray());
        storage.store(1L, 10L, 1, "SHOT_KEYFRAME", remoteUrl("/image.jpg"));
        org.mockito.ArgumentCaptor<Object> uploaded = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(fileStorageService).of(uploaded.capture(), anyString(), anyString());
        byte[] bytes = (byte[]) uploaded.getValue();
        assertEquals(0x89, bytes[0] & 0xff);
        assertEquals('P', bytes[1]);
        assertEquals('N', bytes[2]);
        assertEquals('G', bytes[3]);
    }

    @Test
    void videoRejectsHtmlErrorPageFromSupplier() throws Exception
    {
        responseBody.set("<html><body>Bad Gateway</body></html>".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class,
                () -> storage.storeVideo(1L, 10L, 1, "VIDEO_CLIP", remoteUrl("/clip.mp4")));
        verify(pretreatment, never()).upload();
    }

    @Test
    void videoRejectsTinyContent() throws Exception
    {
        responseBody.set(new byte[64]);
        assertThrows(IllegalArgumentException.class,
                () -> storage.storeVideo(1L, 10L, 1, "VIDEO_CLIP", remoteUrl("/clip.mp4")));
        verify(pretreatment, never()).upload();
    }

    @Test
    void videoRejectsUnknownContainer() throws Exception
    {
        responseBody.set(new byte[2048]);
        assertThrows(IllegalArgumentException.class,
                () -> storage.storeVideo(1L, 10L, 1, "VIDEO_CLIP", remoteUrl("/clip.mp4")));
        verify(pretreatment, never()).upload();
    }

    @Test
    void videoStoresMp4Container() throws Exception
    {
        responseBody.set(mp4ContainerBytes(2048));
        AiVideoLocalAssetStorage.StoredFile stored =
                storage.storeVideo(1L, 10L, 1, "VIDEO_CLIP", remoteUrl("/clip.mp4"));
        verify(pretreatment).upload();
        assertTrue(stored.getSha256() != null && stored.getSha256().length() == 64);
        org.mockito.ArgumentCaptor<Object> uploaded = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(fileStorageService).of(uploaded.capture(), anyString(), anyString());
        assertTrue(uploaded.getValue() instanceof java.io.File, "视频必须通过文件流转存");
    }

    @Test
    void videoDeletesTemporaryFileAfterUploadFailure() throws Exception
    {
        responseBody.set(mp4ContainerBytes(2048));
        when(pretreatment.upload()).thenThrow(new IllegalStateException("上传失败"));
        assertThrows(IllegalStateException.class,
                () -> storage.storeVideo(1L, 10L, 1, "VIDEO_CLIP", remoteUrl("/clip.mp4")));
    }

    @Test
    void videoDeletesPartialFileAfterDownloadFailure() throws Exception
    {
        doAnswer(invocation -> {
            Path target = invocation.getArgument(3);
            videoDownloadPath.set(target);
            Files.write(target, new byte[] {1, 2, 3});
            throw new java.io.IOException("下载中断");
        }).when(remoteAssetDownloader).downloadToFile(anyString(), anyInt(), anyLong(), any(Path.class));
        assertThrows(java.io.IOException.class,
                () -> storage.storeVideo(1L, 10L, 1, "VIDEO_CLIP", remoteUrl("/clip.mp4")));
        verify(pretreatment, never()).upload();
    }
}
