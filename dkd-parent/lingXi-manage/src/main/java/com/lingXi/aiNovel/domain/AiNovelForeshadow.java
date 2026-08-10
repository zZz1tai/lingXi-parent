package com.lingXi.aiNovel.domain;

import com.lingXi.common.core.domain.BaseEntity;
import lombok.Data;

/**
 * AI 小说伏笔实体类，对应数据库表 ai_novel_foreshadow。
 * <p>伏笔用于登记与追踪作品中的情节线索，按状态（已埋/待解/已解）、
 * 重要等级与计划回收章节管理，未解伏笔随创作请求一并注入智能体，
 * 防止长篇小说写作过程中遗忘或冲突。</p>
 */
@Data
public class AiNovelForeshadow extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 伏笔主键ID */
    private Long foreshadowId;

    /** 所属作品ID */
    private Long workId;

    /** 伏笔名称 */
    private String title;

    /** 伏笔详情（埋设内容与预期效果） */
    private String description;

    /** 状态：buried-已埋, pending-待解, resolved-已解 */
    private String status;

    /** 重要等级：high-高, medium-中, low-低 */
    private String priority;

    /** 伏笔关键词，用于索引与检索 */
    private String keyword;

    /** 计划回收章节号 */
    private Integer resolveChapterNo;
}
