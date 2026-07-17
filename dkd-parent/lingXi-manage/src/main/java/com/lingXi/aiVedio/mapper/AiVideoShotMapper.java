package com.lingXi.aiVedio.mapper;

import com.lingXi.aiVedio.domain.AiVideoShot;

public interface AiVideoShotMapper
{
    AiVideoShot selectAiVideoShotByShotId(Long shotId);

    int insertAiVideoShot(AiVideoShot shot);
}
