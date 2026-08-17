package com.lingXi.aiNovel.service;

import tools.jackson.databind.JsonNode;
import com.lingXi.aiNovel.domain.dto.NovelContextAnalyzeRequestDTO;
import com.lingXi.aiNovel.domain.dto.NovelContextApplyRequestDTO;
import com.lingXi.aiNovel.domain.dto.NovelContextApplyResultDTO;

/** 章节保存后的事实摘要生成与设定/伏笔人工确认同步服务。 */
public interface IAiNovelContextSyncService
{
    /** 分析已保存章节、刷新本章事实摘要并返回待确认的资料候选。 */
    JsonNode analyze(Long workId, NovelContextAnalyzeRequestDTO request);

    /** 事务应用用户勾选的候选变更。 */
    NovelContextApplyResultDTO apply(Long workId, NovelContextApplyRequestDTO request);
}
