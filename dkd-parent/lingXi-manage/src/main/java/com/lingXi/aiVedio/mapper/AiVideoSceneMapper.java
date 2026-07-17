package com.lingXi.aiVedio.mapper;

import com.lingXi.aiVedio.domain.AiVideoScene;

public interface AiVideoSceneMapper
{
    AiVideoScene selectAiVideoSceneBySceneId(Long sceneId);

    int insertAiVideoScene(AiVideoScene scene);
}
