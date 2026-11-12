package com.lingXi.aiVedio.domain.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 抖音视频详情页数据：详情、弹幕、首屏评论与相关推荐（一次页面访问一并抓取）。
 *
 * @author lingXi
 * @since 2026-11-11
 */
@Data
public class DouyinVideoPage
{
    /** 视频信息（含播放令牌） */
    private DouyinVideoCard video;

    /** 弹幕列表（按出现时间排列） */
    private List<DouyinDanmakuItem> danmaku = new ArrayList<>();

    /** 首屏评论 */
    private List<DouyinComment> comments = new ArrayList<>();

    /** 评论是否还有更多 */
    private boolean commentsHasMore;

    /** 相关推荐 */
    private List<DouyinVideoCard> related = new ArrayList<>();

    /** 相关推荐是否还有更多 */
    private boolean relatedHasMore;
}
