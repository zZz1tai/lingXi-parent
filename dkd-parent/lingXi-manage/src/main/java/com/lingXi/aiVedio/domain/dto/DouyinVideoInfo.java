package com.lingXi.aiVedio.domain.dto;

import lombok.Data;

/**
 * 抖音视频解析结果。
 * <p>
 * 由抖音工具接口返回给管理端：包含视频基本信息（标题、作者、封面、时长）
 * 与播放令牌（playToken），管理端使用令牌走代理流接口播放，不暴露 CDN 直链。
 * </p>
 *
 * @author lingXi
 * @since 2026-11-11
 */
@Data
public class DouyinVideoInfo
{
    /** 作品 ID */
    private String awemeId;

    /** 视频标题/文案 */
    private String title;

    /** 作者昵称 */
    private String author;

    /** 作者头像地址 */
    private String authorAvatar;

    /** 视频封面地址 */
    private String coverUrl;

    /** 视频时长（毫秒） */
    private Long durationMs;

    /** 播放令牌，对应代理流接口 /aivideo/tool/douyin/stream/{token} */
    private String playToken;

    /** 用户粘贴的原始链接（规范化后） */
    private String sourceUrl;
}
