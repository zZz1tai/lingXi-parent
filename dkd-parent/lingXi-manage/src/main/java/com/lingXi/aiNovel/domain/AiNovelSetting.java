package com.lingXi.aiNovel.domain;

import com.lingXi.common.core.domain.BaseEntity;
import lombok.Data;

/**
 * AI 小说设定卡实体类，对应数据库表 ai_novel_setting。
 * <p>设定卡用于维护长篇小说的人物、世界观、物品、组织、事件与文风等
 * 创作一致性资料，随创作请求一并注入智能体作为作品上下文。</p>
 */
@Data
public class AiNovelSetting extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 设定主键ID */
    private Long settingId;

    /** 所属作品ID */
    private Long workId;

    /** 设定类型：character/world/item/organization/event/style/other */
    private String settingType;

    /** 设定标题 */
    private String title;

    /** 设定内容 */
    private String content;
}
