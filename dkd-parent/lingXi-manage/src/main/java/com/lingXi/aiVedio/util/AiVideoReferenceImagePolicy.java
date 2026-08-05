package com.lingXi.aiVedio.util;

import com.lingXi.common.exception.ServiceException;

/** 视频供应商参考图的通用输入约束。 */
public final class AiVideoReferenceImagePolicy
{
    public static final int MIN_WIDTH = 300;
    public static final int MIN_HEIGHT = 300;

    private AiVideoReferenceImagePolicy()
    {
    }

    /**
     * 校验参考图尺寸，避免在供应商异步受理后才返回确定性参数错误。
     *
     * @param width 图片宽度
     * @param height 图片高度
     * @param label 面向用户的图片名称
     */
    public static void validateDimensions(Integer width, Integer height, String label)
    {
        String safeLabel = label == null || label.trim().isEmpty() ? "参考图片" : label.trim();
        if (width == null || height == null || width.intValue() <= 0 || height.intValue() <= 0)
        {
            throw new ServiceException(safeLabel + "：无法读取图片分辨率，请重新选择 PNG 或 JPG");
        }
        if (width.intValue() < MIN_WIDTH || height.intValue() < MIN_HEIGHT)
        {
            throw new ServiceException(safeLabel + "：图片分辨率至少为"
                    + MIN_WIDTH + "×" + MIN_HEIGHT + "，当前为" + width + "×" + height);
        }
    }
}
