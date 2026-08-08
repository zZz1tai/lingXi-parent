package com.lingXi.aiNovel.domain.dto;

import lombok.Data;

/**
 * 小说作品上下文条目（设定卡），随创作请求注入智能体。
 */
@Data
public class NovelSettingItemDTO {

    /** 设定类型：character/world/outline 等 */
    private String settingType;

    /** 设定标题 */
    private String title;

    /** 设定内容 */
    private String content;
}
