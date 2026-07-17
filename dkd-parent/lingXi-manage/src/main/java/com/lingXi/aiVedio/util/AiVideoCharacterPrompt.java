package com.lingXi.aiVedio.util;

/** 人物参考图统一使用正面、侧面、背面三视图。 */
public final class AiVideoCharacterPrompt
{
    public static final String ASSET_TYPE = "CHARACTER_REFERENCE";
    public static final String ASPECT_RATIO = "16:9";
    public static final String IMAGE_SIZE = "1280*720";
    public static final String CONSTRAINT_VERSION = "character-three-view-v1";

    private static final String THREE_VIEW_PROMPT =
            "MANDATORY OUTPUT SPECIFICATION, overriding any conflicting instruction above: " +
            "character turnaround sheet, exactly three full-body orthographic views of the same character " +
            "arranged left to right: front view, left side profile view, back view. " +
            "Show the complete body from head to toe in every view. Use the same neutral A-pose, scale, eye level, " +
            "body proportions, face, hairstyle, clothing, accessories and colors across all three views. " +
            "Plain light-gray studio background, even neutral lighting, no perspective foreshortening, " +
            "no props, no environment, no text and no labels.";

    private static final String THREE_VIEW_NEGATIVE_PROMPT =
            "single view, portrait crop, close-up, missing side view, missing back view, more or fewer than three views, " +
            "extra panels, different characters, inconsistent face, inconsistent hairstyle, different outfit, " +
            "different proportions, action pose, perspective view, foreshortening, cropped head, cropped feet, " +
            "overlapping figures, props, scenery";

    private AiVideoCharacterPrompt()
    {
    }

    public static boolean isCharacterReference(String assetType)
    {
        return ASSET_TYPE.equals(assetType);
    }

    public static String ensureThreeViewPrompt(String prompt)
    {
        String normalized = prompt == null ? "" : prompt.trim();
        if (hasRequiredThreeViewPrompt(normalized))
        {
            return normalized;
        }
        return normalized.isEmpty() ? THREE_VIEW_PROMPT : normalized + " " + THREE_VIEW_PROMPT;
    }

    public static String ensureThreeViewNegativePrompt(String negativePrompt)
    {
        String normalized = negativePrompt == null ? "" : negativePrompt.trim();
        if (hasRequiredThreeViewNegativePrompt(normalized))
        {
            return normalized;
        }
        return normalized.isEmpty()
                ? THREE_VIEW_NEGATIVE_PROMPT
                : normalized + ", " + THREE_VIEW_NEGATIVE_PROMPT;
    }

    public static boolean hasRequiredThreeViewPrompt(String prompt)
    {
        return prompt != null && prompt.trim().endsWith(THREE_VIEW_PROMPT);
    }

    public static boolean hasRequiredThreeViewNegativePrompt(String negativePrompt)
    {
        return negativePrompt != null && negativePrompt.trim().endsWith(THREE_VIEW_NEGATIVE_PROMPT);
    }
}
