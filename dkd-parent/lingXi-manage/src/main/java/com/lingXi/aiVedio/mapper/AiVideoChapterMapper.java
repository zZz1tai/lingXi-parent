package com.lingXi.aiVedio.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.lingXi.aiVedio.domain.AiVideoChapter;

public interface AiVideoChapterMapper
{
    AiVideoChapter selectAiVideoChapterByChapterId(Long chapterId);

    List<AiVideoChapter> selectAiVideoChapterList(Long projectId);

    int insertAiVideoChapter(AiVideoChapter chapter);

    int updateAiVideoChapterAnalysisStatus(@Param("chapterId") Long chapterId, @Param("parseStatus") String parseStatus,
            @Param("pipelineStatus") String pipelineStatus, @Param("summaryText") String summaryText,
            @Param("currentBibleVersion") Integer currentBibleVersion);

    int deleteAiVideoChapterByChapterIds(@Param("chapterIds") Long[] chapterIds, @Param("projectId") Long projectId);
}
