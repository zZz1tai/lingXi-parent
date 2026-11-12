package com.lingXi.aiVedio.domain.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 抖音作者主页数据：资料 + 作品分页。
 *
 * @author lingXi
 * @since 2026-11-11
 */
@Data
public class DouyinUserPage
{
    /** 作者资料 */
    private DouyinUserProfile profile;

    /** 作品分页 */
    private DouyinPageResult<DouyinVideoCard> posts = new DouyinPageResult<>();
}
