package com.lingXi.aiVedio.domain;

import com.lingXi.common.core.domain.BaseEntity;
import lombok.Data;

/**
 * AI 视频人物档案实体类，对应数据库表 ai_video_character。
 * <p>人物档案用于保持角色在不同场景和镜头中的视觉一致性，
 * 包含人物基本信息、性格特征、外貌描述及基础视觉提示词等。</p>
 */
@Data
public class AiVideoCharacter extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 人物主键ID */
    private Long characterId;

    /** 所属项目ID */
    private Long projectId;

    /** 人物编码，项目内唯一标识 */
    private String characterCode;

    /** 人物名称 */
    private String characterName;

    /** 人物别名列表，JSON格式存储 */
    private String aliasesJson;

    /** 性别 */
    private String gender;

    /** 年龄范围描述 */
    private String ageRange;

    /** 性格特征，JSON格式存储 */
    private String personalityJson;

    /** 外貌文字描述 */
    private String appearanceText;

    /** 说话语气风格 */
    private String speakingStyle;

    /** 基础视觉提示词，用于人物图像生成 */
    private String visualPromptBase;

    /** 人物状态，如活跃、已归档等 */
    private String status;
}
