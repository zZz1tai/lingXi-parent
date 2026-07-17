package com.lingXi.aiVedio.service;

import java.util.List;
import com.lingXi.aiVedio.domain.AiVideoChapter;
import com.lingXi.aiVedio.domain.AiVideoStoryBible;

public interface IAiVideoChapterService
{
    List<AiVideoChapter> selectAiVideoChapterList(Long projectId);

    int insertAiVideoChapter(AiVideoChapter chapter);

    int deleteAiVideoChapterByChapterIds(Long projectId, Long[] chapterIds);

    Long startChapterAnalysis(Long projectId, Long chapterId);

    AiVideoStoryBible selectLatestStoryBible(Long projectId, Long chapterId);
}
