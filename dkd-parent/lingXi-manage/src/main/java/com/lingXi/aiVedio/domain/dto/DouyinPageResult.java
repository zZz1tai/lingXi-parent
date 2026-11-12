package com.lingXi.aiVedio.domain.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 分页结果（视频卡片、评论等通用）。
 *
 * @author lingXi
 * @param <T> 条目类型
 * @since 2026-11-11
 */
@Data
public class DouyinPageResult<T>
{
    /** 当前页条目 */
    private List<T> items = new ArrayList<>();

    /** 是否还有更多 */
    private boolean hasMore;

    /** 游标（透传给下次翻页请求） */
    private Long cursor;
}
