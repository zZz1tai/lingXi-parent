package com.lingXi.aiNovel.domain.dto;

import lombok.Data;

/** 触发章节设定/伏笔同步分析的请求。 */
@Data
public class NovelContextAnalyzeRequestDTO
{
    /** 已保存的章节ID。 */
    private Long chapterId;

    /** 是否强制重新分析相同正文；手动点击时使用。 */
    private Boolean force;
}
