package com.lingXi.aiVedio.domain.dto;

import lombok.Data;

/**
 * 抖音评论条目。
 *
 * @author lingXi
 * @since 2026-11-11
 */
@Data
public class DouyinComment
{
    /** 评论 ID */
    private String cid;

    /** 评论内容 */
    private String text;

    /** 评论者昵称 */
    private String nickname;

    /** 评论者头像 */
    private String avatar;

    /** 点赞数 */
    private Long likeCount;

    /** 评论时间（秒级时间戳） */
    private Long createTime;

    /** 回复数 */
    private Long replyCount;
}
