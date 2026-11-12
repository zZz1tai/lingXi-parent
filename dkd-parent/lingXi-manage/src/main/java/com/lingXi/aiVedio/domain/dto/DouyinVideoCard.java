package com.lingXi.aiVedio.domain.dto;

import lombok.Data;

/**
 * 抖音视频卡片（列表/流/相关推荐通用条目）。
 *
 * @author lingXi
 * @since 2026-11-11
 */
@Data
public class DouyinVideoCard
{
    /** 作品 ID */
    private String awemeId;

    /** 视频标题/文案 */
    private String title;

    /** 封面地址 */
    private String coverUrl;

    /** 时长（毫秒） */
    private Long durationMs;

    /** 作者昵称 */
    private String author;

    /** 作者 sec_uid（主页跳转用） */
    private String authorSecUid;

    /** 作者头像 */
    private String authorAvatar;

    /** 点赞数 */
    private Long likeCount;

    /** 评论数 */
    private Long commentCount;

    /** 播放量（抖音未提供时为空） */
    private Long playCount;

    /** 播放令牌，对应代理流接口 */
    private String playToken;
}
