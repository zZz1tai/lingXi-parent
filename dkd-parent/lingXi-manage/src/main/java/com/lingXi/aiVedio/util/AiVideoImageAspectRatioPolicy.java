package com.lingXi.aiVedio.util;

/** 将模型配置中的成片画幅转换为图片服务支持的画幅。 */
public final class AiVideoImageAspectRatioPolicy
{
    private static final String LANDSCAPE = "16:9";
    private static final String PORTRAIT = "9:16";
    private static final String SQUARE = "1:1";

    private AiVideoImageAspectRatioPolicy()
    {
    }

    /**
     * 根据资产类型和配置的视频比例，解析图片服务支持的画幅比例。
     * 人物三视图强制使用横屏，其他类型跟随配置比例。
     *
     * @param assetType          资产类型
     * @param configuredVideoRatio 配置的视频比例
     * @return 图片服务支持的画幅比例
     */
    public static String resolve(String assetType, String configuredVideoRatio)
    {
        // 人物三视图必须横向并排，不能跟随竖屏成片画幅。
        if ("CHARACTER_REFERENCE".equals(assetType))
        {
            return LANDSCAPE;
        }
        if (configuredVideoRatio == null || configuredVideoRatio.trim().isEmpty())
        {
            return LANDSCAPE;
        }
        String ratio = configuredVideoRatio.trim();
        if (LANDSCAPE.equals(ratio) || PORTRAIT.equals(ratio) || SQUARE.equals(ratio))
        {
            return ratio;
        }
        int separator = ratio.indexOf(':');
        if (separator <= 0 || separator == ratio.length() - 1)
        {
            return LANDSCAPE;
        }
        try
        {
            int width = Integer.parseInt(ratio.substring(0, separator));
            int height = Integer.parseInt(ratio.substring(separator + 1));
            if (width == height)
            {
                return SQUARE;
            }
            return width < height ? PORTRAIT : LANDSCAPE;
        }
        catch (NumberFormatException ex)
        {
            return LANDSCAPE;
        }
    }
}
