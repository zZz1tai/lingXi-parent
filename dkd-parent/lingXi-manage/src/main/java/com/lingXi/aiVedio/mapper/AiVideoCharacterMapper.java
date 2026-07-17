package com.lingXi.aiVedio.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.lingXi.aiVedio.domain.AiVideoCharacter;

public interface AiVideoCharacterMapper
{
    AiVideoCharacter selectAiVideoCharacterByProjectAndCodeForUpdate(AiVideoCharacter character);

    List<AiVideoCharacter> selectAiVideoCharacterByProjectIdentityForUpdate(AiVideoCharacter character);

    List<AiVideoCharacter> selectAiVideoCharactersByProjectId(Long projectId);

    int upsertAiVideoCharacter(AiVideoCharacter character);

    int updateAiVideoCharacterAliases(@Param("characterId") Long characterId,
            @Param("aliasesJson") String aliasesJson, @Param("updateBy") String updateBy);
}
