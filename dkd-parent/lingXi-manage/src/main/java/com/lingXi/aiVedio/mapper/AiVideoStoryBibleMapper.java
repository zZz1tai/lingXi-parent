package com.lingXi.aiVedio.mapper;

import com.lingXi.aiVedio.domain.AiVideoStoryBible;

public interface AiVideoStoryBibleMapper
{
    int insertAiVideoStoryBible(AiVideoStoryBible bible);

    AiVideoStoryBible selectLatestAiVideoStoryBibleByChapterId(Long chapterId);
}
