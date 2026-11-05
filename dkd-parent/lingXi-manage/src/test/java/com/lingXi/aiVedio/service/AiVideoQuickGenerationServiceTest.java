package com.lingXi.aiVedio.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lingXi.common.exception.ServiceException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class AiVideoQuickGenerationServiceTest
{
    @Test
    void rejectsHugeHeaderBeforePixelDecode() throws Exception
    {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", output);
        byte[] png = output.toByteArray();
        ByteBuffer.wrap(png, 16, 8).putInt(100_000).putInt(100_000);
        CRC32 crc = new CRC32();
        crc.update(png, 12, 17);
        ByteBuffer.wrap(png, 29, 4).putInt((int) crc.getValue());
        AiVideoQuickGenerationService service = new AiVideoQuickGenerationService(null, null, null,
                null, null, null, null);
        ServiceException exception = assertThrows(ServiceException.class, () -> service.validateImage(
                new MockMultipartFile("file", "huge.png", "image/png", png), 0));
        assertTrue(exception.getMessage().contains("8192"));
    }
}
