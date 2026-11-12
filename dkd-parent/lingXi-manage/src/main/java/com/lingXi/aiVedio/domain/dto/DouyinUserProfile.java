package com.lingXi.aiVedio.domain.dto;

import lombok.Data;

/**
 * 抖音作者主页信息。
 *
 * @author lingXi
 * @since 2026-11-11
 */
@Data
public class DouyinUserProfile
{
    /** 作者 sec_uid */
    private String secUid;

    /** 昵称 */
    private String nickname;

    /** 头像 */
    private String avatar;

    /** 简介 */
    private String signature;

    /** 粉丝数 */
    private Long followerCount;

    /** 获赞总数 */
    private Long totalFavorited;

    /** 作品数 */
    private Long awemeCount;
}
