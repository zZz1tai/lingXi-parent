package com.lingXi.aiVedio.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import com.lingXi.common.exception.ServiceException;

class AiVideoImageAspectRatioPolicyTest
{
    @Test
    void characterReferenceAlwaysUsesLandscapeCanvas()
    {
        assertEquals("16:9", AiVideoImageAspectRatioPolicy.resolve("CHARACTER_REFERENCE", "9:16"));
    }

    @Test
    void supportedImageRatiosFollowModelConfiguration()
    {
        assertEquals("16:9", AiVideoImageAspectRatioPolicy.resolve("SCENE_REFERENCE", "16:9"));
        assertEquals("9:16", AiVideoImageAspectRatioPolicy.resolve("SHOT_KEYFRAME", "9:16"));
        assertEquals("1:1", AiVideoImageAspectRatioPolicy.resolve("SHOT_KEYFRAME", "1:1"));
    }

    @Test
    void unsupportedExactRatiosKeepTheConfiguredOrientation()
    {
        assertEquals("9:16", AiVideoImageAspectRatioPolicy.resolve("SCENE_REFERENCE", "3:4"));
        assertEquals("16:9", AiVideoImageAspectRatioPolicy.resolve("SCENE_REFERENCE", "21:9"));
    }

    @Test
    void referenceImagesAcceptTheMinimumSupportedResolution()
    {
        assertDoesNotThrow(() -> AiVideoReferenceImagePolicy.validateDimensions(
                Integer.valueOf(300), Integer.valueOf(300), "起始关键帧"));
    }

    @Test
    void referenceImagesRejectWhenEitherDimensionIsTooSmall()
    {
        ServiceException landscape = assertThrows(ServiceException.class,
                () -> AiVideoReferenceImagePolicy.validateDimensions(
                        Integer.valueOf(400), Integer.valueOf(299), "起始关键帧"));
        assertEquals("起始关键帧：图片分辨率至少为300×300，当前为400×299",
                landscape.getMessage());

        assertThrows(ServiceException.class,
                () -> AiVideoReferenceImagePolicy.validateDimensions(
                        Integer.valueOf(299), Integer.valueOf(400), "参考图片"));
    }

    @Test
    void referenceImagesRejectMissingDimensions()
    {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> AiVideoReferenceImagePolicy.validateDimensions(
                        null, Integer.valueOf(720), "参考图片"));
        assertEquals("参考图片：无法读取图片分辨率，请重新选择 PNG 或 JPG",
                exception.getMessage());
    }
}
