package com.lingXi.aiVedio.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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

    @InjectMocks
    private AiVideoLocalAssetStorage storage;

    private AutoCloseable mocks;
    private HttpServer server;
    private final AtomicReference<byte[]> responseBody = new AtomicReference<>(new byte[0]);

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

        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", this::respond);
        server.start();
    }

    private void respond(HttpExchange exchange) throws IOException
    {
        byte[] body = responseBody.get();
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream output = exchange.getResponseBody())
        {
            output.write(body);
        }
    }

    @AfterEach
    void tearDown() throws Exception
    {
        server.stop(0);
        mocks.close();
    }

    private String remoteUrl(String path)
    {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
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
    }
}
