package com.lingXi.aiVedio.domain.dto;

import lombok.Data;

/**
 * 抖音视频解析请求。
 *
 * @author lingXi
 * @since 2026-11-11
 */
@Data
public class DouyinResolveRequest
{
    /** 用户粘贴的分享口令或链接（允许包含无关文案，服务端自行提取链接） */
    private String text;
}
