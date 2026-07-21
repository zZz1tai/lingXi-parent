package com.lingXi.aiVedio.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

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
}
