package com.lingXi.aiVedio.domain.dto;

import lombok.Data;

/**
 * 抖音弹幕条目。
 *
 * @author lingXi
 * @since 2026-11-11
 */
@Data
public class DouyinDanmakuItem
{
    /** 出现时刻（相对视频开头的毫秒偏移） */
    private Long offsetMs;

    /** 弹幕文本 */
    private String text;
}
