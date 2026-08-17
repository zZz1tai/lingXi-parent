package com.lingXi.aiNovel.service;

import tools.jackson.databind.JsonNode;
import com.lingXi.aiNovel.domain.AiNovelContextTask;
import com.lingXi.aiNovel.domain.dto.NovelContextAnalyzeRequestDTO;
import com.lingXi.aiNovel.domain.dto.NovelContextApplyRequestDTO;
import com.lingXi.aiNovel.domain.dto.NovelContextApplyResultDTO;
import com.lingXi.aiNovel.domain.dto.NovelContextTaskVO;

/** 章节保存后的事实摘要生成与设定/伏笔人工确认同步服务。 */
public interface IAiNovelContextSyncService
{
    /** 提交持久化异步分析任务；相同章节正文自动去重。 */
    NovelContextTaskVO submitAnalysis(Long workId, NovelContextAnalyzeRequestDTO request);

    /** 查询指定任务并校验作品归属。 */
    NovelContextTaskVO getAnalysisTask(Long workId, Long taskId);

    /** 查询章节最近一次资料同步任务，用于页面刷新后恢复状态。 */
    NovelContextTaskVO getLatestAnalysisTask(Long workId, Long chapterId);

    /** Worker 执行已领取任务；正文已变化或章节已删除时返回 null。 */
    JsonNode executeAnalysisTask(AiNovelContextTask task);

    /** 事务应用用户勾选的候选变更。 */
    NovelContextApplyResultDTO apply(Long workId, NovelContextApplyRequestDTO request);
}
