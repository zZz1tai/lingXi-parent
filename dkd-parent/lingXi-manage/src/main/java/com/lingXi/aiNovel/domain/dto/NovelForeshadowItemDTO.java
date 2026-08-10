package com.lingXi.aiNovel.domain.dto;

import lombok.Data;

/**
 * 小说作品上下文条目（伏笔），随创作请求注入智能体。
 * <p>仅注入未解（已埋/待解）伏笔，字段命名与 lingXi-agent 的
 * NovelForeshadowItem 契约保持一致。</p>
 */
@Data
public class NovelForeshadowItemDTO {

    /** 伏笔名称 */
    private String title;

    /** 伏笔详情（埋设内容与预期效果） */
    private String description;

    /** 状态：buried-已埋, pending-待解, resolved-已解 */
    private String status;

    /** 重要等级：high-高, medium-中, low-低 */
    private String priority;

    /** 伏笔关键词 */
    private String keyword;

    /** 计划回收章节号 */
    private Integer resolveChapterNo;
}
