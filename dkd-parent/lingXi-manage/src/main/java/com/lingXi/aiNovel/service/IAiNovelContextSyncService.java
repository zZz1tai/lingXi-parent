package com.lingXi.aiNovel.service;

import tools.jackson.databind.JsonNode;
import com.lingXi.aiNovel.domain.dto.NovelContextAnalyzeRequestDTO;
import com.lingXi.aiNovel.domain.dto.NovelContextApplyRequestDTO;
import com.lingXi.aiNovel.domain.dto.NovelContextApplyResultDTO;

/** 章节保存后的设定与伏笔人工确认同步服务。 */
public interface IAiNovelContextSyncService
{
    /** 分析已保存章节并返回候选变更，不写业务库。 */
    JsonNode analyze(Long workId, NovelContextAnalyzeRequestDTO request);

    /** 事务应用用户勾选的候选变更。 */
    NovelContextApplyResultDTO apply(Long workId, NovelContextApplyRequestDTO request);
}
