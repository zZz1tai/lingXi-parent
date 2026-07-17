package com.lingXi.aiVedio.domain;

import com.lingXi.common.core.domain.BaseEntity;
import lombok.Data;

/** AI 视频人物档案对象 ai_video_character */
@Data
public class AiVideoCharacter extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long characterId;
    private Long projectId;
    private String characterCode;
    private String characterName;
    private String aliasesJson;
    private String gender;
    private String ageRange;
    private String personalityJson;
    private String appearanceText;
    private String speakingStyle;
    private String visualPromptBase;
    private String status;
}
